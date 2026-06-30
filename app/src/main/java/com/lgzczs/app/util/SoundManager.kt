package com.lgzczs.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

object SoundManager {
    private var mediaPlayer: MediaPlayer? = null

    fun playNotificationSound(context: Context, ringtoneUri: String? = null) {
        try {
            val uri = if (ringtoneUri != null) Uri.parse(ringtoneUri)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri == null) return

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { reset() }
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    fun playAlertLoop(context: Context, ringtoneUri: String? = null) {
        try {
            val uri = if (ringtoneUri != null) Uri.parse(ringtoneUri)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (uri == null) return

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun reset() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
