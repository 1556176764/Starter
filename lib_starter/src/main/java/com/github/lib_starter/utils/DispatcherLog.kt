package com.github.lib_starter.utils

import android.util.Log

object DispatcherLog {
    var isDebug = false

    @JvmStatic
    fun i(msg: String?) {
        if (msg == null) {
            return
        }
        Log.i("StartTask", msg)
    }
}