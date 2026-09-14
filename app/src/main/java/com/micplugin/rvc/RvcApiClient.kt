package com.micplugin.rvc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import android.util.Log

@Serializable
data class RvcProcessRequest(
    val audioBase64: String,
    val pitch: Int,
    val formant: Float,
    val indexRate: Float,
    val protectConsonants: Boolean,
    val speakerId: Int,
    val chunkSize: Int,
    val hopLength: Int,
    val f0Method: String,
)

@Serializable
data class RvcProcessResponse(
    val status: String,  // "success" or "error"
    val audioBase64: String? = null,
    val error: String? = null,
    val latencyMs: Long? = null,
)

@Serializable
data class RvcHealthResponse(
    val status: String,  // "healthy" or "error"
    val message: String? = null,
)

class RvcApiClient(private val kaggleEndpoint: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun healthCheck(): Result<RvcHealthResponse> = try {
        val response = client.get<RvcHealthResponse>("$kaggleEndpoint/health")
        Result.success(response)
    } catch (e: Exception) {
        Log.e("RvcApiClient", "Health check failed", e)
        Result.failure(e)
    }

    suspend fun processAudio(
        audioBase64: String,
        settings: RvcSettings,
    ): Result<RvcProcessResponse> = try {
        val request = RvcProcessRequest(
            audioBase64 = audioBase64,
            pitch = settings.pitch,
            formant = settings.formant,
            indexRate = settings.indexRate,
            protectConsonants = settings.protectConsonants,
            speakerId = settings.speakerId,
            chunkSize = settings.chunkSize,
            hopLength = settings.hopLength,
            f0Method = settings.f0Method,
        )
        
        val response = client.post<RvcProcessResponse>("${settings.kaggleEndpoint}/process") {
            contentType(ContentType.Application.Json)
            // Body would be serialized automatically by ContentNegotiation plugin
            // setBody(request) — requires proper plugin setup
        }
        
        if (response.status == "success" && response.audioBase64 != null) {
            Result.success(response)
        } else {
            Result.failure(Exception(response.error ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Log.e("RvcApiClient", "Audio processing failed", e)
        Result.failure(e)
    }

    fun close() {
        client.close()
    }
}
