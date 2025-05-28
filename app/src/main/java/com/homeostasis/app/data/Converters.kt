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

    @TypeConverter
    fun fromString(value: String?): List<com.homeostasis.app.data.model.TaskCompletion>? {
        val listType: Type = object : TypeToken<List<com.homeostasis.app.data.model.TaskCompletion>?>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun toString(list: List<com.homeostasis.app.data.model.TaskCompletion>?): String? {
        return Gson().toJson(list)
    }
}