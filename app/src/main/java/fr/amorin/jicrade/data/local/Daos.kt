package fr.amorin.jicrade.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE id = :id")
    fun observe(id: String): Flow<AlertEntity?>

    @Query("SELECT * FROM alerts")
    suspend fun all(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun get(id: String): AlertEntity?

    @Upsert
    suspend fun upsert(alert: AlertEntity)

    @Upsert
    suspend fun upsertAll(alerts: List<AlertEntity>)

    @Query("DELETE FROM alerts WHERE id NOT IN (:keep)")
    suspend fun deleteOthers(keep: List<String>)

    @Transaction
    suspend fun replaceAll(alerts: List<AlertEntity>) {
        upsertAll(alerts)
        deleteOthers(alerts.map { it.id })
    }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT accountId FROM users WHERE accountId IN (:ids)")
    suspend fun knownIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY name")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM on_call_periods ORDER BY start")
    fun observePeriods(): Flow<List<OnCallPeriodEntity>>

    @Query("SELECT * FROM schedules")
    suspend fun schedules(): List<ScheduleEntity>

    @Query("SELECT * FROM on_call_periods ORDER BY start")
    suspend fun periods(): List<OnCallPeriodEntity>

    @Query("UPDATE schedules SET onCallIds = :onCallIdsJson, fetchedAt = :fetchedAt WHERE id = :id")
    suspend fun updateOnCall(id: String, onCallIdsJson: String, fetchedAt: Long)

    @Upsert
    suspend fun upsertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE id NOT IN (:keep)")
    suspend fun deleteSchedulesOthers(keep: List<String>)

    @Insert
    suspend fun insertPeriods(periods: List<OnCallPeriodEntity>)

    @Query("DELETE FROM on_call_periods")
    suspend fun clearPeriods()

    @Transaction
    suspend fun replaceAll(schedules: List<ScheduleEntity>, periods: List<OnCallPeriodEntity>) {
        upsertSchedules(schedules)
        deleteSchedulesOthers(schedules.map { it.id })
        clearPeriods()
        insertPeriods(periods)
    }
}
