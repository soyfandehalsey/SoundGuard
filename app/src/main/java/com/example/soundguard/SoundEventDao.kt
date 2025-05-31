package com.example.soundguard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SoundEventDao {
    @Insert
    suspend fun insert(event: SoundEvent)

    @Query("SELECT * FROM SoundEvent ORDER BY timestamp DESC")
    suspend fun getAll(): List<SoundEvent>
}