package com.llunaticsys.llunevie.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        BookChapterEntity::class,
        HighlightNoteEntity::class,
        DailyReadingLogEntity::class,
        ShelfEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LlunevieDatabase : RoomDatabase() {
    abstract fun dao(): LlunevieDao

    companion object {
        @Volatile
        private var INSTANCE: LlunevieDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN tocIndexed INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN tocIndexVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): LlunevieDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LlunevieDatabase::class.java,
                    "llunevie_native_v02.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
