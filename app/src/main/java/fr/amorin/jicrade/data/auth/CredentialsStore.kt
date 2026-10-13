package fr.amorin.jicrade.data.auth

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Session? {
        val site = prefs.getString("site", null) ?: return null
        val email = prefs.getString("email", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Session(
            credentials = Credentials(site, email, token),
            cloudId = prefs.getString("cloudId", null) ?: return null,
            accountId = prefs.getString("accountId", null) ?: return null,
            displayName = prefs.getString("displayName", "") ?: "",
            teamId = prefs.getString("teamId", null) ?: return null,
            teamName = prefs.getString("teamName", null) ?: return null,
        )
    }

    fun save(session: Session) = prefs.edit {
        putString("site", session.credentials.site)
        putString("email", session.credentials.email)
        putString("token", session.credentials.token)
        putString("cloudId", session.cloudId)
        putString("accountId", session.accountId)
        putString("displayName", session.displayName)
        putString("teamId", session.teamId)
        putString("teamName", session.teamName)
    }

    fun clear() = prefs.edit { clear() }
}
