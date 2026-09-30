package com.evolune.app

import android.app.Application
import com.evolune.app.data.AppPreferences
import com.evolune.app.data.EvoluneDatabase
import com.evolune.app.data.EvoluneRepository
import com.evolune.app.notifications.NotificationChannels

class EvoluneApplication : Application() {
    val database by lazy { EvoluneDatabase(this) }
    val preferences by lazy { AppPreferences(this) }
    val repository by lazy { EvoluneRepository(database, preferences) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }
}
