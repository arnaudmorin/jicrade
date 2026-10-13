package fr.amorin.jicrade.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [AlertEntity::class, UserEntity::class, ScheduleEntity::class, OnCallPeriodEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class JicradeDb : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun userDao(): UserDao
    abstract fun scheduleDao(): ScheduleDao

    suspend fun clearAll() = withContext(Dispatchers.IO) { clearAllTables() }
}
