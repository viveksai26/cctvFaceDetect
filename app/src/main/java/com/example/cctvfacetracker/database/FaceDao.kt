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

    @Query("DELETE FROM faces WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("UPDATE faces SET name = :newName WHERE name = :oldName")
    suspend fun renameByName(oldName: String, newName: String)
}
