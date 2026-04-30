// core/AudioFocusManager.kt
package com.guardapp.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Управление аудио-фокусом для приглушения других приложений.
 *
 * Стратегия:
 *  - GAIN_TRANSIENT_EXCLUSIVE — корректные плееры (YouTube, Spotify, видео в Chrome)
 *    автоматически замолкают или ставятся на паузу
 *  - После закрытия Экрана Просветления — abandonAudioFocus() →
 *    плееры восстанавливают звук сами
 *
 * Звонки (Phone, WhatsApp, Zoom) НЕ приглушаются — у них системный приоритет.
 */
class AudioFocusManager(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* игнорируем */ }

    /** Запрашивает эксклюзивный аудио-фокус */
    fun requestFocus(): Boolean {
        if (hasFocus) return true

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }

        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, if (hasFocus) "🔇 Audio focus получен" else "❌ Audio focus не получен")
        return hasFocus
    }

    /** Возвращает audio focus → плееры восстанавливают звук */
    fun release() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        hasFocus = false
        focusRequest = null
        Log.i(TAG, "🔊 Audio focus released")
    }

    companion object { private const val TAG = "AudioFocusManager" }
}
