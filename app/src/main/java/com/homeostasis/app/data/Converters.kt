package com.homeostasis.app.data

import androidx.room.TypeConverter
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale


class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Timestamp? {
        return value?.let { Timestamp(it, 0) }
    }

    @TypeConverter
    fun toTimestampLong(timestamp: Timestamp?): Long? {
        return timestamp?.seconds
    }

    companion object { // Utility functions are often best placed in a companion object
        /**
         * Formats a Firebase Timestamp into a human-readable date/time string.
         * @param timestamp The Firebase Timestamp to format.
         * @param formatPattern The desired output format (e.g., "MMM dd, yyyy 'at' hh:mm a").
         * @return A formatted date string, or an empty string if the timestamp is null.
         */
        fun formatTimestampToString(timestamp: Timestamp?, formatPattern: String = "MMM dd, yyyy 'at' hh:mm a"): String {
            return timestamp?.toDate()?.let { date ->
                val sdf = SimpleDateFormat(formatPattern, Locale.getDefault())
                sdf.format(date)
            } ?: "" // Return empty string or some placeholder if timestamp is null
        }

        // You could also have a version that returns nullable if you prefer
        // fun formatTimestampToStringNullable(timestamp: Timestamp?, formatPattern: String = "MMM dd, yyyy 'at' hh:mm a"): String? {
        //     return timestamp?.toDate()?.let { date ->
        //         val sdf = SimpleDateFormat(formatPattern, Locale.getDefault())
        //         sdf.format(date)
        //     }
        // }
    }

}