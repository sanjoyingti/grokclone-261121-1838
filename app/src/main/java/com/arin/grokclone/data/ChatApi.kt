package com.arin.grokclone.data

import com.arin.grokclone.model.extractDeltaContent
import com.arin.grokclone.model.extractFullContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class ApiException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)

class ChatApi(private val client: OkHttpClient) {

    suspend fun complete(
        baseUrl: String,
        apiKey: String,
        requestJson: String,
        stream: Boolean,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val url = buildEndpoint(baseUrl)
        val body = requestJson.toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        val call = client.newCall(builder.build())
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }

        try {
            val response: Response = call.execute()
            response.use { open ->
                if (!open.isSuccessful) {
                    val raw = safeBodyString(open)
                    if (call.isCanceled()) {
                        throw CancellationException("Stopped by the user")
                    }
                    throw ApiException(describeStatus(open.code, raw), open.code)
                }
                if (stream) readStream(open, onDelta) else readPlain(open)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (call.isCanceled()) {
                throw CancellationException("Stopped by the user")
            }
            throw e
        } catch (e: IOException) {
            if (call.isCanceled()) {
                throw CancellationException("Stopped by the user")
            }
            throw mapIoException(e)
        } finally {
            cancelHandle?.dispose()
        }
    }

    /**
     * The reply body type differs between the two major versions of the HTTP library
     * (nullable in 4.x, non-null in 5.x). Returning it into a nullable slot keeps this
     * file compiling and behaving identically with either of them.
     */
    private fun bodyOrNull(response: Response): ResponseBody? = response.body

    private fun safeBodyString(response: Response): String {
        val body = bodyOrNull(response) ?: return ""
        return try {
            body.string()
        } catch (e: IOException) {
            ""
        }
    }

    private suspend fun readStream(response: Response, onDelta: (String) -> Unit): String {
        val body = bodyOrNull(response)
            ?: throw ApiException("The server returned an empty response.")
        val source = body.source()
        val collected = StringBuilder()
        while (true) {
            coroutineContext.ensureActive()
            val line = try {
                source.readUtf8Line()
            } catch (e: IOException) {
                throw mapIoException(e)
            } ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith(":")) continue
            val payload = when {
                trimmed.startsWith("data:") -> trimmed.removePrefix("data:").trim()
                trimmed.startsWith("{") -> trimmed
                else -> continue
            }
            if (payload == "[DONE]") break
            val delta = extractDeltaContent(payload)
            if (delta.isNotEmpty()) {
                collected.append(delta)
                onDelta(delta)
            }
        }
        return collected.toString()
    }

    private fun readPlain(response: Response): String {
        val body = bodyOrNull(response)
            ?: throw ApiException("The server returned an empty response.")
        val raw = try {
            body.string()
        } catch (e: IOException) {
            throw mapIoException(e)
        }
        if (raw.isBlank()) throw ApiException("The server returned an empty response.")
        val content = extractFullContent(raw)
        if (content.isBlank()) {
            throw ApiException("Could not read the reply. Response started with: ${raw.take(140)}")
        }
        return content
    }

    private fun buildEndpoint(baseUrl: String): String {
        var base = baseUrl.trim()
        if (base.isEmpty()) base = AppSettings.DEFAULT_BASE_URL
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://$base"
        }
        base = base.trimEnd('/')
        if (base.endsWith("/chat/completions")) return base
        return "$base/chat/completions"
    }

    private fun describeStatus(code: Int, raw: String): String {
        val serverMessage = extractErrorMessage(raw)
        val detail = if (serverMessage.isBlank()) "" else " Server said: $serverMessage"
        return when (code) {
            400 -> "400 Bad Request — the server rejected the request.$detail"
            401 -> "401 Unauthorized — your API key is missing or wrong. Open Settings.$detail"
            403 -> "403 Forbidden — the server refused this key or model.$detail"
            404 -> "404 Not Found — check the Base URL path and the model name.$detail"
            408 -> "408 Request Timeout — the server gave up waiting.$detail"
            413 -> "413 Payload Too Large — the conversation or image is too big.$detail"
            422 -> "422 Unprocessable — the server did not understand the request.$detail"
            429 -> "429 Too Many Requests — you are rate limited. Try again shortly.$detail"
            in 500..599 -> "$code Server error — the AI backend is having trouble.$detail"
            else -> "$code — ${if (serverMessage.isBlank()) "request failed" else serverMessage}"
        }
    }

    private fun extractErrorMessage(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val root = com.arin.grokclone.model.chatJson
                .parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
            val error = root?.get("error")
            when (error) {
                is kotlinx.serialization.json.JsonObject ->
                    (error["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                is kotlinx.serialization.json.JsonPrimitive -> error.content
                else -> raw.take(140)
            }
        } catch (e: Exception) {
            raw.take(140)
        }
    }

    private fun mapIoException(e: IOException): ApiException = when (e) {
        is SocketTimeoutException ->
            ApiException("Timeout — the server took too long to answer. Check the Base URL.")
        is UnknownHostException ->
            ApiException("Network error — that server name could not be found. Check the Base URL.")
        is ConnectException ->
            ApiException("Network error — could not connect to the server. Is it running?")
        else ->
            ApiException("Network error — ${e.message ?: "the request failed"}")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
