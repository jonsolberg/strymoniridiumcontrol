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

    companion object {
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
