package com.example.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class ExamDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("ExamDeviceAdmin", "Device Administrator Enabled for EXAM Browser SMK Negeri 1 Juwangi")
        Toast.makeText(context, "Mode Admin Kiosk EXAM Browser Aktif", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w("ExamDeviceAdmin", "Device Administrator Disabled")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.i("ExamDeviceAdmin", "Entering LockTask Mode: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.i("ExamDeviceAdmin", "Exiting LockTask Mode")
    }
}
