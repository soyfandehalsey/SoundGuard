package com.example.soundguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.soundguard.databinding.ActivityMainBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.vmadalin.easypermissions.EasyPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var classifier: SoundClassifier
    private var isMonitoring = false
    private lateinit var executor: ExecutorService

    // Views
    private lateinit var spectrogram: LineChart
    private lateinit var currentStatus: TextView
    private lateinit var confidenceBar: ProgressBar
    private lateinit var navigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar vistas
        spectrogram = binding.spectrogram
        currentStatus = binding.currentStatus
        confidenceBar = binding.confidenceBar
        navigation = binding.navigation

        // Configurar gráfico
        configureChart()

        // Configurar navegación
        navigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_history -> showHistory()
                R.id.menu_settings -> showSettings()
            }
            true
        }

        // Inicializar componentes
        executor = Executors.newSingleThreadExecutor()
        classifier = SoundClassifier(this)
        audioRecorder = AudioRecorder(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        )

        // Solicitar permisos
        requestPermissions()
    }

    private fun configureChart() {
        spectrogram.setTouchEnabled(false)
        spectrogram.description.isEnabled = false
        spectrogram.legend.isEnabled = false
        spectrogram.axisLeft.isEnabled = false
        spectrogram.axisRight.isEnabled = false
        spectrogram.xAxis.isEnabled = false
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            setupMonitoringButton()
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMonitoringButton()
            } else {
                Snackbar.make(
                        binding.root,
                        R.string.mic_permission_required,
                        Snackbar.LENGTH_INDEFINITE
                ).show()
            }
        }
    }

    private fun setupMonitoringButton() {
        binding.fab.setOnClickListener {
            if (!isMonitoring) {
                startMonitoring()
            } else {
                stopMonitoring()
            }
        }
    }

    private fun startMonitoring() {
        if (!audioRecorder.isRecording()) {
            audioRecorder.startRecording()
        }

        isMonitoring = true
        binding.fab.text = getString(R.string.stop_monitoring)
        currentStatus.text = getString(R.string.waiting_for_sound)

        lifecycleScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(BUFFER_SIZE)
            while (isMonitoring) {
                val bytesRead = audioRecorder.read(buffer, 0, BUFFER_SIZE)
                if (bytesRead > 0) {
                    processAudio(buffer)
                }
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        binding.fab.text = getString(R.string.start_monitoring)
        audioRecorder.stopRecording()
    }

    private suspend fun logEvent(type: String, confidence: Float) {
        val dao = SoundGuardApp.database.soundEventDao()
        dao.insert(SoundEvent(type = type, confidence = confidence))
    }

    private fun processAudio(buffer: ShortArray) {
        val floatBuffer = FloatArray(buffer.size) { buffer[it] / 32767.0f }
        val results = classifier.classify(floatBuffer)

        results.firstOrNull()?.let { result ->
            val topCategory = result.classifications.maxByOrNull { it.score }
            topCategory?.let { category ->
                lifecycleScope.launch {
                when (category.label) {
                    "collapse" -> handleEmergency(R.string.collapse_detected, category.score)
                    "fire" -> handleEmergency(R.string.fire_detected, category.score)
                    "baby" -> handleAlert(R.string.baby_detected, category.score)
                    "doorbell" -> handleNotification(R.string.doorbell_detected, category.score)
                    "alarm" -> handleEmergency(R.string.alarm_detected, category.score)
                }

                logEvent(category.label, category.score)
            }


            updateUI(category.label, category.score)
            }
        }
    }

    private fun updateUI(label: String, confidence: Float) {
        runOnUiThread {
            currentStatus.text = getString(
                    when (label) {
                        "collapse" -> R.string.collapse_detected
                        "fire" -> R.string.fire_detected
                        "baby" -> R.string.baby_detected
                        "doorbell" -> R.string.doorbell_detected
                        "alarm" -> R.string.alarm_detected
                        else -> R.string.waiting_for_sound
                    }
            )

            confidenceBar.progress = (confidence * 100).toInt()
        }
    }

    private fun handleEmergency(messageRes: Int, confidence: Float) {
        runOnUiThread {
            Snackbar.make(binding.root, getString(messageRes), Snackbar.LENGTH_LONG)
                    .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
                    .show()

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(
                                longArrayOf(100, 200, 100, 200),
                                intArrayOf(255, 0, 255, 0),
                                -1
                        )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(100, 200, 100, 200), -1)
            }
        }
    }

    private fun handleAlert(messageRes: Int, confidence: Float) {
        runOnUiThread {
            Snackbar.make(binding.root, getString(messageRes), Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(ContextCompat.getColor(this, R.color.success))
                    .show()
        }
    }

    private fun handleNotification(messageRes: Int, confidence: Float) {
        runOnUiThread {
            Snackbar.make(binding.root, getString(messageRes), Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(ContextCompat.getColor(this, R.color.secondary))
                    .show()
        }
    }

        private suspend fun logEvent(type: String, confidence: Float) {
            val dao = SoundGuardApp.database.soundEventDao()
            dao.insert(SoundEvent(type = type, confidence = confidence))
        }

    // Opcional: mostrar historial en log (por ahora)
    private fun showHistory() {
        lifecycleScope.launch {
            val events = SoundGuardApp.database.soundEventDao().getAll()
            events.forEach {
                Log.d("SoundEvent", "Evento: ${it.type}, confianza: ${it.confidence}, ts: ${it.timestamp}")
            }
        }
    }

    private fun showSettings() {
        // Aquí podrías lanzar una nueva Activity o mostrar un diálogo de configuración
        Snackbar.make(binding.root, "Ajustes no implementados", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        executor.shutdown()
        classifier.close()
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val BUFFER_SIZE = 44100 // 1 segundo de audio
    }
}