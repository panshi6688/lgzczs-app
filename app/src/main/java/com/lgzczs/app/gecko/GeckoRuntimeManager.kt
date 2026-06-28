package com.lgzczs.app.gecko

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeManager {
    private var runtime: GeckoRuntime? = null

    fun get(application: Application): GeckoRuntime {
        if (runtime == null) {
            runtime = GeckoRuntime.create(application, GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(true)
                .build())
        }
        return runtime!!
    }
}
