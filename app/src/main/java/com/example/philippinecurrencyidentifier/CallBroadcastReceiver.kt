package com.example.philippinecurrencyidentifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("CallBroadcastReceiver", "onReceive: RECEIVED")

        val i = Intent()
        i.setClassName("com.package", "com.package.MainActivity")
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(i)
    }
}