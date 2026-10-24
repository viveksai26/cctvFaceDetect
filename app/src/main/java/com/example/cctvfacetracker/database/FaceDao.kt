package com.example.cctvfacetracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FaceDao {
    @Query("SELECT * FROM faces")
    suspend fun getAll(): List<FaceEntity>

    @Insert
    suspend fun insert(face: FaceEntity)
}
