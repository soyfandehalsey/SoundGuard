package com.example.soundguard

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AudioRecorder(
        private val audioSource: Int,
        private val sampleRate: Int,
        private val channelConfig: Int,
        private val audioFormat: Int,
        bufferSize: Int = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
        )
) {
    private var audioRecord: AudioRecord? = null
    private val bufferSize: Int

    init {
        this.bufferSize = bufferSize
    }

    fun startRecording() {
        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            return
        }

        audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
        )

        audioRecord?.startRecording()
    }

    fun stopRecording() {
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
                release()
            }
        }
        audioRecord = null
    }

    fun isRecording(): Boolean {
        return audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
    }

    fun read(buffer: ShortArray, offset: Int, size: Int): Int {
        return audioRecord?.read(buffer, offset, size) ?: 0
    }
}