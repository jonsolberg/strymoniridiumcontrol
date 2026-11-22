package se.jonsolberg.iridium;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks for a newer release of the app on GitHub, at most once a day.
 * On first start the user is asked whether the app may check; "Don't check
 * again" in the update dialog turns it off.
 *
 * Part of Iridium Control. Licensed under the GNU General Public License v3.
 */
public final class UpdateChecker {
    private static final String REPO = "jonsolberg/strymoniridiumcontrol";
    private static final String APK = "IridiumControl";
    private static final String PREFS = "update_check";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST = "last_check";
    private static final long INTERVAL_MS = 20L * 60 * 60 * 1000;

    private UpdateChecker() {}

    /** Call from the main activity's onCreate. */
    public static void start(Activity activity) {
        // For testing: adb shell am start ... --es updatecheck 0.0 checks now,
        // as if version 0.0 were installed.
        String forced = activity.getIntent() == null ? null
                : activity.getIntent().getStringExtra("updatecheck");
        if (forced == null && isEmulator()) return;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        if (!prefs.contains(KEY_ENABLED)) {
            ask(activity, prefs, forced);
            return;
        }
        if (!prefs.getBoolean(KEY_ENABLED, false)) return;
        long now = System.currentTimeMillis();
        if (forced == null && now - prefs.getLong(KEY_LAST, 0) < INTERVAL_MS) return;
        check(activity, prefs, forced);
    }

    private static void ask(Activity activity, SharedPreferences prefs, String forced) {
        if (!alive(activity)) return;
        new AlertDialog.Builder(activity)
                .setTitle("Check for new versions?")
                .setMessage("The app can let you know when a new version is out. "
                        + "It asks GitHub at most once a day and sends nothing about you.")
                .setPositiveButton("Yes", (d, w) -> {
                    prefs.edit().putBoolean(KEY_ENABLED, true).apply();
                    check(activity, prefs, forced);
                })
                .setNegativeButton("No", (d, w) ->
                        prefs.edit().putBoolean(KEY_ENABLED, false).apply())
                .setCancelable(false)
                .show();
    }

    private static void check(Activity activity, SharedPreferences prefs, String forced) {
        final String installed = forced != null ? forced : installedVersion(activity);
        new Thread(() -> {
            String latest = latestVersion();
            if (latest == null) return; // offline or GitHub unreachable: try again next start
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
            if (installed == null || compare(latest, installed) <= 0) return;
            activity.runOnUiThread(() -> offer(activity, prefs, latest));
        }, "UpdateChecker").start();
    }

    private static void offer(Activity activity, SharedPreferences prefs, String latest) {
        if (!alive(activity)) return;
        new AlertDialog.Builder(activity)
                .setTitle("Version " + latest + " is available")
                .setMessage("Download the new version and open the file to install it "
                        + "over this one. Your settings are kept.")
                .setPositiveButton("Download", (d, w) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://github.com/" + REPO + "/releases/latest/download/" + APK + ".apk")));
                    } catch (Exception ignored) {
                        // No browser installed
                    }
                })
                .setNegativeButton("Later", null)
                .setNeutralButton("Don't check again", (d, w) ->
                        prefs.edit().putBoolean(KEY_ENABLED, false).apply())
                .show();
    }

    /** The version of the newest APK in the latest GitHub release, or null. */
    private static String latestVersion() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(
                    "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", APK + "-update-check");
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            JSONArray assets = new JSONObject(out.toString("UTF-8")).optJSONArray("assets");
            if (assets == null) return null;
            Pattern p = Pattern.compile(Pattern.quote(APK) + "-(\\d+(?:\\.\\d+)*)\\.apk");
            String best = null;
            for (int i = 0; i < assets.length(); i++) {
                Matcher m = p.matcher(assets.getJSONObject(i).optString("name"));
                if (m.matches() && (best == null || compare(m.group(1), best) > 0)) best = m.group(1);
            }
            return best;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String installedVersion(Activity activity) {
        try {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return null;
        }
    }

    /** Compares dotted version numbers: 1.10 is newer than 1.9. */
    static int compare(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i]) : 0;
            long q = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static boolean alive(Activity a) {
        return !a.isFinishing() && !a.isDestroyed();
    }

    private static boolean isEmulator() {
        // CI takes screenshots on emulators; keep the dialogs out of them.
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("emulator")
                || Build.PRODUCT.contains("sdk");
    }
}
