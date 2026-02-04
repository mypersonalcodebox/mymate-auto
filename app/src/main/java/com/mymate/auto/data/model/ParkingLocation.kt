package com.mymate.auto.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a saved parking location
 */
@Entity(
    tableName = "parking_locations",
    indices = [
        Index("isActive"),
        Index("timestamp")
    ]
)
data class ParkingLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val address: String? = null,
    val note: String? = null,
    val photoPath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true, // Current parking spot
    val sentToTelegram: Boolean = false
) {
    /**
     * Google Maps URL for this location
     */
    fun getMapsUrl(): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }
    
    /**
     * Short display string
     */
    fun getDisplayString(): String {
        return address ?: "📍 ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}"
    }
}
