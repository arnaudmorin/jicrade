package fr.amorin.jicrade.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.amorin.jicrade.data.remote.dto.TeamDto
import fr.amorin.jicrade.data.remote.toUserMessage
import fr.amorin.jicrade.data.repo.AuthRepository
import fr.amorin.jicrade.data.repo.VerifiedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(
    val site: String = "",
    val email: String = "",
    val token: String = "",
    val cloudId: String = "",
    val showAdvanced: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val verified: VerifiedAccount? = null,
)

@HiltViewModel
class SetupViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    fun onSite(v: String) = _state.update { it.copy(site = v, error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onToken(v: String) = _state.update { it.copy(token = v, error = null) }
    fun onCloudId(v: String) = _state.update { it.copy(cloudId = v, error = null) }
    fun toggleAdvanced() = _state.update { it.copy(showAdvanced = !it.showAdvanced) }
    fun back() = _state.update { it.copy(verified = null) }

    fun verify() {
        val s = _state.value
        if (s.site.isBlank() || s.email.isBlank() || s.token.isBlank()) {
            _state.update { it.copy(error = "Site, email and API token are required") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val account = auth.verify(s.site, s.email, s.token, s.cloudId)
                if (account.teams.isEmpty()) {
                    _state.update { it.copy(loading = false, error = "Credentials OK but you are not a member of any Operations team") }
                } else {
                    _state.update { it.copy(loading = false, verified = account) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun chooseTeam(team: TeamDto) {
        val account = _state.value.verified ?: return
        auth.login(account, team)
    }
}
