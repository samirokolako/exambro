package com.example

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.audio.ExamAlarmManager
import com.example.kiosk.KioskManager
import com.example.session.ExamSessionManager
import com.example.ui.browser.ExamWebViewScreen
import com.example.ui.dialog.ExitConfirmDialog
import com.example.ui.home.HomeScreen
import com.example.ui.loading.ExamLoadingScreen
import com.example.ui.scanner.ScannerScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.MyApplicationTheme

enum class Screen {
    SPLASH,
    HOME,
    SCANNER,
    EXAM_LOADING,
    EXAM
}

class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"
    lateinit var alarmManager: ExamAlarmManager
    lateinit var kioskManager: KioskManager

    private var currentExamUrl: String? = null
    private var isExitDialogVisibleState = mutableStateOf(false)
    private var currentScreenState = mutableStateOf(Screen.HOME)
    private var screenCaptureCallback: Activity.ScreenCaptureCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Hardware Screenshot and Recording Blocker (Active in release builds)
        if (!BuildConfig.DEBUG) {
            try {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } catch (_: Throwable) {}
        }

        // 2. Keep screen turned on throughout the exam
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Throwable) {}

        // Initialize Core Managers
        alarmManager = ExamAlarmManager(applicationContext)
        kioskManager = KioskManager(applicationContext)

        // Apply sticky immersive mode to conceal status and navigation bars
        kioskManager.applyImmersiveMode(this)

        // 3. Android 14+ Screenshot Detection: Ring loud Alarm B if screenshot attempted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val callback = Activity.ScreenCaptureCallback {
                    if (currentScreenState.value != Screen.SCANNER) {
                        Log.w(tag, "Screenshot attempt detected by system! Triggering Alarm B at MAX volume.")
                        ExamSessionManager.recordViolation(
                            type = "UPAYA_SCREENSHOT",
                            details = "Upaya tangkapan layar terdeteksi"
                        )
                        alarmManager.enforceMaximumAlarmVolume()
                        alarmManager.startAlarmB()
                    }
                }
                screenCaptureCallback = callback
                registerScreenCaptureCallback(mainExecutor, callback)
            } catch (e: Throwable) {
                Log.w(tag, "Screen capture callback could not be registered: ${e.message}")
            }
        }

        setContent {
            MyApplicationTheme {
                MainAppContent()
            }
        }
    }

    @Composable
    private fun MainAppContent() {
        var currentScreen by currentScreenState
        var showExitDialog by isExitDialogVisibleState
        val isKioskActive = kioskManager.isLockTaskActive() || kioskManager.isLockTaskPermitted()

        // Automatically start Kiosk Mode (Screen Pinning) on launch
        LaunchedEffect(Unit) {
            kioskManager.startKioskMode(this@MainActivity)
        }

        // Global back press handling
        BackHandler(enabled = true) {
            when (currentScreen) {
                Screen.HOME -> {
                    // Back button on Home triggers Exit Confirmation Dialog with Alarm A
                    showExitDialog = true
                }
                Screen.SCANNER -> {
                    // Back from scanner returns to Home screen
                    currentScreen = Screen.HOME
                }
                Screen.EXAM_LOADING -> {
                    // Locked during loading
                }
                Screen.EXAM -> {
                    // Trapped in ExamWebViewScreen
                }
                else -> {}
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    Screen.SPLASH -> {
                        SplashScreen(
                            onSplashFinished = {
                                currentScreen = Screen.HOME
                            }
                        )
                    }

                    Screen.HOME -> {
                        HomeScreen(
                            isKioskActive = isKioskActive,
                            onScanQrClicked = {
                                currentScreen = Screen.SCANNER
                            },
                            onExitAppClicked = {
                                showExitDialog = true
                            }
                        )
                    }

                    Screen.SCANNER -> {
                        ScannerScreen(
                            onBackClicked = {
                                currentScreen = Screen.HOME
                            },
                            onValidUrlScanned = { url ->
                                currentExamUrl = url
                                currentScreen = Screen.EXAM_LOADING
                                kioskManager.startKioskMode(this@MainActivity)
                            }
                        )
                    }

                    Screen.EXAM_LOADING -> {
                        val url = currentExamUrl ?: "https://docs.google.com/forms"
                        ExamLoadingScreen(
                            examUrl = url,
                            onLoadingFinished = {
                                currentScreen = Screen.EXAM
                            }
                        )
                    }

                    Screen.EXAM -> {
                        val url = currentExamUrl ?: "https://docs.google.com/forms"
                        ExamWebViewScreen(
                            examUrl = url,
                            alarmManager = alarmManager,
                            onRequestExit = {
                                showExitDialog = true
                            }
                        )
                    }
                }
            }

            // Top Status Bar Touch Blocker: Consumes drag gestures from top edge
            // so students cannot pull down the notification shade!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .align(Alignment.TopCenter)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val hasDownwardDrag = event.changes.any { it.position.y > it.previousPosition.y + 4 }
                                if (hasDownwardDrag && currentScreen != Screen.SCANNER && !isExitDialogVisibleState.value) {
                                    kioskManager.collapseStatusBar(this@MainActivity)
                                    alarmManager.enforceMaximumAlarmVolume()
                                    alarmManager.startAlarmB()
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )

            // Exit Dialog (Triggers Alarm A, asks for Supervisor PIN 1234)
            if (showExitDialog) {
                ExitConfirmDialog(
                    alarmManager = alarmManager,
                    onDismiss = {
                        showExitDialog = false
                    },
                    onConfirmedExit = {
                        showExitDialog = false
                        ExamSessionManager.endSession()
                        kioskManager.stopKioskMode(this@MainActivity)
                        finishAffinity()
                    }
                )
            }
        }
    }

    /**
     * Detection for Notification shade pull-down, floating apps (e.g. WhatsApp bubble/chat head),
     * or app switcher attempts.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::kioskManager.isInitialized || !::alarmManager.isInitialized) return
        if (hasFocus) {
            kioskManager.applyImmersiveMode(this)
        } else {
            // When focus is lost and NOT in supervisor exit dialog and NOT during scanner camera permission:
            if (!isExitDialogVisibleState.value && currentScreenState.value != Screen.SCANNER) {
                // Instantly collapse the notification drawer!
                kioskManager.collapseStatusBar(this)

                @Suppress("DEPRECATION")
                try {
                    val closeDialogIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    sendBroadcast(closeDialogIntent)
                } catch (_: Exception) {}

                Log.w(tag, "Notification drawer or overlay opened! Ringing Alarm B at MAX volume.")
                ExamSessionManager.recordViolation(
                    type = "KEHILANGAN_FOKUS",
                    details = "Aplikasi kehilangan fokus (notifikasi dibuka atau aplikasi melayang/WA muncul)"
                )
                alarmManager.enforceMaximumAlarmVolume()
                alarmManager.startAlarmB()
            }
        }
    }

    /**
     * Detection for Home button / Recent Apps button press attempts
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!::alarmManager.isInitialized) return
        if (!isExitDialogVisibleState.value && currentScreenState.value != Screen.SCANNER) {
            Log.w(tag, "User leave hint triggered! Ringing Alarm B at MAX volume.")
            ExamSessionManager.recordViolation(
                type = "UPAYA_KELUAR",
                details = "Pengguna menekan tombol Home atau mencoba pindah aplikasi"
            )
            alarmManager.enforceMaximumAlarmVolume()
            alarmManager.startAlarmB()
        }
    }

    /**
     * Detection for Multi-Window / Split-Screen attempts
     */
    @Deprecated("Deprecated in Java")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        @Suppress("DEPRECATION")
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (!::alarmManager.isInitialized) return
        if (isInMultiWindowMode && !isExitDialogVisibleState.value && currentScreenState.value != Screen.SCANNER) {
            Log.w(tag, "Multi-window mode detected! Triggering Alarm B at MAX volume.")
            ExamSessionManager.recordViolation(
                type = "MULTISCREEN_DILARANG",
                details = "Pengguna mencoba membagi layar (split-screen / multiscreen)"
            )
            alarmManager.enforceMaximumAlarmVolume()
            alarmManager.startAlarmB()
        }
    }

    /**
     * Detection for Picture-in-Picture attempts
     */
    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        @Suppress("DEPRECATION")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!::alarmManager.isInitialized) return
        if (isInPictureInPictureMode && !isExitDialogVisibleState.value && currentScreenState.value != Screen.SCANNER) {
            Log.w(tag, "Picture-in-picture mode detected! Triggering Alarm B at MAX volume.")
            ExamSessionManager.recordViolation(
                type = "PIP_DILARANG",
                details = "Pengguna mencoba mode picture-in-picture"
            )
            alarmManager.enforceMaximumAlarmVolume()
            alarmManager.startAlarmB()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!::alarmManager.isInitialized) {
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                // If any alarm is running or exam is active, block volume down and forcefully boost to MAX!
                if (alarmManager.isAnyAlarmRunning() || ExamSessionManager.isExamActive) {
                    Log.w(tag, "Volume Down/Mute key blocked! Forcing volume UP to MAXIMUM.")
                    alarmManager.enforceMaximumAlarmVolume()
                    return true // Swallow key event
                }
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_ALL_APPS -> {
                Log.w(tag, "System navigation key intercepted: $keyCode. Ringing Alarm B!")
                if (!isExitDialogVisibleState.value && currentScreenState.value != Screen.SCANNER) {
                    alarmManager.enforceMaximumAlarmVolume()
                    alarmManager.startAlarmB()
                }
                return true // Swallow key event so it never switches apps or goes to Home!
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!::alarmManager.isInitialized) {
            return super.onKeyUp(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (alarmManager.isAnyAlarmRunning() || ExamSessionManager.isExamActive) {
                alarmManager.enforceMaximumAlarmVolume()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        if (::kioskManager.isInitialized) {
            kioskManager.applyImmersiveMode(this)
            if (currentScreenState.value != Screen.SCANNER) {
                kioskManager.collapseStatusBar(this)
            }
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                screenCaptureCallback?.let { unregisterScreenCaptureCallback(it) }
            } catch (_: Throwable) {}
        }
        try {
            if (::alarmManager.isInitialized) {
                alarmManager.releaseAll()
            }
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
