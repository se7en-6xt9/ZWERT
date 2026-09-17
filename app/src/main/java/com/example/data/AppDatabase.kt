package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CourseEntity::class,
        StudentEntity::class,
        ScheduleSlotEntity::class,
        AttendanceRecordEntity::class,
        UserProfileEntity::class,
        AttendanceSessionEntity::class,
        EnrollmentEntity::class,
        OfficialClassEntity::class,
        OfficialAttendanceEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private var currentUserId: String? = null

        fun getDatabase(context: Context, userId: String): AppDatabase {
            if (INSTANCE != null && currentUserId == userId) {
                return INSTANCE!!
            }
            synchronized(this) {
                // If userId changed, we create a new instance pointing to a different file
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attentis_db_$userId"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                currentUserId = userId
                return instance
            }
        }
    }
}
