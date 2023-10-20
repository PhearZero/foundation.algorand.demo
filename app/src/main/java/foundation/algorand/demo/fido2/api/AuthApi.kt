/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foundation.algorand.demo.fido2.api

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import com.google.android.gms.fido.fido2.api.common.Attachment
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAttestationResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorSelectionCriteria
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialCreationOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialParameters
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRpEntity
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialUserEntity
import foundation.algorand.demo.BuildConfig
import foundation.algorand.demo.fido2.decodeBase64
import foundation.algorand.demo.fido2.toBase64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import ru.gildor.coroutines.okhttp.await
import java.io.StringReader
import java.io.StringWriter
import javax.inject.Inject


/**
 * Interacts with the server API.
 */
class AuthApi @Inject constructor(
    private val client: OkHttpClient
) {

    companion object {
        private const val BASE_URL = BuildConfig.API_BASE_URL
        private val JSON = "application/json".toMediaTypeOrNull()
        private const val SessionIdKey = "connect.sid="
        private const val TAG = "fido2.AuthApi"
    }

    suspend fun connectResponse(requestId: Double, wallet: String, origin: String?): ApiResult<Unit> {
        val base = getOrigin(origin)
        val path = "$base/connect/response"
        Log.d(TAG, "Running: connectResponse($requestId, $wallet): POST $path")
        val call = client.newCall(
            Request.Builder()
                .url(path)
                .method("POST", jsonRequestBody {
                    name("requestId").value(requestId)
                    name("wallet").value(wallet)
                })
                .build()
        )

        val response = call.await()
        return response.result("Error: POST $path RequestID: $requestId" ) {
            Log.d(TAG, "Successful: /connect/response($requestId): POST $path")
            body ?: throw ApiException("Empty response from $path")
        }
    }

    /**
     * Create a Session with the FIDO2 API Service
     *
     * Currently just represents a public key the application would like to use.
     * Eventually this should include a challenge to attest against on the server
     *
     * @param wallet The wallet to be used for sign-in.
     * @return The Session ID.
     */
    suspend fun createSession(wallet: String, origin: String?): ApiResult<Unit> {
        val base = getOrigin(origin)
        val path = "$base/auth/session"
        Log.d(TAG, "Running: createSession($wallet): POST $path")
        val call = client.newCall(
            Request.Builder()
                .url(path)
                .method("POST", jsonRequestBody {
                    name("wallet").value(wallet)
                })
                .build()
        )
        val response = call.await()
        return response.result("Error: POST $path") {
            Log.d(TAG, "Successful: createSession($wallet): POST $path")
        }
    }

    /**
     * @param sessionId The session ID.
     * @return A list of all the credentials registered on the server.
     */
    suspend fun getKeys(sessionId: String, origin: String): ApiResult<List<Credential>> {
        val base = getOrigin(origin)
        val path = "$base/auth/keys"
        Log.d(TAG, "Running: getKeys($sessionId): GET $path")
        val call = client.newCall(
            Request.Builder()
                .url(path)
                .addHeader("Cookie", formatCookie(sessionId))
                .build()
        )
        val response = call.await()
        return response.result("Error: GET $path") {
            Log.d(TAG, "Successful: getKeys($sessionId): GET $path")
            parseUserCredentials(body ?: throw ApiException("Empty response from /getKeys"))
        }
    }

    /**
     * @param sessionId The session ID.
     * @return A pair. The `first` element is an [PublicKeyCredentialCreationOptions] that can be
     * used for a subsequent FIDO2 API call. The `second` element is a challenge string that should
     * be sent back to the server in [attestationResponse].
     */
    suspend fun attestationRequest(sessionId: String, origin: String?): ApiResult<PublicKeyCredentialCreationOptions> {
        val base = getOrigin(origin)
        val path = "$base/attestation/request"
        val call = client.newCall(
            Request.Builder()
                .url(path)
                .addHeader("Cookie", formatCookie(sessionId))
                .method("POST", jsonRequestBody {
                    name("attestation").value("none")
                    name("authenticatorSelection").objectValue {
                        name("authenticatorAttachment").value("platform")
                        name("userVerification").value("required")
                    }
                })
                .build()
        )
        val response = call.await()
        return response.result("Error calling POST $path") {
            parsePublicKeyCredentialCreationOptions(
                body ?: throw ApiException("Empty response from POST $path")
            )
        }
    }

    /**
     * @param sessionId The session ID to be used for the sign-in.
     * @param credential The PublicKeyCredential object.
     * @return A list of all the credentials registered on the server, including the newly
     * registered one.
     */
    suspend fun attestationResponse(
        sessionId: String,
        credential: PublicKeyCredential,
        origin: String,
    ): ApiResult<List<Credential>> {
        val base = getOrigin(origin)
        val path = "$base/attestation/response"
        Log.d(TAG, "attestationResponse($sessionId, $credential, $origin)")

        val rawId = credential.rawId.toBase64()
        val response = credential.response as AuthenticatorAttestationResponse

        val call = client.newCall(
            Request.Builder()
                .url(path)
                .addHeader("Cookie", formatCookie(sessionId))
                .method("POST", jsonRequestBody {
                    name("id").value(rawId)
                    name("type").value(PublicKeyCredentialType.PUBLIC_KEY.toString())
                    name("rawId").value(rawId)
                    name("response").objectValue {
                        name("clientDataJSON").value(
                            response.clientDataJSON.toBase64()
                        )
                        name("attestationObject").value(
                            response.attestationObject.toBase64()
                        )
                    }
                })
                .build()
        )
        val apiResponse = call.await()
        return apiResponse.result("Error calling /attestation/response") {
            parseUserCredentials(
                body ?: throw ApiException("Empty response from /attestation/response")
            )
        }
    }

    /**
     * @param sessionId The session ID.
     * @param credentialId The credential ID to be removed.
     */
    suspend fun deleteKey(sessionId: String, credentialId: String): ApiResult<Unit> {
        Log.d(TAG, "Running: deleteKey() DELETE /auth/keys")
        val call = client.newCall(
            Request.Builder()
                .url("$BASE_URL/auth/keys/$credentialId")
                .addHeader("Cookie", formatCookie(sessionId))
                .method("DELETE", jsonRequestBody {})
                .build()
        )
        val response = call.await()
        return response.result("Error calling DELETE /auth/keys") {
            Log.d(TAG, "Successful /auth/keys with DELETE")
        }
    }

    /**
     * @param sessionId The session ID to be used for the sign-in.
     * @param credentialId The credential ID of this device.
     * @return A pair. The `first` element is a [PublicKeyCredentialRequestOptions] that can be used
     * for a subsequent FIDO2 API call. The `second` element is a challenge string that should
     * be sent back to the server in [assertionResponse].
     */
    suspend fun assertionRequest(
        sessionId: String?,
        credentialId: String
    ): ApiResult<PublicKeyCredentialRequestOptions> {
        val path = "$BASE_URL/assertion/request/$credentialId"
        Log.d(TAG, "Running: assertionRequest($sessionId, $credentialId) POST $path" )

        val requestBuilder = Request.Builder()
            .url(
                buildString {
                    append(path)
                }
            )
            .method("POST", jsonRequestBody {})

        if (sessionId != null) {
            Log.d(TAG, "Running session")
            requestBuilder.addHeader("Cookie", formatCookie(sessionId))
        } else {
            Log.w(TAG, "No session found")
        }

        val call = client.newCall(
            requestBuilder.build()
        )
        val response = call.await()
        return response.result("Failed to fetch /assertion/request") {
            parsePublicKeyCredentialRequestOptions(
                body ?: throw ApiException("Empty response")
            )
        }
    }

    /**
     * @param sessionId The session ID to be used for the sign-in.
     * @param credential The PublicKeyCredential object.
     * @return A list of all the credentials registered on the server, including the newly
     * registered one.
     */
    suspend fun assertionResponse(
        sessionId: String,
        credential: PublicKeyCredential
    ): ApiResult<List<Credential>> {
        val rawId = credential.rawId.toBase64()
        val response = credential.response as AuthenticatorAssertionResponse
        val builder = Request.Builder()
            .url("$BASE_URL/assertion/response")
            .addHeader("Cookie", formatCookie(sessionId))
            .method("POST", jsonRequestBody {
                name("id").value(rawId)
                name("type").value(PublicKeyCredentialType.PUBLIC_KEY.toString())
                name("rawId").value(rawId)
                name("response").objectValue {
                    name("clientDataJSON").value(
                        response.clientDataJSON.toBase64()
                    )
                    name("authenticatorData").value(
                        response.authenticatorData.toBase64()
                    )
                    name("signature").value(
                        response.signature.toBase64()
                    )
                    name("userHandle").value(
                        response.userHandle?.toBase64() ?: ""
                    )
                }
            })

        val call = client.newCall(
            builder.build()
        )
        val apiResponse = call.await()
        return apiResponse.result("Error calling /assertion/response") {
            parseUserCredentials(body ?: throw ApiException("Empty response from /signinResponse"))
        }
    }

    private fun parsePublicKeyCredentialRequestOptions(
        body: ResponseBody
    ): PublicKeyCredentialRequestOptions {
        val builder = PublicKeyCredentialRequestOptions.Builder()
        JsonReader(body.byteStream().bufferedReader()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "challenge" -> builder.setChallenge(reader.nextString().decodeBase64())
                    "userVerification" -> reader.skipValue()
                    "allowCredentials" -> builder.setAllowList(parseCredentialDescriptors(reader))
                    "rpId" -> builder.setRpId(reader.nextString())
                    "timeout" -> builder.setTimeoutSeconds(reader.nextDouble())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return builder.build()
    }

    private fun parsePublicKeyCredentialCreationOptions(
        body: ResponseBody
    ): PublicKeyCredentialCreationOptions {
        val builder = PublicKeyCredentialCreationOptions.Builder()
        JsonReader(body.byteStream().bufferedReader()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "user" -> builder.setUser(parseUser(reader))
                    "challenge" -> builder.setChallenge(reader.nextString().decodeBase64())
                    "pubKeyCredParams" -> builder.setParameters(parseParameters(reader))
                    "timeout" -> builder.setTimeoutSeconds(reader.nextDouble())
                    "attestation" -> reader.skipValue() // Unused
                    "excludeCredentials" -> builder.setExcludeList(
                        parseCredentialDescriptors(reader)
                    )

                    "authenticatorSelection" -> builder.setAuthenticatorSelection(
                        parseSelection(reader)
                    )

                    "rp" -> builder.setRp(parseRp(reader))
                    "extensions" -> reader.skipValue() // Unused
                }
            }
            reader.endObject()
        }
        return builder.build()
    }

    private fun parseRp(reader: JsonReader): PublicKeyCredentialRpEntity {
        var id: String? = null
        var name: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "name" -> name = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return PublicKeyCredentialRpEntity(id!!, name!!, /* icon */ null)
    }

    private fun parseSelection(reader: JsonReader): AuthenticatorSelectionCriteria {
        val builder = AuthenticatorSelectionCriteria.Builder()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "authenticatorAttachment" -> builder.setAttachment(
                    Attachment.fromString(reader.nextString())
                )

                "userVerification" -> reader.skipValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return builder.build()
    }

    private fun parseCredentialDescriptors(
        reader: JsonReader
    ): List<PublicKeyCredentialDescriptor> {
        val list = mutableListOf<PublicKeyCredentialDescriptor>()
        reader.beginArray()
        while (reader.hasNext()) {
            var id: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextString()
                    "type" -> reader.skipValue()
                    "transports" -> reader.skipValue()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                PublicKeyCredentialDescriptor(
                    PublicKeyCredentialType.PUBLIC_KEY.toString(),
                    id!!.decodeBase64(),
                    /* transports */ null
                )
            )
        }
        reader.endArray()
        return list
    }

    private fun parseUser(reader: JsonReader): PublicKeyCredentialUserEntity {
        reader.beginObject()
        var id: String? = null
        var name: String? = null
        var displayName = ""
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "name" -> name = reader.nextString()
                "displayName" -> displayName = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return PublicKeyCredentialUserEntity(
            id!!.decodeBase64(),
            name!!,
            "", // icon
            displayName
        )
    }

    private fun parseParameters(reader: JsonReader): List<PublicKeyCredentialParameters> {
        val parameters = mutableListOf<PublicKeyCredentialParameters>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var type: String? = null
            var alg = 0
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> type = reader.nextString()
                    "alg" -> alg = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            parameters.add(PublicKeyCredentialParameters(type!!, alg))
        }
        reader.endArray()
        return parameters
    }

    private fun jsonRequestBody(body: JsonWriter.() -> Unit): RequestBody {
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.beginObject()
            writer.body()
            writer.endObject()
        }
        return output.toString().toRequestBody(JSON)
    }

    private fun parseUserCredentials(body: ResponseBody): List<Credential> {
        fun readCredentials(reader: JsonReader): List<Credential> {
            val credentials = mutableListOf<Credential>()
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                var id: String? = null
                var publicKey: String? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "credId" -> id = reader.nextString()
                        "publicKey" -> publicKey = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id != null && publicKey != null) {
                    credentials.add(Credential(id, publicKey))
                }
            }
            reader.endArray()
            return credentials
        }
        JsonReader(body.byteStream().bufferedReader()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == "credentials") {
                    return readCredentials(reader)
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        throw ApiException("Cannot parse credentials")
    }

    private fun throwResponseError(response: Response, message: String): Nothing {
        val b = response.body
        if (b != null) {
            throw ApiException("$message; ${parseError(b)}")
        } else {
            throw ApiException(message)
        }
    }

    private fun parseError(body: ResponseBody): String {
        val errorString = body.string()
        Log.d(TAG, errorString)
        try {
            JsonReader(StringReader(errorString)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name == "error") {
                        val token = reader.peek()
                        if (token == JsonToken.STRING) {
                            return reader.nextString()
                        }
                        return "Unknown"
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot parse the error: $errorString", e)
            // Don't throw; this method is called during throwing.
        }
        return ""
    }

    private fun JsonWriter.objectValue(body: JsonWriter.() -> Unit) {
        beginObject()
        body()
        endObject()
    }

    private fun <T> Response.result(errorMessage: String, data: Response.() -> T): ApiResult<T> {
        if (!isSuccessful) {
            if (code == 401) { // Unauthorized
                return ApiResult.SignedOutFromServer
            }
            // All other errors throw an exception.
            throwResponseError(this, errorMessage)
        }
        val cookie = headers("set-cookie").find { it.startsWith(SessionIdKey) }
        val sessionId = if (cookie != null) parseSessionId(cookie) else null
        return ApiResult.Success(sessionId, data())
    }

    private fun parseSessionId(cookie: String): String {
        val start = cookie.indexOf(SessionIdKey)
        if (start < 0) {
            throw ApiException("Cannot find $SessionIdKey")
        }
        val semicolon = cookie.indexOf(";", start + SessionIdKey.length)
        val end = if (semicolon < 0) cookie.length else semicolon
        return cookie.substring(start + SessionIdKey.length, end)
    }

    private fun formatCookie(sessionId: String): String {
        return "$SessionIdKey$sessionId"
    }
    private fun getOrigin(origin: String?): String {
        return if(origin === null) BASE_URL else origin
    }
}
