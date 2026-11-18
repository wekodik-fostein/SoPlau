package com.soplau.rp

import android.app.Application
import androidx.room.Room
import com.soplau.rp.data.db.AppDatabase
import com.soplau.rp.data.repository.ChatRepository
import com.soplau.rp.data.settings.SettingsStore
import com.soplau.rp.util.ImageStorage

class SoPlauApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container. Small enough that Hilt would be overkill. */
class AppContainer(app: SoPlauApp) {
    val database: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "soplau.db")
        .fallbackToDestructiveMigration()
        .build()

    val repository = ChatRepository(database)
    val settings = SettingsStore(app)
    val images = ImageStorage(app)
}
