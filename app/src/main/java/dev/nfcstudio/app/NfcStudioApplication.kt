package dev.nfcstudio.app

import android.app.Application

/** Application entry point; owns the manual dependency container. */
class NfcStudioApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
