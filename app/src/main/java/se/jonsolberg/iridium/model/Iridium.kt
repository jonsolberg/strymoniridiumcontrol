package se.jonsolberg.iridium.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** MIDI CC numbers from the Iridium MIDI implementation chart. */
object Cc {
    const val BANK = 0
    const val VOL_POSITION = 9      // 0 = volume pedal PRE, 1-127 = POST
    const val LEVEL = 12
    const val DRIVE = 13
    const val BASS = 14
    const val MIDDLE = 15
    const val TREBLE = 16
    const val ROOM = 17
    const val ROOM_SIZE = 18        // 1 small, 2 medium, 3 large
    const val AMP = 19              // 1 round, 2 chime, 3 punch
    const val CAB = 20              // 0-2 round a-c, 3-5 chime a-c, 6-8 punch a-c
    const val AMP_DISABLE = 21      // 0 = amp on, 1-127 = amp bypassed
    const val FAV_SWITCH = 27       // 127 = press, 0 = release
    const val ON_SWITCH = 28        // 127 = press, 0 = release
    const val AMP_KNOB_OUT = 29     // sent by the pedal when the AMP switch moves
    const val CAB_KNOB_OUT = 30     // sent by the pedal when the CAB switch moves
    const val BYPASS = 102          // 0 = bypass, 1-127 = on
}

object Iridium {
    const val PRESET_COUNT = 300
    /** Bank 0 / program 127 recalls manual mode and cannot store a preset. */
    const val MANUAL_SLOT = 127

    val AMPS = listOf("round", "chime", "punch")
    val AMP_DESCRIPTIONS = listOf(
        "American black-panel clean",
        "British top-boost chime",
        "British plexi crunch",
    )
    val CAB_LETTERS = listOf("a", "b", "c")
    val CAB_DESCRIPTIONS = listOf(
        "1x12 open-back combo", "1x12 small combo", "2x10 combo",
        "2x12 open-back combo", "1x12 closed-back", "4x12 half-back",
        "4x12 greenback-style", "2x12 V30-style", "8x12 full stack",
    )
    val ROOM_SIZES = listOf("small", "medium", "large")

    fun isStorable(n: Int) = n in 0 until PRESET_COUNT && n != MANUAL_SLOT
    fun cabName(cab: Int) = "${AMPS[cab / 3]}-${CAB_LETTERS[cab % 3]}"
}

fun pct(v: Int): Int = (v * 100f / 127f).roundToInt()

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/** Every parameter of one Iridium sound. */
data class PedalSound(
    val drive: Int = 64,
    val level: Int = 64,
    val bass: Int = 64,
    val middle: Int = 64,
    val treble: Int = 64,
    val room: Int = 32,
    val roomSize: Int = 2,
    val amp: Int = 1,
    val cab: Int = 0,
    val ampDisabled: Boolean = false,
    val volumePost: Boolean = false,
) {
    val cabLetter: Int get() = cab % 3
    val cabFamily: Int get() = cab / 3

    /** Amp must go before cab: the pedal re-maps the cab when the amp changes. */
    fun ccMessages(): List<Pair<Int, Int>> = listOf(
        Cc.AMP to amp,
        Cc.CAB to cab,
        Cc.DRIVE to drive,
        Cc.LEVEL to level,
        Cc.BASS to bass,
        Cc.MIDDLE to middle,
        Cc.TREBLE to treble,
        Cc.ROOM_SIZE to roomSize,
        Cc.ROOM to room,
        Cc.AMP_DISABLE to if (ampDisabled) 127 else 0,
        Cc.VOL_POSITION to if (volumePost) 127 else 0,
    )

    fun withCc(cc: Int, raw: Int): PedalSound {
        val v = raw.coerceIn(0, 127)
        return when (cc) {
            Cc.DRIVE -> copy(drive = v)
            Cc.LEVEL -> copy(level = v)
            Cc.BASS -> copy(bass = v)
            Cc.MIDDLE -> copy(middle = v)
            Cc.TREBLE -> copy(treble = v)
            Cc.ROOM -> copy(room = v)
            Cc.ROOM_SIZE -> if (v in 1..3) copy(roomSize = v) else this
            Cc.AMP -> if (v in 1..3) copy(amp = v) else this
            Cc.CAB -> if (v in 0..8) copy(cab = v) else this
            Cc.AMP_DISABLE -> copy(ampDisabled = v > 0)
            Cc.VOL_POSITION -> copy(volumePost = v > 0)
            else -> this
        }
    }

    fun summary(): String =
        "${cap(Iridium.AMPS[amp - 1])}-${pct(drive)} | ${cap(Iridium.cabName(cab))} | " +
            "${cap(Iridium.ROOM_SIZES[roomSize - 1])}-${pct(room)}"

    fun toJson(): JSONObject = JSONObject().apply {
        val midi = JSONArray()
        ccMessages().forEach { (c, v) -> midi.put(JSONObject().put("ccn", c).put("ccv", v)) }
        put("midi", midi)
    }

    companion object {
        fun fromMidiArray(arr: JSONArray): PedalSound {
            var s = PedalSound()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                s = s.withCc(o.optInt("ccn", -1), o.optInt("ccv", 0))
            }
            return s
        }
    }
}

enum class SyncState { EMPTY, EDITED, SYNCED }

data class Preset(
    val number: Int,
    val name: String = "",
    val sound: PedalSound? = null,
    val sync: SyncState = SyncState.EMPTY,
)
