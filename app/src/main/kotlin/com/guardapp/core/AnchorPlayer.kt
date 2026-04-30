// core/AnchorPlayer.kt
package com.guardapp.core

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.guardapp.data.AnchorConfig
import com.guardapp.data.AnchorType

/**
 * Воспроизведение медиа-якоря.
 *  - Видео → ExoPlayer (зацикленный)
 *  - Изображение → URI передаётся в UI, Coil загружает с downsampling
 *  - Аудио → MediaPlayer (зацикленный)
 *  - Изображение + аудио → URI + MediaPlayer (зацикленный)
 *
 * Bitmap больше не хранится здесь — это предотвращает OOM и
 * "Canvas: trying to draw too large bitmap".
 */
class AnchorPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var mediaPlayer: MediaPlayer? = null

    fun play(config: AnchorConfig) {
        when (config.type) {
            AnchorType.VIDEO -> playVideo(config)
            AnchorType.IMAGE -> { /* URI рендерится через Coil в AnchorActivity */ }
            AnchorType.AUDIO -> playAudio(config.primaryUri, config.volumePercent)
            AnchorType.IMAGE_AND_AUDIO -> {
                // Изображение — через Coil в UI, аудио — здесь
                playAudio(config.secondaryUri, config.volumePercent)
            }
        }
    }

    private fun playVideo(config: AnchorConfig) {
        val uri = config.primaryUri?.let { Uri.parse(it) } ?: return
        try {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                volume = config.volumePercent / 100f
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video playback error: $e")
        }
    }

    private fun playAudio(uriString: String?, volumePercent: Int) {
        val uri = uriString?.let { Uri.parse(it) } ?: return
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                val v = volumePercent / 100f
                setVolume(v, v)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback error: $e")
        }
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    /**
     * Полная остановка и освобождение ресурсов.
     * Вызывается только когда пользователь закрывает экран.
     */
    fun stop() {
        try { exoPlayer?.release() } catch (_: Exception) {}
        exoPlayer = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    companion object { private const val TAG = "AnchorPlayer" }
}
