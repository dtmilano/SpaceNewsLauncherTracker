package com.example.spacenewslaunchertracker.bedrock

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.InvokeModelRequest
import aws.smithy.kotlin.runtime.ServiceException
import com.example.spacenewslaunchertracker.data.BedrockSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Outcome of asking Bedrock to restate one mission description. */
sealed interface ExplainResult {
    data class Success(val text: String) : ExplainResult

    data class Failure(val message: String) : ExplainResult
}

/**
 * Rewrites a technical mission description into plain English via Amazon Bedrock.
 *
 * This is the Android counterpart of the boto3 `bedrock-runtime` client: the AWS SDK for
 * Kotlin exposes the same `InvokeModel` operation and the same Anthropic messages payload.
 * Credentials arrive from Settings on every call; a missing one is reported as a [Failure]
 * naming the field, never as a crash.
 */
class BedrockExplainer {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Clients own a connection pool and a credential set, so we keep exactly one and rebuild
     * it only when the user edits their Bedrock settings.
     */
    private var cachedClient: BedrockRuntimeClient? = null
    private var cachedFor: BedrockSettings? = null

    suspend fun explain(
        settings: BedrockSettings,
        missionName: String,
        description: String,
    ): ExplainResult {
        if (!settings.isComplete) {
            val missing = settings.missingFields.joinToString(", ")
            return ExplainResult.Failure(
                "Amazon Bedrock credentials are required for this feature. " +
                    "Open Settings → Amazon Bedrock and fill in: $missing.",
            )
        }
        if (description.isBlank()) {
            return ExplainResult.Failure("This mission has no description to rewrite yet.")
        }

        return try {
            val response = client(settings).invokeModel(
                InvokeModelRequest {
                    modelId = settings.modelId
                    contentType = "application/json"
                    accept = "application/json"
                    body = requestPayload(missionName, description).toByteArray()
                },
            )
            val text = firstTextBlock(response.body.decodeToString())
            if (text.isNullOrBlank()) {
                ExplainResult.Failure("Bedrock returned an empty response. Try again.")
            } else {
                ExplainResult.Success(text.trim())
            }
        } catch (serviceFailure: ServiceException) {
            ExplainResult.Failure(serviceMessage(serviceFailure, settings))
        } catch (failure: Exception) {
            ExplainResult.Failure(
                "Couldn't reach Bedrock: ${failure.message ?: failure::class.simpleName}",
            )
        }
    }

    private fun client(settings: BedrockSettings): BedrockRuntimeClient {
        cachedClient?.takeIf { cachedFor == settings }?.let { return it }
        cachedClient?.close()
        return BedrockRuntimeClient {
            region = settings.region
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = settings.accessKeyId
                secretAccessKey = settings.secretAccessKey
                sessionToken = settings.sessionToken.takeIf { it.isNotBlank() }
            }
        }.also {
            cachedClient = it
            cachedFor = settings
        }
    }

    /**
     * The Anthropic messages payload as `InvokeModel` expects it on Bedrock — identical to
     * the boto3 body, including the `anthropic_version` discriminator.
     */
    private fun requestPayload(missionName: String, description: String): String =
        buildJsonObject {
            put("anthropic_version", ANTHROPIC_BEDROCK_VERSION)
            put("max_tokens", 400)
            put("system", SYSTEM_PROMPT)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put(
                                "text",
                                "Mission: $missionName\n\nTechnical description:\n$description",
                            )
                        }
                    }
                }
            }
        }.toString()

    /** Pulls the assistant text out of the `content` block array. */
    private fun firstTextBlock(body: String): String? = runCatching {
        json.parseToJsonElement(body)
            .jsonObject["content"]
            ?.jsonArray
            ?.firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == "text" }
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
    }.getOrNull()

    private fun serviceMessage(cause: ServiceException, settings: BedrockSettings): String {
        val detail = cause.message ?: cause::class.simpleName.orEmpty()
        return when {
            detail.contains("expired", ignoreCase = true) ->
                "These Bedrock credentials have expired. Refresh them and paste the new ones " +
                    "into Settings."
            detail.contains("AccessDenied", ignoreCase = true) ||
                detail.contains("not authorized", ignoreCase = true) ->
                "These credentials aren't allowed to invoke ${settings.modelId} in " +
                    "${settings.region}."
            detail.contains("Throttling", ignoreCase = true) ->
                "Bedrock throttled the request. Wait a moment and try again."
            detail.contains("ValidationException", ignoreCase = true) ->
                "Bedrock rejected the request. Check that ${settings.modelId} is a model your " +
                    "account can invoke in ${settings.region}."
            else -> "Bedrock error: $detail"
        }
    }

    private companion object {
        const val ANTHROPIC_BEDROCK_VERSION = "bedrock-2023-05-31"
        const val SYSTEM_PROMPT =
            "You rewrite technical spaceflight mission descriptions for a curious general " +
                "audience. Reply with 2 to 3 short sentences of plain English and nothing " +
                "else: no preamble, no bullet points, no jargon, no acronyms left " +
                "unexplained. Use only facts present in the description the user gives you " +
                "— never add details, numbers, dates, or outcomes that are not there. If the " +
                "description is too sparse to explain, say so in one sentence."
    }
}
