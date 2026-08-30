package com.example.cctvfacetracker.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FaceEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao
}
