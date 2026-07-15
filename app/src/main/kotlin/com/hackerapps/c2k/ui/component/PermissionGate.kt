package com.hackerapps.c2k.ui.component

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun RequestLocationPermission(onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val alreadyGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    LaunchedEffect(Unit) {
        if (alreadyGranted) onResult(true)
        else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

// Requests ACTIVITY_RECOGNITION on API 34+, where it's required to start WorkoutService as a
// "health" foreground service type — resolves immediately on older versions, which don't
// enforce a foreground service type at all. Proceeds regardless of grant/deny, same as
// notifications; unlike location there's no treadmill-mode fallback to skip needing it, since
// the manifest declares a single service type that always applies.
@Composable
fun RequestActivityRecognitionPermission(onResult: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        LaunchedEffect(Unit) { onResult() }
        return
    }

    val context = LocalContext.current
    val alreadyGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult() }

    LaunchedEffect(Unit) {
        if (alreadyGranted) onResult()
        else launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }
}

// Requests POST_NOTIFICATIONS on API 33+; resolves immediately on older versions.
@Composable
fun RequestNotificationPermission(onResult: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(Unit) { onResult() }
        return
    }

    val context = LocalContext.current
    val alreadyGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult() }  // proceed regardless of grant/deny

    LaunchedEffect(Unit) {
        if (alreadyGranted) onResult()
        else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
