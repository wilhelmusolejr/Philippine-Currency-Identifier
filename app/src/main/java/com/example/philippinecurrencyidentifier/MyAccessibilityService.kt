package com.example.philippinecurrencyidentifier

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent


class MyAccessibilityService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {

        Log.d("MyAccessibilityService", "onKeyEvent: initialized")

        val action = event.action
        val keyCode = event.keyCode
        return if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                Log.d("MyAccessibilityService", "KeyUp")
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                Log.d("MyAccessibilityService", "KeyDown")
            }
            true
        } else {
            super.onKeyEvent(event)
        }
    }

    override fun onServiceConnected() {

        val info = AccessibilityServiceInfo()

        // Configuration code as you have it...
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_FOCUSED
        info.packageNames = arrayOf("com.example.android.myFirstApp", "com.example.android.mySecondApp")
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
        info.notificationTimeout = 100
        this.serviceInfo = info

        // TRIGGERS TO OPEN THE APP
        // NEED TO MOVE TO A FUNCTION THAT TRIGGERS WHEN A VOLUME KEY WAS PRESSED
//        val launchIntent = Intent(this, MainActivity::class.java)
//        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        startActivity(launchIntent)

        Log.d("MyAccessibilityService", "SERVICE CONNECTED")
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
}