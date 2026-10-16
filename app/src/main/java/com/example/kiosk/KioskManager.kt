package com.example.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

class KioskManager(private val context: Context) {

    private val tag = "KioskManager"
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val adminComponent = ComponentName(context, ExamDeviceAdminReceiver::class.java)

    /**
     * Checks if this app is configured as the Device Owner.
     */
    fun isDeviceOwner(): Boolean {
        return try {
            dpm?.isDeviceOwnerApp(context.packageName) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if this app is an active Device Administrator.
     */
    fun isAdminActive(): Boolean {
        return try {
            dpm?.isAdminActive(adminComponent) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches the system dialog requesting Device Administrator rights.
     */
    fun requestDeviceAdmin(activity: Activity) {
        if (!isAdminActive()) {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Diperlukan untuk mengunci aplikasi dalam Mode Ujian (Kiosk) SMK Negeri 1 Juwangi, menonaktifkan tombol navigasi dan notifikasi."
                    )
                }
                activity.startActivity(intent)
                Log.i(tag, "Requested Device Administrator permission from user")
            } catch (e: Exception) {
                Log.e(tag, "Failed to launch device admin activation", e)
            }
        }
    }

    /**
     * Checks if Lock Task mode is allowed without prompting user.
     */
    fun isLockTaskPermitted(): Boolean {
        return try {
            dpm?.isLockTaskPermitted(context.packageName) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if Lock Task mode is currently running on the device.
     */
    fun isLockTaskActive(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }

    /**
     * Configures enterprise-grade Kiosk Mode policies:
     * - Whitelists this app package for LockTask mode
     * - Disables Home button, Recent Apps, and Notifications via LOCK_TASK_FEATURE_NONE
     * - Disables status bar pull-down and keyguard
     * - Applies anti-tamper user restrictions
     */
    fun configureKioskPolicies() {
        if (isDeviceOwner() && dpm != null) {
            try {
                // 1. Whitelist this app for automatic, unprompted LockTask mode
                dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))

                // 2. Disable Home button, Recent apps, Notifications, and Global Actions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }

                // 3. Disable Status Bar (Notification Drawer) and Keyguard
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setStatusBarDisabled(adminComponent, true)
                    dpm.setKeyguardDisabled(adminComponent, true)
                }

                // 4. Anti-tamper user restrictions
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

                Log.i(tag, "Kiosk policies configured successfully: Home, Recents, and Status Bar fully locked out!")
            } catch (e: Exception) {
                Log.e(tag, "Failed to configure lock task packages or policies", e)
            }
        }
    }

    /**
     * Starts Lock Task Mode (Kiosk) on the provided Activity:
     * - Applies Device Owner policies if provisioned
     * - Restricts navigation, Home button, and Recent Apps
     * - Enforces sticky immersive fullscreen
     */
    fun startKioskMode(activity: Activity) {
        try {
            configureKioskPolicies()
            activity.startLockTask()
            applyImmersiveMode(activity)
            Log.i(tag, "startLockTask called successfully. Device locked into exam interface.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start LockTask mode", e)
        }
    }

    /**
     * Stops Lock Task Mode (Kiosk) on the provided Activity and restores normal device state.
     */
    fun stopKioskMode(activity: Activity) {
        try {
            if (isDeviceOwner() && dpm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setStatusBarDisabled(adminComponent, false)
                    dpm.setKeyguardDisabled(adminComponent, false)
                }
                try {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                } catch (_: Exception) {}
            }
            activity.stopLockTask()
            Log.i(tag, "stopLockTask called successfully. LockTask mode stopped.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop LockTask mode", e)
        }
    }

    /**
     * Enforces sticky immersive mode to keep system bars hidden during exam.
     */
    fun applyImmersiveMode(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Programmatically closes/collapses the notification drawer if the user tried to pull it down.
     */
    fun collapseStatusBar(context: Context) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                statusBarManager.getMethod("collapsePanels")
            } else {
                statusBarManager.getMethod("collapse")
            }
            method.invoke(statusBarService)
            Log.d(tag, "Notification drawer collapsed successfully")
        } catch (e: Throwable) {
            try {
                @Suppress("DEPRECATION")
                val closeDialogIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                context.sendBroadcast(closeDialogIntent)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Returns the ADB command string for schools/proctors to set the app as Device Owner:
     * adb shell dpm set-device-owner com.aistudio.exambrowser.juwangi/com.example.kiosk.ExamDeviceAdminReceiver
     */
    fun getAdbDeviceOwnerCommand(): String {
        return "adb shell dpm set-device-owner ${context.packageName}/${ExamDeviceAdminReceiver::class.java.name}"
    }
}
