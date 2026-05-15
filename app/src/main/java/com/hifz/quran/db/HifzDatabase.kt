package com.hifz.quran.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hifz.quran.model.Badge
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset

@Database(
    entities = [Sourate::class, Verset::class, Session::class, Badge::class],
    version = 4,
    exportSchema = false
)
abstract class HifzDatabase : RoomDatabase() {

    abstract fun sourateDao(): SourateDao
    abstract fun versetDao():  VersetDao
    abstract fun sessionDao(): SessionDao
    abstract fun badgeDao():   BadgeDao

    companion object {
        @Volatile private var INSTANCE: HifzDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sourates ADD COLUMN sourateNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sourates ADD COLUMN reciterId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE versets ADD COLUMN arabicText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE versets ADD COLUMN transliteration TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE versets ADD COLUMN translationFr TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE versets ADD COLUMN localAudioPath TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS badges (
                        id TEXT PRIMARY KEY NOT NULL,
                        titleFr TEXT NOT NULL,
                        titleAr TEXT NOT NULL,
                        description TEXT NOT NULL,
                        iconRes TEXT NOT NULL,
                        unlockedAt INTEGER NOT NULL DEFAULT 0,
                        isUnlocked INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): HifzDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    HifzDatabase::class.java,
                    "hifz_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
