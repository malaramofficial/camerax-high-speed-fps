package com.sample.cameraxhighspeedfps.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

data class HighSpeedConfig(
    val quality: Quality,
    val fpsRange: Range<Int>
) {
    override fun toString(): String {
        val qStr = when (quality) {
            Quality.UHD -> "2160p"
            Quality.FHD -> "1080p"
            Quality.HD -> "720p"
            Quality.SD -> "480p"
            else -> "Unknown"
        }
        return "$qStr/${fpsRange.upper}fps"
    }
}

class HighSpeedCameraManager(private val context: Context) {
    private val _configs = MutableStateFlow<List<HighSpeedConfig>>(emptyList())
    val configs = _configs.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    @SuppressLint("UnsafeOptInUsageError")
    fun discoverCapabilities(cameraInfo: CameraInfo) {
        val capabilities = Recorder.getHighSpeedVideoCapabilities(cameraInfo) ?: return
        val supportedQualities = capabilities.getSupportedQualities(DynamicRange.SDR)

        val foundConfigs = mutableListOf<HighSpeedConfig>()

        for (quality in supportedQualities) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality))
                .build()
            val vc = VideoCapture.withOutput(recorder)

            // Query frame-rate support for the same use-case combination that
            // we actually bind later: VideoCapture + Preview. Querying only
            // VideoCapture can return a range that becomes invalid once the
            // Preview surface is added, which can result in a black preview.
            val preview = Preview.Builder().build()
            val builder = HighSpeedVideoSessionConfig.Builder(vc)
                .setPreview(preview)
            val highSpeedSessionConfig = builder.build()

            val fpsRanges = cameraInfo.getSupportedFrameRateRanges(highSpeedSessionConfig)
            for (range in fpsRanges) {
                if (range.upper >= 120) {
                    foundConfigs.add(HighSpeedConfig(quality, range))
                }
            }
        }

        _configs.value = foundConfigs.sortedByDescending { it.fpsRange.upper }.distinct()
    }

    @SuppressLint("UnsafeOptInUsageError")
    suspend fun bindCamera(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        config: HighSpeedConfig,
        slowMotion: Boolean
    ) {
        Log.d("HighSpeedCameraManager", "Binding camera: $config, slowMotion=$slowMotion")

        activeRecording?.stop()
        activeRecording = null
        _isRecording.value = false

        cameraProvider.unbindAll()
        delay(100.milliseconds)

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(config.quality))
            .build()
        val vc = VideoCapture.withOutput(recorder)
        videoCapture = vc

        val preview = Preview.Builder().build()
        preview.surfaceProvider = previewView.surfaceProvider

        val builder = HighSpeedVideoSessionConfig.Builder(vc)
            .setPreview(preview)
            .setFrameRateRange(config.fpsRange)
            .setSlowMotionEnabled(slowMotion)

        val highSpeedSessionConfig = builder.build()

        try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                highSpeedSessionConfig
            )
            Log.d("HighSpeedCameraManager", "Binding successful")
        } catch (e: Exception) {
            Log.e("HighSpeedCameraManager", "Binding failed", e)
        }
    }

    @SuppressLint("MissingPermission", "UnsafeOptInUsageError")
    fun toggleRecording() {
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
            activeRecording = null
            _isRecording.value = false
            return
        }

        val vc = videoCapture ?: return

        val name = "HighSpeed-" + SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CameraXHighSpeed")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        activeRecording = vc.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        activeRecording = null
                        if (event.hasError()) {
                            Log.e("HighSpeedCameraManager", "Recording error: ${event.error}")
                        } else {
                            Log.d("HighSpeedCameraManager", "Recording saved: ${event.outputResults.outputUri}")
                        }
                    }
                }
            }
    }
}
