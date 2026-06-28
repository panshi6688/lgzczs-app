package com.lgzczs.app.gecko

import android.app.Application
import org.mozilla.geckoview.GeckoRuntime

object GeckoRuntimeManager {
    private var runtime: GeckoRuntime? = null

    fun get(application: Application): GeckoRuntime {
        if (runtime == null) {
            runtime = GeckoRuntime.create(application)
        }
        return runtime!!
    }
}
