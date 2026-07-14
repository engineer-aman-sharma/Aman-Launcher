package com.example.amanslauncher.downloader

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses Get Info media JSON only.
 *
 * Expected fields: error, hosting, shortcode, caption, audio, type, download_url, thumb.
 * Does not accept User Info / profile payloads (username, followers, etc.).
 */
object InstagramMediaResponseParser {

    fun parse(body: String): InstagramMediaResult {
        val normalized = stripBom(body).trim()
        if (normalized.isEmpty()) {
            throw MediaApiException("Empty response from server")
        }

        val root = parseRootObject(normalized)
        rejectProfileResponse(root)
        assertNoApiError(root)

        val mediaUrl = stringFieldIgnoreCase(root, KEY_DOWNLOAD_URL)
        if (mediaUrl == null) {
            val apiError = extractErrorMessage(root)
            if (!apiError.isNullOrBlank()) {
                throw MediaApiException(apiError)
            }
            throw MediaApiException("No download_url in API response")
        }

        return InstagramMediaResult(
            downloadUrl = mediaUrl,
            thumb = stringFieldIgnoreCase(root, "thumb"),
            type = stringFieldIgnoreCase(root, "type"),
            caption = stringFieldIgnoreCase(root, "caption"),
            shortcode = stringFieldIgnoreCase(root, "shortcode"),
            hosting = stringFieldIgnoreCase(root, "hosting"),
            audio = stringFieldIgnoreCase(root, "audio"),
        )
    }

    fun parseErrorMessage(body: String): String? {
        val normalized = stripBom(body).trim()
        if (normalized.isEmpty()) return null
        return runCatching {
            extractErrorMessage(parseRootObject(normalized))
                ?: normalized.take(500)
        }.getOrElse {
            normalized.take(500)
        }
    }

    private fun parseRootObject(body: String): JSONObject {
        return when {
            body.startsWith("{") -> JSONObject(body)
            body.startsWith("[") -> {
                val array = JSONArray(body)
                if (array.length() == 0) {
                    throw MediaApiException("Empty response array")
                }
                array.optJSONObject(0)
                    ?: throw MediaApiException("Unexpected response array format")
            }
            else -> throw MediaApiException("Unexpected response format")
        }
    }

    private fun rejectProfileResponse(root: JSONObject) {
        val looksLikeProfile =
            hasIgnoreCase(root, "username") ||
                hasIgnoreCase(root, "followers") ||
                hasIgnoreCase(root, "following") ||
                hasIgnoreCase(root, "profile_picture")
        val hasMediaUrl = !stringFieldIgnoreCase(root, KEY_DOWNLOAD_URL).isNullOrBlank()
        if (looksLikeProfile && !hasMediaUrl) {
            throw MediaApiException(
                "Received User Info profile response instead of Get Info media. " +
                    "Use the get-info endpoint with the post/Reel URL.",
            )
        }
    }

    private fun assertNoApiError(root: JSONObject) {
        if (!hasIgnoreCase(root, "error") || isNullIgnoreCase(root, "error")) return

        when (val errorValue = getIgnoreCase(root, "error")) {
            is Boolean -> if (errorValue) {
                throw MediaApiException(
                    extractErrorMessage(root)
                        ?: "The API reported an error for this URL",
                )
            }
            is String -> if (
                errorValue.isNotBlank() &&
                !errorValue.equals("false", ignoreCase = true) &&
                !errorValue.equals("null", ignoreCase = true)
            ) {
                throw MediaApiException(errorValue)
            }
        }
    }

    private fun extractErrorMessage(root: JSONObject): String? {
        return sequenceOf("message", "error_message", "detail", "error")
            .mapNotNull { key ->
                stringFieldIgnoreCase(root, key)?.takeIf { value ->
                    !value.equals("true", ignoreCase = true) &&
                        !value.equals("false", ignoreCase = true)
                }
            }
            .firstOrNull()
    }

    private fun stripBom(value: String): String =
        if (value.isNotEmpty() && value[0] == '\uFEFF') value.substring(1) else value

    private const val KEY_DOWNLOAD_URL = "download_url"
}

internal fun stringFieldIgnoreCase(json: JSONObject, key: String): String? {
    if (!hasIgnoreCase(json, key) || isNullIgnoreCase(json, key)) return null
    val raw = when (val value = getIgnoreCase(json, key)) {
        is String -> value
        null -> return null
        else -> value.toString()
    }.trim()
    return raw.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun hasIgnoreCase(json: JSONObject, key: String): Boolean {
    if (json.has(key)) return true
    val keys = json.keys()
    while (keys.hasNext()) {
        if (keys.next().equals(key, ignoreCase = true)) return true
    }
    return false
}

private fun isNullIgnoreCase(json: JSONObject, key: String): Boolean {
    if (json.has(key)) return json.isNull(key)
    val keys = json.keys()
    while (keys.hasNext()) {
        val actual = keys.next()
        if (actual.equals(key, ignoreCase = true)) return json.isNull(actual)
    }
    return true
}

private fun getIgnoreCase(json: JSONObject, key: String): Any? {
    if (json.has(key)) return json.get(key)
    val keys = json.keys()
    while (keys.hasNext()) {
        val actual = keys.next()
        if (actual.equals(key, ignoreCase = true)) return json.get(actual)
    }
    return null
}
