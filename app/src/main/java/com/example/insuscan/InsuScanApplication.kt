package com.example.insuscan

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.insuscan.appdata.AppDataStore
import com.example.insuscan.utils.FileLogger

class InsuScanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        AppDataStore.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppForegroundObserver)
    }
}