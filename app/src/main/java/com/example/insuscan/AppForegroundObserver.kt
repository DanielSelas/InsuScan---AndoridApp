package com.example.insuscan

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.insuscan.appdata.AppDataStore

object AppForegroundObserver : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        AppDataStore.refreshAll()
    }
}