package com.example.soundguard

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity
data class SoundEvent(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val type: String,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
)