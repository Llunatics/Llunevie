package com.llunaticsys.llunevie.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        BookChapterEntity::class,
        HighlightNoteEntity::class,
        DailyReadingLogEntity::class,
        ShelfEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LlunevieDatabase : RoomDatabase() {
    abstract fun dao(): LlunevieDao

    companion object {
        @Volatile
        private var INSTANCE: LlunevieDatabase? = null

        fun getInstance(context: Context): LlunevieDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LlunevieDatabase::class.java,
                    "llunevie_native_v02.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
