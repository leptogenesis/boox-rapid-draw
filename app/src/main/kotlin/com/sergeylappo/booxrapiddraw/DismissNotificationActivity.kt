package com.sergeylappo.booxrapiddraw

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class DismissNotificationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra("action") == "SETTINGS") {
            startService(
                Intent(this, OverlayShowingService::class.java).apply {
                    action = "SETTINGS"
                }
            )
        }
        finish()
    }
}
