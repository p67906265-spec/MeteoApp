package com.p67906265.meteoapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WeatherWidgetStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            WeatherWidgetRefreshScheduler.schedule(context)
            WeatherWidgetRefreshScheduler.refreshNow(context)
        }
    }
}
