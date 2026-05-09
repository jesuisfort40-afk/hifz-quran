package com.hifz.quran.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset

@Database(
    entities = [Sourate::class, Verset::class, Session::class],
    version = 2,          // ← incrémenté pour Phase 1
    exportSchema = false
)
abstract class HifzDatabase : RoomDatabase() {

    abstract fun sourateDao(): SourateDao
    abstract fun versetDao(): VersetDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: HifzDatabase? = null

        // ── Migration v1 → v2 ─────────────────────────────────────────────────
        // Ajout de : sourateNumber, reciterId dans sourates
        //            arabicText, transliteration, translationFr dans versets
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Nouvelles colonnes sur sourates
                database.execSQL("ALTER TABLE sourates ADD COLUMN sourateNumber INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sourates ADD COLUMN reciterId TEXT NOT NULL DEFAULT ''")

                // Nouvelles colonnes sur versets
                database.execSQL("ALTER TABLE versets ADD COLUMN arabicText TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE versets ADD COLUMN transliteration TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE versets ADD COLUMN translationFr TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): HifzDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    HifzDatabase::class.java,
                    "hifz_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
