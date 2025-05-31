package com.example.soundguard

import android.app.Application
import androidx.room.Room

class SoundGuardApp : Application() {
    companion object {
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "soundguard-db"
        ).build()
    }
}