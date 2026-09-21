package com.sample.cameraxhighspeedfps.ui

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sample.cameraxhighspeedfps.camera.HighSpeedConfig

@Composable
fun CameraScreen(viewModel: HighSpeedViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val display = context.display
    val rotation = display?.rotation ?: Surface.ROTATION_0
    
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val selectedConfig by viewModel.selectedConfig.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val slowMotionEnabled by viewModel.slowMotionEnabled.collectAsStateWithLifecycle()

    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProviderState.value = provider
            val cameraInfo = provider.availableCameraInfos.firstOrNull {
                it.lensFacing == CameraSelector.LENS_FACING_BACK
            }
            if (cameraInfo != null) {
                viewModel.discoverCapabilities(cameraInfo)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        val ratio = if (isLandscape) 16f / 9f else 9f / 16f
        
        // Keep one PreviewView instance for the whole screen. Recreating the
        // PreviewView while CameraX is bound can leave the surface disconnected
        // and produce a completely black preview.
        val previewView = remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(ratio, matchHeightConstraintsFirst = false)
        )

        LaunchedEffect(cameraProviderState.value, selectedConfig, slowMotionEnabled) {
            val provider = cameraProviderState.value
            val config = selectedConfig
            if (provider != null && config != null) {
                viewModel.bindCamera(
                    provider,
                    lifecycleOwner,
                    previewView,
                    config,
                    slowMotionEnabled
                )
            }
        }

        // Top Right Selector
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            var expanded by remember { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
                Text(
                    text = (if (slowMotionEnabled) "SlowMo: " else "") + (selectedConfig?.toString() ?: "None"),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp)
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    configs.forEach { config ->
                        DropdownMenuItem(
                            text = { Text(config.toString()) },
                            onClick = {
                                viewModel.selectConfig(config)
                                expanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text("Slow Motion Effect")
                                Switch(
                                    checked = slowMotionEnabled,
                                    onCheckedChange = { viewModel.toggleSlowMotion(it) }
                                )
                            }
                        },
                        onClick = { }
                    )
                }
            }
        }

        // Record Button
        val alignment = when (rotation) {
            Surface.ROTATION_90 -> Alignment.CenterEnd
            Surface.ROTATION_270 -> Alignment.CenterStart
            Surface.ROTATION_180 -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }

        Box(
            modifier = Modifier
                .align(alignment)
                .padding(
                    bottom = if (rotation == Surface.ROTATION_0) 64.dp else 0.dp,
                    end = if (rotation == Surface.ROTATION_90) 64.dp else 0.dp,
                    top = if (rotation == Surface.ROTATION_180) 64.dp else 0.dp,
                    start = if (rotation == Surface.ROTATION_270) 64.dp else 0.dp
                )
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(4.dp, Color.Gray, CircleShape)
                .clickable { viewModel.toggleRecording() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (isRecording) 32.dp else 60.dp)
                    .clip(if (isRecording) MaterialTheme.shapes.small else CircleShape)
                    .background(Color.Red)
            )
        }
    }
}
