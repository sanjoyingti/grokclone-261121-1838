package com.arin.grokclone.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

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

fun buildChatRequest(
    model: String,
    messages: List<OutgoingMessage>,
    temperature: Float,
    maxTokens: Int,
    stream: Boolean
): String {
    val root = buildJsonObject {
        put("model", model.ifBlank { "gpt-4o-mini" })
        put(
            "messages",
            buildJsonArray {
                messages.forEach { m ->
                    add(
                        buildJsonObject {
                            put("role", m.role)
                            if (m.imageBase64.isNullOrBlank()) {
                                put("content", m.content)
                            } else {
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "text")
                                                put("text", m.content)
                                            }
                                        )
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put("url", "data:image/jpeg;base64,${m.imageBase64}")
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }
        )
        put("temperature", temperature)
        put("max_tokens", maxTokens)
        put("stream", stream)
    }
    return root.toString()
}

private fun contentToText(element: kotlinx.serialization.json.JsonElement?): String {
    return when (element) {
        null, is JsonNull -> ""
        is JsonPrimitive -> if (element.isString) element.content else element.content
        is JsonArray -> element.joinToString("") { part ->
            val obj = (part as? JsonObject) ?: return@joinToString ""
            when (obj["type"]?.let { (it as? JsonPrimitive)?.content }) {
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
        val message = (first["message"] as? JsonObject)
        if (message != null) {
            contentToText(message["content"])
        } else {
            contentToText(first["text"])
        }
    } catch (e: Exception) {
        ""
    }
}
