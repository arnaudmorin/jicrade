package fr.amorin.jicrade.sync

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneCalendar @Inject constructor(@ApplicationContext private val context: Context) {
    private val resolver get() = context.contentResolver

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun apply(desired: List<DesiredEvent>, now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val calendarId = findCalendar() ?: createCalendar()
        val changes = planCalendar(desired, existing(calendarId), now)
        if (!changes.isEmpty) {
            val ops = ArrayList<ContentProviderOperation>()
            changes.inserts.forEach { d ->
                ops += ContentProviderOperation.newInsert(asSyncAdapter(Events.CONTENT_URI))
                    .withValues(eventValues(d).apply { put(Events.CALENDAR_ID, calendarId) })
                    .build()
            }
            changes.updates.forEach { (id, d) ->
                ops += ContentProviderOperation.newUpdate(asSyncAdapter(Events.CONTENT_URI))
                    .withSelection("${Events._ID} = ?", arrayOf(id.toString()))
                    .withValues(eventValues(d))
                    .build()
            }
            changes.deletes.forEach { id ->
                ops += ContentProviderOperation.newDelete(asSyncAdapter(Events.CONTENT_URI))
                    .withSelection("${Events._ID} = ?", arrayOf(id.toString()))
                    .build()
            }
            resolver.applyBatch(AUTHORITY, ops)
        }
        desired.size
    }

    suspend fun deleteCalendar() = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext
        resolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE),
        )
    }

    private fun findCalendar(): Long? = resolver.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID),
        "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
        arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE),
        null,
    )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    private fun createCalendar(): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(Calendars.NAME, "jicrade_oncall")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Jicrade on-call")
            put(Calendars.CALENDAR_COLOR, 0xFFB3261E.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC")
        }
        val uri = resolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)
            ?: throw IllegalStateException("Could not create the Jicrade calendar")
        return uri.lastPathSegment!!.toLong()
    }

    private fun existing(calendarId: Long): List<ExistingEvent> = resolver.query(
        Events.CONTENT_URI,
        arrayOf(Events._ID, Events.SYNC_DATA1, Events.TITLE, Events.DTSTART, Events.DTEND),
        "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0",
        arrayOf(calendarId.toString()),
        null,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                add(ExistingEvent(c.getLong(0), c.getString(1), c.getString(2).orEmpty(), c.getLong(3), c.getLong(4)))
            }
        }
    }.orEmpty()

    private fun eventValues(d: DesiredEvent) = ContentValues().apply {
        put(Events.SYNC_DATA1, d.key)
        put(Events.TITLE, d.title)
        put(Events.DESCRIPTION, "Added by Jicrade")
        put(Events.DTSTART, d.start)
        put(Events.DTEND, d.end)
        put(Events.EVENT_TIMEZONE, "UTC")
        put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
        put(Events.HAS_ALARM, 0)
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        private const val AUTHORITY = CalendarContract.AUTHORITY
        private const val ACCOUNT_NAME = "Jicrade"
        private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
    }
}
