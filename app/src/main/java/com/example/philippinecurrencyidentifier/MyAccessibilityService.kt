package com.example.philippinecurrencyidentifier

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo()

        // Set the type of events that this service wants to listen to. Others
        // aren't passed to this service.
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_FOCUSED

        // If you only want this service to work with specific apps, set their
        // package names here. Otherwise, when the service is activated, it
        // listens to events from all apps.
        info.packageNames = arrayOf("com.example.android.myFirstApp", "com.example.android.mySecondApp")

        // Set the type of feedback your service provides.
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN

        // Here you can set additional flags, for example:
        // info.flags = AccessibilityServiceInfo.DEFAULT

        info.notificationTimeout = 100

        this.serviceInfo = info
    }
}