package com.homeostasis.app.data

import androidx.room.TypeConverter
import com.google.firebase.Timestamp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(it, 0) }
    }

    @TypeConverter
    fun toTimestampLong(timestamp: Timestamp?): Long? {
        return timestamp?.seconds
    }

}