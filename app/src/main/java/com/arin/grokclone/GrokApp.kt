package com.arin.grokclone

import android.app.Application
import com.arin.grokclone.data.AppContainer

class GrokApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
