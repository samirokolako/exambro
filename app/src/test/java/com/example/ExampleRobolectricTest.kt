package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.UrlValidator
import com.example.session.ExamSessionManager
import com.example.ui.dialog.DEFAULT_EXIT_PIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("EXAM Browser", appName)
    }

    @Test
    fun testExitPinIs1234() {
        assertEquals("1234", DEFAULT_EXIT_PIN)
    }

    @Test
    fun testUrlValidatorAllowsHttpsOnly() {
        assertTrue(UrlValidator.isValidExamUrl("https://docs.google.com/forms/d/e/1FAIpQLSc/viewform"))
        assertTrue(UrlValidator.isValidExamUrl("https://ujian.smkn1juwangi.sch.id/test"))
        
        // Disallowed schemes
        assertFalse(UrlValidator.isValidExamUrl("http://unsecure-exam.com"))
        assertFalse(UrlValidator.isValidExamUrl("file:///sdcard/cheat.html"))
        assertFalse(UrlValidator.isValidExamUrl("javascript:alert(1)"))
        assertFalse(UrlValidator.isValidExamUrl("intent://browser"))
        assertFalse(UrlValidator.isValidExamUrl(""))
        assertFalse(UrlValidator.isValidExamUrl(null))
    }

    @Test
    fun testExamSessionManager() {
        ExamSessionManager.startSession("https://docs.google.com/forms/example")
        assertTrue(ExamSessionManager.isExamActive)
        assertFalse(ExamSessionManager.isMonitoringArmed)
        assertEquals("https://docs.google.com/forms/example", ExamSessionManager.currentExamUrl)

        // Before monitoring is armed, violations are ignored to prevent scan/loading false alarms
        val notRecorded = ExamSessionManager.recordViolation("EARLY_VIOLATION", "Ignored during transition")
        assertFalse(notRecorded)

        // Arm monitoring once page finishes loading
        ExamSessionManager.armMonitoring()
        assertTrue(ExamSessionManager.isMonitoringArmed)

        val recorded = ExamSessionManager.recordViolation("TEST_VIOLATION", "Testing violation record")
        assertTrue(recorded)
        assertEquals(1, ExamSessionManager.getViolationCount())

        ExamSessionManager.endSession()
        assertFalse(ExamSessionManager.isExamActive)
        assertFalse(ExamSessionManager.isMonitoringArmed)
    }

    @Test
    fun testSplashActivityLifecycle() {
        val controller = org.robolectric.Robolectric.buildActivity(SplashActivity::class.java).setup()
        val activity = controller.get()
        org.junit.Assert.assertNotNull(activity)
        assertEquals("EXAM Browser", activity.getString(R.string.app_name))
        assertEquals("SMK NEGERI 1 JUWANGI", activity.getString(R.string.school_name))
    }

    @Test
    fun testMainActivityLifecycle() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        org.junit.Assert.assertNotNull(activity)
        assertEquals("EXAM Browser", activity.getString(R.string.app_name))
    }

    @Test
    fun testKioskManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val kioskManager = com.example.kiosk.KioskManager(context)
        org.junit.Assert.assertNotNull(kioskManager)
        assertEquals(context.packageName, kioskManager.adminComponent.packageName)
        assertEquals("com.example.kiosk.ExamDeviceAdminReceiver", kioskManager.adminComponent.className)
        assertTrue(kioskManager.getAdbDeviceOwnerCommand().contains("set-device-owner"))
    }
}
