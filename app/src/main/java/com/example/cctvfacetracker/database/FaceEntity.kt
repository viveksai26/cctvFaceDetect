package com.example.cctvfacetracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faces")
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val embedding: FloatArray
)
