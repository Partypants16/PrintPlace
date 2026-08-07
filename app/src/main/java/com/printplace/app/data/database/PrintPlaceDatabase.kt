package com.printplace.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ModelEntity::class], version = 1, exportSchema = true)
abstract class PrintPlaceDatabase : RoomDatabase() {

    abstract fun modelDao(): ModelDao

    companion object {
        private const val DATABASE_NAME = "printplace.db"

        fun create(context: Context): PrintPlaceDatabase =
            Room.databaseBuilder(context, PrintPlaceDatabase::class.java, DATABASE_NAME).build()
    }
}
