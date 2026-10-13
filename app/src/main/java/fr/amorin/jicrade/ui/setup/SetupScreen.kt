package fr.amorin.jicrade.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.amorin.jicrade.data.repo.VerifiedAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: SetupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val verified = state.verified
    Scaffold(topBar = { TopAppBar(title = { Text(if (verified == null) "Connect to Jira Service Management" else "Choose your team") }) }) { padding ->
        if (verified == null) {
            CredentialsForm(state, vm, Modifier.padding(padding))
        } else {
            TeamPicker(verified, vm, Modifier.padding(padding))
        }
    }
}

@Composable
private fun TeamPicker(account: VerifiedAccount, vm: SetupViewModel, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Text(
                "Logged in as ${account.displayName}. Alerts and schedules of the selected team will be shown.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(account.teams, key = { it.teamId }) { team ->
            ListItem(
                headlineContent = { Text(team.teamName) },
                modifier = Modifier.clickable { vm.chooseTeam(team) },
            )
        }
        item { TextButton(onClick = vm::back, modifier = Modifier.padding(16.dp)) { Text("Back") } }
    }
}

@Composable
private fun CredentialsForm(state: SetupState, vm: SetupViewModel, modifier: Modifier) {
    Column(
        modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Use your Atlassian account email and an API token created at id.atlassian.com/manage-profile/security/api-tokens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.site, onValueChange = vm::onSite, singleLine = true,
            label = { Text("Site") }, suffix = { Text(".atlassian.net") },
            placeholder = { Text("mycompany") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email, onValueChange = vm::onEmail, singleLine = true,
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.token, onValueChange = vm::onToken, singleLine = true,
            label = { Text("API token") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = vm::toggleAdvanced) { Text(if (state.showAdvanced) "Hide advanced" else "Advanced") }
        if (state.showAdvanced) {
            OutlinedTextField(
                value = state.cloudId, onValueChange = vm::onCloudId, singleLine = true,
                label = { Text("Cloud ID (optional, auto-detected)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = vm::verify, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Text("Connect")
        }
        Spacer(Modifier.weight(1f))
        Text(
            "The token is stored encrypted on this device only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
