package fr.amorin.jicrade.sync

import android.content.Context
import android.media.AudioManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import kotlin.math.roundToInt

object AlarmVolume {
    private const val PREFS = "alarm_volume"
    private const val KEY_ORIGINAL = "original"
    private const val KEY_RAISED = "raised"

    fun raise(context: Context, percent: Int) {
        val am = context.getSystemService<AudioManager>() ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val target = (max * percent.coerceIn(10, 100) / 100f).roundToInt().coerceIn(1, max)
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        if (current >= target) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0) }.onSuccess {
            prefs.edit {
                if (!prefs.contains(KEY_ORIGINAL)) putInt(KEY_ORIGINAL, current)
                putInt(KEY_RAISED, target)
            }
        }
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val original = prefs.getInt(KEY_ORIGINAL, -1)
        if (original < 0) return
        val am = context.getSystemService<AudioManager>()
        if (am != null && am.getStreamVolume(AudioManager.STREAM_ALARM) == prefs.getInt(KEY_RAISED, -1)) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
        }
        prefs.edit { clear() }
    }
}
