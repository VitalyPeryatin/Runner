package com.infinity_coder.runner.ui

import android.app.Service
import android.content.Intent
import android.os.IBinder

class NavigationService: Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}