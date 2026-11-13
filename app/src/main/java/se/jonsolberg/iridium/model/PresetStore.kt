package se.jonsolberg.iridium.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the 300-slot preset library as JSON in app storage. */
class PresetStore(private val file: File) {

    fun load(): List<Preset> {
        if (!file.exists()) return defaults()
        return try {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("presets")
            val list = MutableList(Iridium.PRESET_COUNT) { Preset(it) }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val n = o.getInt("number")
                if (n !in 0 until Iridium.PRESET_COUNT) continue
                val midi = o.optJSONArray("midi")
                val sound = midi?.let { PedalSound.fromMidiArray(it) }
                val sync = runCatching { SyncState.valueOf(o.optString("sync")) }.getOrNull()
                    ?: if (sound == null) SyncState.EMPTY else SyncState.EDITED
                list[n] = Preset(n, o.optString("name"), sound, if (sound == null) SyncState.EMPTY else sync)
            }
            list
        } catch (e: Exception) {
            defaults()
        }
    }

    fun save(presets: List<Preset>) {
        val arr = JSONArray()
        presets.filter { it.sound != null || it.name.isNotEmpty() }.forEach { p ->
            val o = JSONObject().put("number", p.number).put("name", p.name).put("sync", p.sync.name)
            p.sound?.let { o.put("midi", it.toJson().getJSONArray("midi")) }
            arr.put(o)
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().put("version", 1).put("presets", arr).toString())
        tmp.renameTo(file)
    }

    private val favFile = File(file.parentFile, "favorite.json")

    fun loadFavorite(): Favorite? = try {
        if (favFile.exists()) favoriteFromJson(JSONObject(favFile.readText())) else null
    } catch (e: Exception) {
        null
    }

    fun saveFavorite(fav: Favorite?) {
        if (fav == null) favFile.delete() else favFile.writeText(favoriteToJson(fav).toString())
    }

    companion object {
        fun favoriteToJson(f: Favorite): JSONObject =
            JSONObject().put("title", f.name).put("midi", f.sound.toJson().getJSONArray("midi"))

        fun favoriteFromJson(o: JSONObject): Favorite? {
            val midi = o.optJSONArray("midi") ?: return null
            return Favorite(o.optString("title"), PedalSound.fromMidiArray(midi))
        }

        /** Full backup: every stored preset (with its slot number), the FAV sound and app settings. */
        fun backupJson(presets: List<Preset>, favorite: Favorite?, settings: JSONObject): String {
            val arr = JSONArray()
            presets.filter { it.sound != null }.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("number", p.number)
                        .put("title", p.name)
                        .put("state", 1)
                        .put("sync", p.sync.name)
                        .put("midi", p.sound!!.toJson().getJSONArray("midi"))
                )
            }
            return JSONObject()
                .put("app", "Iridium Control")
                .put("type", "backup")
                .put("version", 2)
                .put("device", EXPORT_DEVICE)
                .put("favorite", favorite?.let { favoriteToJson(it) } ?: JSONObject.NULL)
                .put("settings", settings)
                .put("patches", arr)
                .toString(2)
        }

        fun isBackup(text: String): Boolean =
            runCatching { JSONObject(text).optString("type") == "backup" }.getOrDefault(false)

        fun parseBackup(text: String): Backup {
            val root = JSONObject(text)
            require(root.optString("device", EXPORT_DEVICE).equals(EXPORT_DEVICE, true)) { "Not an Iridium file." }
            val list = MutableList(Iridium.PRESET_COUNT) { Preset(it) }
            val arr = root.optJSONArray("patches") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val n = o.optInt("number", -1)
                val midi = o.optJSONArray("midi") ?: continue
                if (!Iridium.isStorable(n)) continue
                val sync = runCatching { SyncState.valueOf(o.optString("sync")) }.getOrNull() ?: SyncState.EDITED
                list[n] = Preset(n, o.optString("title"), PedalSound.fromMidiArray(midi), if (sync == SyncState.EMPTY) SyncState.EDITED else sync)
            }
            val fav = root.optJSONObject("favorite")?.let { favoriteFromJson(it) }
            return Backup(list, fav, root.optJSONObject("settings"))
        }

        fun defaults(): List<Preset> {
            val list = MutableList(Iridium.PRESET_COUNT) { Preset(it) }
            val seeds = listOf(
                "Clean Jangle" to PedalSound(64, 64, 64, 64, 64, 32, 1, 2, 3),
                "Classic Clean" to PedalSound(71, 49, 88, 64, 88, 32, 2, 1, 0),
                "Crown Jewel" to PedalSound(127, 51, 70, 76, 114, 32, 2, 2, 4),
                "Punched Up" to PedalSound(88, 49, 88, 127, 96, 32, 3, 3, 6),
            )
            seeds.forEachIndexed { i, (name, s) -> list[i] = Preset(i, name, s, SyncState.EDITED) }
            return list
        }

        const val EXPORT_DEVICE = "iridium"

        /** Export in the same shape the old Iridium Controller app used, so files stay interchangeable. */
        fun exportJson(presets: List<Preset>): String {
            val arr = JSONArray()
            presets.forEach { p ->
                val o = JSONObject()
                    .put("title", p.name)
                    .put("desc", "")
                    .put("number", p.number)
                    .put("state", if (p.sound == null) 0 else 1)
                    .put("midi", (p.sound ?: PedalSound()).toJson().getJSONArray("midi"))
                arr.put(o)
            }
            return JSONObject()
                .put("version", "1.0.0")
                .put("device", EXPORT_DEVICE)
                .put("app", "Iridium Control")
                .put("patches", arr)
                .toString(2)
        }

        /** Reads exports from this app or the old app ("patches" with title/midi). */
        fun importJson(text: String): List<Pair<String, PedalSound>> {
            val root = JSONObject(text)
            val device = root.optString("device", EXPORT_DEVICE)
            require(device.equals(EXPORT_DEVICE, ignoreCase = true)) { "This file is for '$device', not Iridium." }
            val arr = root.optJSONArray("patches") ?: root.optJSONArray("presets")
                ?: throw IllegalArgumentException("No presets found in file.")
            val out = mutableListOf<Pair<String, PedalSound>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val midi = o.optJSONArray("midi") ?: continue
                val name = o.optString("title").ifEmpty { o.optString("name") }
                if (o.optInt("state", 1) == 0 && name.isEmpty()) continue
                out += name to PedalSound.fromMidiArray(midi)
            }
            return out
        }
    }
}

data class Favorite(val name: String, val sound: PedalSound)

data class Backup(val presets: List<Preset>, val favorite: Favorite?, val settings: JSONObject?)
