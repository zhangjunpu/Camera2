package com.junpu.camera

import android.app.Application
import com.junpu.log.L
import com.junpu.toast.toastContext
import com.junpu.utils.app

/**
 *
 * @author junpu
 * @date 2021/2/26
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        toastContext = this
        L.logEnable = true
    }
}