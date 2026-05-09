package com.hifz.quran.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hifz.quran.model.Session
import com.hifz.quran.model.Sourate
import com.hifz.quran.model.Verset

@Database(
    entities = [Sourate::class, Verset::class, Session::class],
    version = 1,
    exportSchema = false
)
abstract class HifzDatabase : RoomDatabase() {

    abstract fun sourateDao(): SourateDao
    abstract fun versetDao(): VersetDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: HifzDatabase? = null

        fun getDatabase(context: Context): HifzDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HifzDatabase::class.java,
                    "hifz_quran_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
