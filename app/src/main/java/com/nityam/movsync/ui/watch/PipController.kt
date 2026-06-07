package com.nityam.movsync.ui.watch

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.nityam.movsync.R

class PipController(private val context: Context) {

    private val activity: Activity? = context as? Activity
    
    val isPipSupported: Boolean
        get() = activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true

    fun updatePipParams(isPlaying: Boolean, showControls: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
            val actions = mutableListOf<RemoteAction>()
            
            if (showControls) {
                // Create Play/Pause RemoteAction
                val actionIcon = Icon.createWithResource(
                    context,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
                val actionTitle = if (isPlaying) "Pause" else "Play"
                val actionIntent = Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    if (isPlaying) 1 else 0,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val remoteAction = RemoteAction(actionIcon, actionTitle, actionTitle, pendingIntent)
                actions.add(remoteAction)
            }

            val paramsBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(actions)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                paramsBuilder.setAutoEnterEnabled(true)
                paramsBuilder.setSeamlessResizeEnabled(true)
            }

            activity?.setPictureInPictureParams(paramsBuilder.build())
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
            val paramsBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            activity?.enterPictureInPictureMode(paramsBuilder.build())
        }
    }

    fun clearPipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
            val paramsBuilder = PictureInPictureParams.Builder()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                paramsBuilder.setAutoEnterEnabled(false)
            }
            activity?.setPictureInPictureParams(paramsBuilder.build())
        }
    }

    companion object {
        const val ACTION_PIP_PLAY_PAUSE = "com.nityam.movsync.PIP_PLAY_PAUSE"
    }
}

@Composable
fun rememberPipController(): PipController {
    val context = LocalContext.current
    return remember(context) { PipController(context) }
}

@Composable
fun PipActionReceiver(onPlayPauseToggle: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == PipController.ACTION_PIP_PLAY_PAUSE) {
                    onPlayPauseToggle()
                }
            }
        }
        val filter = IntentFilter(PipController.ACTION_PIP_PLAY_PAUSE)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

@Composable
fun rememberIsInPipMode(): State<Boolean> {
    val activity = LocalContext.current as? Activity
    val pipState = remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) activity?.isInPictureInPictureMode == true else false) }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        
        val listener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            pipState.value = info.isInPictureInPictureMode
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val componentActivity = activity as? androidx.activity.ComponentActivity
            componentActivity?.addOnPictureInPictureModeChangedListener(listener)
            
            onDispose {
                componentActivity?.removeOnPictureInPictureModeChangedListener(listener)
            }
        } else {
            onDispose {}
        }
    }
    
    return pipState
}
