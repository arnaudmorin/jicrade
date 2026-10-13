package fr.amorin.jicrade.data.auth

data class Credentials(val site: String, val email: String, val token: String) {
    val jiraBaseUrl: String get() = "https://$site.atlassian.net/"
}

data class Session(
    val credentials: Credentials,
    val cloudId: String,
    val accountId: String,
    val displayName: String,
    val teamId: String,
    val teamName: String,
) {
    val site: String get() = credentials.site
    val jiraBaseUrl: String get() = credentials.jiraBaseUrl
}
