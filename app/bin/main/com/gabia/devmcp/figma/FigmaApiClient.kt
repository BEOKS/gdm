package com.gabia.devmcp.figma

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Figma API client using Ktor. Supports API Key (X-Figma-Token) and OAuth Bearer token.
 * Configure credentials via environment variables:
 * - FIGMA_API_KEY
 * - FIGMA_OAUTH_TOKEN
 */
class FigmaApiClient {
    private val baseUrl = "https://api.figma.com/v1"

    private val apiKey: String? = System.getenv("FIGMA_API_KEY")
    private val oauthToken: String? = System.getenv("FIGMA_OAUTH_TOKEN")

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
        engine {
            https {
                // Optionally relax or customize TLS if env vars are set
                buildTrustManager()?.let { this.trustManager = it }
            }
        }
        install(ContentNegotiation) { json(jsonConfig) }
        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            val (headerName, headerValue) = buildAuthHeader()
            if (headerName != null && headerValue != null) {
                header(headerName, headerValue)
            }
        }
    }

    private fun buildAuthHeader(): Pair<String?, String?> = when {
        !oauthToken.isNullOrBlank() -> HttpHeaders.Authorization to "Bearer $oauthToken"
        !apiKey.isNullOrBlank() -> "X-Figma-Token" to apiKey
        else -> null to null
    }

    private fun buildTrustManager(): X509TrustManager? {
        // 1) Prefer explicit corporate CA bundle if provided
        val pemPath = System.getenv("FIGMA_CA_CERT_PEM")?.takeIf { it.isNotBlank() }
        if (pemPath != null) {
            val path = Path.of(pemPath)
            if (Files.exists(path)) {
                val bytes = Files.readAllBytes(path)
                val cf = CertificateFactory.getInstance("X.509")
                val certs = try {
                    @Suppress("UNCHECKED_CAST")
                    cf.generateCertificates(ByteArrayInputStream(bytes)) as java.util.Collection<java.security.cert.Certificate>
                } catch (_: Exception) {
                    listOf(cf.generateCertificate(ByteArrayInputStream(bytes)))
                }
                val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                ks.load(null, null)
                var i = 0
                for (c in certs) {
                    ks.setCertificateEntry("figma-ca-$i", c)
                    i++
                }
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(ks)
                val tms = tmf.trustManagers
                return tms.filterIsInstance<X509TrustManager>().firstOrNull()
            }
        }

        // 2) Default to insecure unless explicitly disabled
        // Set FIGMA_SSL_INSECURE=false to use JVM default trust store instead.
        val insecureEnv = System.getenv("FIGMA_SSL_INSECURE")?.lowercase()
        if (insecureEnv == null || insecureEnv == "true") {
            return object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }
        }

        // 3) Explicitly secure: fall back to JVM default trust store
        return null
    }

    suspend fun getFile(fileKey: String, depth: Int?): JsonObject {
        val url = buildString {
            append("$baseUrl/files/")
            append(fileKey)
            if (depth != null) append("?depth=$depth")
        }
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Figma API error: ${response.status.value} ${response.status.description}\n${body ?: ""}")
        }
        return response.body()
    }

    suspend fun getNodes(fileKey: String, nodeIds: List<String>, depth: Int?): JsonObject {
        val urlBuilder = URLBuilder("$baseUrl/files/$fileKey/nodes")
        urlBuilder.parameters.append("ids", nodeIds.joinToString(","))
        if (depth != null) urlBuilder.parameters.append("depth", depth.toString())
        val response = httpClient.get(urlBuilder.build())
        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Figma API error: ${response.status.value} ${response.status.description}\n${body ?: ""}")
        }
        return response.body()
    }

    suspend fun getImageFills(fileKey: String): JsonObject {
        val url = "$baseUrl/files/$fileKey/images"
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Figma API error: ${response.status.value} ${response.status.description}\n${body ?: ""}")
        }
        return response.body()
    }

    suspend fun getImages(
        fileKey: String,
        nodeIds: List<String>,
        format: String,
        extraParams: Map<String, String> = emptyMap()
    ): JsonObject {
        val urlBuilder = URLBuilder("$baseUrl/images/$fileKey")
        urlBuilder.parameters.append("ids", nodeIds.joinToString(","))
        urlBuilder.parameters.append("format", format)
        extraParams.forEach { (k, v) -> urlBuilder.parameters.append(k, v) }
        val response = httpClient.get(urlBuilder.build())
        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Figma API error: ${response.status.value} ${response.status.description}\n${body ?: ""}")
        }
        return response.body()
    }

    suspend fun downloadToBytes(url: String): ByteArray {
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrNull()
            throw Exception("Failed to download image: ${response.status.value} ${response.status.description}\n${body ?: ""}")
        }
        return response.body()
    }

    fun close() {
        httpClient.close()
    }
}
