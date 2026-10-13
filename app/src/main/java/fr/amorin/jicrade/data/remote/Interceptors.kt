package fr.amorin.jicrade.data.remote

import fr.amorin.jicrade.data.auth.SessionHolder
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(private val sessionHolder: SessionHolder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val creds = sessionHolder.credentials()
        if (creds == null || !(host == "api.atlassian.com" || host.endsWith(".atlassian.net"))) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", Credentials.basic(creds.email, creds.token))
                .header("Accept", "application/json")
                .build()
        )
    }
}

class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0
        while ((response.code == 429 || response.code == 503) && attempt < maxRetries) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 30)
            val delayMs = (retryAfter ?: (1L shl attempt)) * 1000
            response.close()
            Thread.sleep(delayMs)
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }
}
