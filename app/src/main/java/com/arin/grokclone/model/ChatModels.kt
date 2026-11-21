package com.arin.grokclone.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

val chatJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

data class OutgoingMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null
)

/**
 * Builds a JSON object from plain pairs. Doing it this way avoids depending on the
 * opt-in builder helpers (buildJsonObject / put / add), which differ between versions
 * of the serialization library and were breaking the compile.
 */
private fun jsonObjectOf(vararg pairs: Pair<String, JsonElement>): JsonObject {
    val map = LinkedHashMap<String, JsonElement>(pairs.size)
    for ((key, value) in pairs) {
        map[key] = value
    }
    return JsonObject(map)
}

fun buildChatRequest(
    model: String,
    messages: List<OutgoingMessage>,
    temperature: Float,
    maxTokens: Int,
    stream: Boolean
): String {
    val encodedMessages: List<JsonElement> = messages.map { message ->
        val role = JsonPrimitive(message.role)
        val image = message.imageBase64
        if (image.isNullOrBlank()) {
            jsonObjectOf(
                "role" to role,
                "content" to JsonPrimitive(message.content)
            )
        } else {
            val parts = JsonArray(
                listOf(
                    jsonObjectOf(
                        "type" to JsonPrimitive("text"),
                        "text" to JsonPrimitive(message.content)
                    ),
                    jsonObjectOf(
                        "type" to JsonPrimitive("image_url"),
                        "image_url" to jsonObjectOf(
                            "url" to JsonPrimitive("data:image/jpeg;base64,$image")
                        )
                    )
                )
            )
            jsonObjectOf(
                "role" to role,
                "content" to parts
            )
        }
    }

    return jsonObjectOf(
        "model" to JsonPrimitive(model.ifBlank { "gpt-4o-mini" }),
        "messages" to JsonArray(encodedMessages),
        "temperature" to JsonPrimitive(temperature),
        "max_tokens" to JsonPrimitive(maxTokens),
        "stream" to JsonPrimitive(stream)
    ).toString()
}

private fun contentToText(element: JsonElement?): String {
    return when (element) {
        null, is JsonNull -> ""
        is JsonPrimitive -> element.content
        is JsonArray -> element.joinToString("") { part ->
            val obj = (part as? JsonObject) ?: return@joinToString ""
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "text" -> (obj["text"] as? JsonPrimitive)?.content ?: ""
                "output_text" -> (obj["text"] as? JsonPrimitive)?.content ?: ""
                else -> ""
            }
        }
        else -> ""
    }
}

fun extractDeltaContent(payload: String): String {
    return try {
        val root = chatJson.parseToJsonElement(payload).jsonObject
        val choices = (root["choices"] as? JsonArray) ?: return ""
        val first = (choices.firstOrNull() as? JsonObject) ?: return ""
        val delta = (first["delta"] as? JsonObject) ?: return ""
        contentToText(delta["content"])
    } catch (e: Exception) {
        ""
    }
}

fun extractFullContent(payload: String): String {
    return try {
        val root = chatJson.parseToJsonElement(payload).jsonObject
        val choices = (root["choices"] as? JsonArray) ?: return ""
        val first = (choices.firstOrNull() as? JsonObject) ?: return ""
        val message = first["message"] as? JsonObject
        if (message != null) {
            contentToText(message["content"])
        } else {
            contentToText(first["text"])
        }
    } catch (e: Exception) {
        ""
    }
}
