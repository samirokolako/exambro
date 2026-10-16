package com.example.session

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExamViolation(
    val timestamp: Long,
    val formattedTime: String,
    val type: String,
    val details: String
)

object ExamSessionManager {

    private var _isExamActive: Boolean = false
    val isExamActive: Boolean get() = _isExamActive

    // Monitoring is armed only AFTER the exam page is loaded and transition completes
    // This prevents false alarms when scanning QR code, switching screens, or starting lock task
    private var _isMonitoringArmed: Boolean = false
    val isMonitoringArmed: Boolean get() = _isMonitoringArmed

    private var _currentExamUrl: String? = null
    val currentExamUrl: String? get() = _currentExamUrl

    private val _violations = mutableListOf<ExamViolation>()
    val violations: List<ExamViolation> get() = _violations.toList()

    private var lastViolationTimestamp: Long = 0L

    fun startSession(url: String) {
        _isExamActive = true
        _isMonitoringArmed = false // Disarmed initially to avoid false alarms during scan & load
        _currentExamUrl = url
        _violations.clear()
        lastViolationTimestamp = 0L
    }

    /**
     * Arms cheating detection once the exam sheet has loaded cleanly.
     */
    fun armMonitoring() {
        if (_isExamActive) {
            _isMonitoringArmed = true
        }
    }

    fun endSession() {
        _isExamActive = false
        _isMonitoringArmed = false
        _currentExamUrl = null
    }

    /**
     * Records a cheat violation if debounce window has passed and monitoring is armed.
     * Returns true if newly recorded, false if ignored.
     */
    fun recordViolation(type: String, details: String): Boolean {
        if (!_isExamActive || !_isMonitoringArmed) return false

        val now = System.currentTimeMillis()
        if (now - lastViolationTimestamp < 3000L) {
            // Debounce within 3 seconds
            return false
        }
        lastViolationTimestamp = now

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        val violation = ExamViolation(
            timestamp = now,
            formattedTime = timeStr,
            type = type,
            details = details
        )
        _violations.add(violation)
        return true
    }

    fun getViolationCount(): Int = _violations.size
}
