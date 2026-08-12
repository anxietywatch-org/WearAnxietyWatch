package com.anxietywatch.mobile.fog.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FogOutboxEntry::class], version = 1, exportSchema = false)
abstract class FogDatabase : RoomDatabase() {
    abstract fun fogOutboxDao(): FogOutboxDao

    companion object {
        private const val DB_NAME = "fog_outbox.db"

        @Volatile
        private var instance: FogDatabase? = null

        fun get(context: Context): FogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FogDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}
