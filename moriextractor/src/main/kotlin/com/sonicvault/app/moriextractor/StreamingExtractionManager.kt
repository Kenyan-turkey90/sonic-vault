/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.moriextractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

fun interface ExtractorAuthenticationCallback {
    fun onAuthenticationRequired()
}

class StreamingExtractionManager(
    private val tokenRepository: BearerTokenRepository,
    baseUrl: String = BackendBaseUrl,
    private val authenticationCallback: ExtractorAuthenticationCallback = ExtractorAuthenticationCallback {},
) : AutoCloseable {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val backendUri = normalizedBaseUrl.toUriOrNull()
        ?: throw IllegalArgumentException("Extractor base URL is invalid")
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    private val httpClient =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(45, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }

    suspend fun extractAudio(
        videoUrl: String,
        userPoToken: String? = null,
        cookies: String? = null,
        userGvsToken: String? = null,
    ): ExtractedAudio =
        withContext(Dispatchers.IO) {
            val normalizedVideoUrl = videoUrl.trim()
            requireHttpUrl(normalizedVideoUrl, "Video URL")
            val normalizedPoToken = userPoToken.normalizeBase64UrlPoToken("player")
            val normalizedGvsToken = userGvsToken.normalizeBase64UrlPoToken("GVS")
            val normalizedCookies = cookies?.trim()?.takeIf(String::isNotEmpty)

            val response =
                authorizedGet("$normalizedBaseUrl/api/extract") {
                    parameter("url", normalizedVideoUrl)
                    normalizedPoToken?.let { parameter("po_token", it) }
                    normalizedGvsToken?.let { parameter("gvs_token", it) }
                    normalizedCookies?.let { parameter("cookies", it) }
                }
            val raw = response.bodyAsText()
            validateHttpResponse(response, raw)

            decodeResponse<BackendExtractorResponse>(raw).toExtractedAudio()
        }

    suspend fun checkStreamStatus(
        streamId: String,
        sig: String,
        exp: Long,
    ): StreamStatusResponse =
        withContext(Dispatchers.IO) {
            val normalizedStreamId = streamId.trim()
            require(StreamIdPattern.matches(normalizedStreamId)) { "Stream ID is invalid" }
            val normalizedSignature = sig.trim()
            require(SignaturePattern.matches(normalizedSignature)) { "Stream signature is invalid" }
            require(exp > 0L) { "Stream expiry must be positive" }

            val response =
                authorizedGet("$normalizedBaseUrl/api/check-status/$normalizedStreamId") {
                    parameter("sig", normalizedSignature)
                    parameter("exp", exp)
                }
            val raw = response.bodyAsText()
            validateHttpResponse(response, raw)
            decodeResponse(raw)
        }

    override fun close() {
        httpClient.close()
    }

    private suspend fun authorizedGet(
        url: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val token = tokenRepository.getToken() ?: throw ExtractorAuthenticationException("Extractor token is missing")
        return try {
            httpClient.get(url) {
                header("Authorization", "Bearer $token")
                configure()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throw SonicVaultExtractorException("SonicVault Extractor request failed", throwable)
        }
    }

    private fun validateHttpResponse(
        response: HttpResponse,
        raw: String,
    ) {
        if (response.status.value in 200..299) return

        val serverMessage =
            runCatching { json.decodeFromString<BackendErrorResponse>(raw).error }
                .getOrNull()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        val message = serverMessage ?: "SonicVault Extractor returned HTTP ${response.status.value}"

        when (response.status) {
            HttpStatusCode.Unauthorized -> {
                tokenRepository.clearToken()
                val exception = ExtractorAuthenticationException(message)
                runCatching(authenticationCallback::onAuthenticationRequired)
                    .exceptionOrNull()
                    ?.let(exception::addSuppressed)
                throw exception
            }

            HttpStatusCode.Forbidden -> throw ExtractorForbiddenException(message)
            HttpStatusCode.Gone -> throw ExtractorStreamExpiredException(message)
            else -> throw ExtractorHttpException(response.status.value, message)
        }
    }

    private inline fun <reified T> decodeResponse(raw: String): T =
        try {
            json.decodeFromString<T>(raw)
        } catch (serialization: SerializationException) {
            throw SonicVaultExtractorException(
                "SonicVault Extractor returned invalid JSON",
                serialization,
            )
        }

    private fun BackendExtractorResponse.toExtractedAudio(): ExtractedAudio {
        val normalizedError = error?.trim()?.takeIf(String::isNotEmpty)
        if (!success || !valid) {
            throw SonicVaultExtractorException(normalizedError ?: "SonicVault Extractor returned an invalid stream")
        }
        val normalizedStreamUrl = streamUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: throw SonicVaultExtractorException("SonicVault Extractor returned no signed stream URL")
        val normalizedStreamPath = streamPath?.trim()?.takeIf(String::isNotEmpty)
            ?: throw SonicVaultExtractorException("SonicVault Extractor returned no signed stream path")
        val normalizedStreamExpiresAt = streamExpiresAt
            ?: throw SonicVaultExtractorException("SonicVault Extractor returned no stream expiry")
        val normalizedServerVersion = serverVersion?.trim()?.takeIf(String::isNotEmpty)
            ?: throw SonicVaultExtractorException("SonicVault Extractor returned no server version")
        val streamUri = normalizedStreamUrl.toUriOrNull()
            ?: throw SonicVaultExtractorException("SonicVault Extractor returned an invalid signed stream URL")
        if (streamUri.scheme?.lowercase() !in HttpSchemes ||
            !streamUri.isSameOriginAs(backendUri) ||
            streamUri.path?.startsWith(StreamPathPrefix) != true
        ) {
            throw SonicVaultExtractorException("SonicVault Extractor returned an untrusted signed stream URL")
        }
        val resolvedStreamPath = buildString {
            append(streamUri.rawPath)
            streamUri.rawQuery?.let { query -> append('?').append(query) }
        }
        if (normalizedStreamPath != resolvedStreamPath) {
            throw SonicVaultExtractorException("SonicVault Extractor returned inconsistent signed stream data")
        }
        val queryParameters = streamUri.rawQuery.orEmpty().split('&').mapNotNull { parameter ->
            val separator = parameter.indexOf('=')
            if (separator <= 0) null else parameter.substring(0, separator) to parameter.substring(separator + 1)
        }.toMap()
        val signature = queryParameters[SignatureQueryParameter]
        val expiry = queryParameters[ExpiryQueryParameter]?.toLongOrNull()
        if (signature == null ||
            !SignaturePattern.matches(signature) ||
            expiry == null ||
            expiry != normalizedStreamExpiresAt
        ) {
            throw SonicVaultExtractorException("SonicVault Extractor returned an incomplete signed stream URL")
        }

        return ExtractedAudio(
            success = success,
            valid = valid,
            cached = cached,
            serverVersion = normalizedServerVersion,
            title = title,
            thumbnail = thumbnail,
            streamUrl = normalizedStreamUrl,
            streamPath = normalizedStreamPath,
            streamExpiresAt = normalizedStreamExpiresAt,
            formatId = formatId,
            ext = ext,
            acodec = acodec,
            mimeType = mimeType,
            error = error,
        )
    }

    private fun requireHttpUrl(
        value: String,
        fieldName: String,
    ) {
        val uri = value.toUriOrNull()
        if (uri == null || uri.scheme?.lowercase() !in HttpSchemes || uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("$fieldName is invalid")
        }
    }

    private fun String?.normalizeBase64UrlPoToken(context: String): String? {
        val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val base64UrlValue =
            value
                .replace('+', '-')
                .replace('/', '_')
                .trimEnd('=')
        val paddingLength = (4 - base64UrlValue.length % 4) % 4
        val paddedValue = base64UrlValue + "=".repeat(paddingLength)
        val decoded =
            runCatching { Base64.getUrlDecoder().decode(paddedValue) }
                .getOrElse { cause ->
                    throw SonicVaultExtractorException(
                        "Invalid $context PO Token: expected a base64url-encoded value",
                        cause,
                    )
                }
        if (decoded.isEmpty()) {
            throw SonicVaultExtractorException(
                "Invalid $context PO Token: expected a non-empty base64url-encoded value",
            )
        }
        return Base64.getUrlEncoder().encodeToString(decoded)
    }

    private fun String.toUriOrNull(): URI? = runCatching { URI(this) }.getOrNull()

    private fun URI.isSameOriginAs(other: URI): Boolean =
        scheme.equals(other.scheme, ignoreCase = true) &&
            host.equals(other.host, ignoreCase = true) &&
            effectivePort() == other.effectivePort()

    private fun URI.effectivePort(): Int =
        when {
            port >= 0 -> port
            scheme.equals("https", ignoreCase = true) -> 443
            else -> 80
        }

    private companion object {
        const val BackendBaseUrl = "https://moriextractor.koyeb.app"
        const val StreamPathPrefix = "/api/play/"
        const val SignatureQueryParameter = "sig"
        const val ExpiryQueryParameter = "exp"
        val HttpSchemes = setOf("http", "https")
        val StreamIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")
        val SignaturePattern = Regex("^[A-Fa-f0-9]{32,256}$")
    }
}

open class SonicVaultExtractorException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class ExtractorAuthenticationException(
    message: String,
) : SonicVaultExtractorException(message)

class ExtractorForbiddenException(
    message: String,
) : SonicVaultExtractorException(message)

class ExtractorStreamExpiredException(
    message: String,
) : SonicVaultExtractorException(message)

class ExtractorHttpException(
    val statusCode: Int,
    message: String,
) : SonicVaultExtractorException(message)

@Serializable
private data class BackendErrorResponse(
    val error: String? = null,
)
