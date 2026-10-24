package com.example.cctvfacetracker.database

import androidx.room.TypeConverter
import java.nio.ByteBuffer

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * 4)
        buffer.asFloatBuffer().put(value)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(value: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(value)
        val floatArray = FloatArray(value.size / 4)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }
}
