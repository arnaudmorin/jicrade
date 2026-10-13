package fr.amorin.jicrade.data.remote

import fr.amorin.jicrade.data.remote.dto.ApiErrorsDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

class ApiException(val code: Int, message: String) : IOException(message) {
    val isUnauthorized: Boolean get() = code == 401
}

fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> message ?: "HTTP $code"
    is HttpException -> "HTTP ${code()}"
    is IOException -> message ?: "Network error"
    else -> message ?: toString()
}

suspend fun <T> api(json: Json, label: String? = null, block: suspend () -> T): T {
    val prefix = label?.let { "$it: " }.orEmpty()
    try {
        return block()
    } catch (e: SerializationException) {
        throw ApiException(0, "${prefix}unexpected response format (${e.message?.lineSequence()?.first()?.take(160)})")
    } catch (e: ApiException) {
        throw ApiException(e.code, prefix + e.message)
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { json.decodeFromString(ApiErrorsDto.serializer(), body) }.getOrNull()
        val msg = parsed?.errors?.firstOrNull()?.title
            ?: parsed?.message
            ?: when (e.code()) {
                401 -> "Authentication failed: check email and API token"
                403 -> "Forbidden: your account lacks access to this resource"
                404 -> "Not found"
                else -> "HTTP ${e.code()}"
            }
        throw ApiException(e.code(), prefix + msg)
    }
}
