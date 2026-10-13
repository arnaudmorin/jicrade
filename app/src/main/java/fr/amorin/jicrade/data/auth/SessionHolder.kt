package fr.amorin.jicrade.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class NotLoggedInException : IllegalStateException("No session")

@Singleton
class SessionHolder @Inject constructor(private val store: CredentialsStore) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    @Volatile
    var pending: Credentials? = null

    fun credentials(): Credentials? = _session.value?.credentials ?: pending

    fun require(): Session = _session.value ?: throw NotLoggedInException()

    fun login(session: Session) {
        store.save(session)
        pending = null
        _session.value = session
    }

    fun update(transform: (Session) -> Session) {
        val updated = transform(require())
        store.save(updated)
        _session.value = updated
    }

    fun logout() {
        store.clear()
        pending = null
        _session.value = null
    }
}
