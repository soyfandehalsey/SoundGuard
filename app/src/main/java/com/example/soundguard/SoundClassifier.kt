package com.example.soundguard

import android.content.Context
import android.util.Log
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.IOException

class SoundClassifier(context: Context) {
    private var classifier: AudioClassifier? = null
    private var tensorAudio: TensorAudio? = null

    init {
        try {
            classifier = AudioClassifier.createFromFile(context, "model.tflite")
            tensorAudio = classifier?.createInputTensorAudio()
        } catch (e: IOException) {
            Log.e("SoundClassifier", "Error al cargar el modelo", e)
        } catch (e: IllegalStateException) {
            Log.e("SoundClassifier", "Error al inicializar el clasificador", e)
        }
    }

    fun classify(audioBuffer: FloatArray): List<AudioClassifier.AudioClassificationResult> {
        return try {
            tensorAudio?.load(audioBuffer)
            classifier?.classify(tensorAudio) ?: emptyList()
        } catch (e: Exception) {
            Log.e("SoundClassifier", "Error en la clasificación", e)
            emptyList()
        }
    }

    fun close() {
        classifier = null
        tensorAudio = null
    }
}