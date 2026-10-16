package com.spisokryadom.app.data.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "cities")
data class CityEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
