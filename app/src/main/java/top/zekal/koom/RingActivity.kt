package top.zekal.koom

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import top.zekal.koom.ui.KoomTheme
import top.zekal.koom.ui.Palette
import top.zekal.koom.ui.RingScreen

/** Opened through the system's alarm full-screen notification, not a background activity start. */
class RingActivity : ComponentActivity() {
    private val store by lazy { AlarmStore(this) }
    private val refresh = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // If Android delivered the PendingIntent but suspended the audio service,
        // a now-visible activity is allowed to request foreground playback again.
        try {
            if (store.activeIds().isNotEmpty()) RingService.start(this)
        } catch (error: Exception) {
            AlarmDiagnostics(this).record("RESTORE_RING_FAILED",
                detail = error.message.orEmpty())
        }

        setContent {
            KoomTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(), color = Palette.night
                    ) {
                        refresh.intValue
                        val id = store.activeIds().firstOrNull()
                        val alarm = id?.let { store.byId(it) }
                        if (id == null || alarm == null) {
                            LaunchedEffect(id) { finish() }
                        } else {
                            RingScreen(alarm = alarm, onSolved = {
                                store.dismiss(id)
                                RingService.refresh(this@RingActivity)
                                refresh.intValue++
                                if (store.activeIds().isEmpty()) finish()
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        refresh.intValue++
    }
}
