package com.example.philippinecurrencyidentifier

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent


class MyAccessibilityService : AccessibilityService() {

    private val TAG = "MyAccessibilityService"

    private var lastVolumeUpPressTime: Long = 0
    private val doublePressInterval: Long = 400 // Interval in ms to consider for double press

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo()

        // Configuration code as you have it...
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_FOCUSED
        info.packageNames = arrayOf("com.example.android.myFirstApp", "com.example.android.mySecondApp")
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
        info.notificationTimeout = 100
        this.serviceInfo = info

        Log.d(TAG, "SERVICE CONNECTED")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {

        // FOR DEBUGGING -- CURRENTLY, this function does not trigger.

        Log.d(TAG, "Key event: ${event.keyCode}")
        return super.onKeyEvent(event)
    }

    private fun openApp() {
        // Replace "your.package.name" with your app's package name
        val launchIntent = Intent(this, MainActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
}