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

package foundation.algorand.demo.fido2.repository

import android.app.PendingIntent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.algorand.algosdk.account.Account
import foundation.algorand.demo.fido2.api.ApiException
import foundation.algorand.demo.fido2.api.ApiResult
import foundation.algorand.demo.fido2.api.AuthApi
import foundation.algorand.demo.fido2.api.Credential
import foundation.algorand.demo.fido2.toBase64
import com.google.android.gms.fido.fido2.Fido2ApiClient
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works with the API, the local data store, and FIDO2 API.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) {

    private companion object {
        const val TAG = "AuthRepository"

        // Keys for SharedPreferences
        val ALGORAND_PUBLIC_KEY = stringPreferencesKey("public_key")
        val ALGORAND_PRIVATE_KEY = stringPreferencesKey("private_key")
        val USERNAME = stringPreferencesKey("username")
        val SESSION_ID = stringPreferencesKey("session_id")
        val CREDENTIALS = stringSetPreferencesKey("credentials")
        val LOCAL_CREDENTIAL_ID = stringPreferencesKey("local_credential_id")

        suspend fun <T> DataStore<Preferences>.read(key: Preferences.Key<T>): T? {
            return data.map { it[key] }.first()
        }
    }

    var account: Account? = null
    private var fido2ApiClient: Fido2ApiClient? = null

    fun setFido2APiClient(client: Fido2ApiClient?) {
        fido2ApiClient = client
    }

    private val signInStateMutable = MutableSharedFlow<SignInState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** The current [SignInState]. */
    val signInState = signInStateMutable.asSharedFlow()

    /**
     * The list of credentials this user has registered on the server. This is only populated when
     * the sign-in state is [SignInState.SignedIn].
     */
    val credentials =
        dataStore.data.map { it[CREDENTIALS] ?: emptySet() }.map { parseCredentials(it) }
    init {
        scope.launch {
            val username: String?
            val prvKey = dataStore.read(ALGORAND_PRIVATE_KEY)
            // No Wallet Found
            if (prvKey === null) {
                // TODO: go back to random generated and dispense with algokit api
                val acc = Account("industry kangaroo visa history swarm exotic doctor fade strike honey ride bicycle pistol large eager solution midnight loan give list company behave purpose abstract good")
                // WARNING: This is not safe for production!
                dataStore.edit { prefs ->
                    prefs[USERNAME] = acc.address.toString()
                    prefs[ALGORAND_PUBLIC_KEY] = acc.address.toString()
                    prefs[ALGORAND_PRIVATE_KEY] = acc.toMnemonic()
                }
                Log.d(TAG,  acc.toMnemonic())
                Log.d(TAG,  acc.address.toString())
                account = acc
                username = acc.address.toString()
                createSession(username)
            }
            // Create new Wallet
            else {
                val acc = Account(prvKey)
                dataStore.edit { prefs ->
                    prefs[USERNAME] = acc.address.toString()
                }
                account = acc
                Log.d(TAG,  acc.toMnemonic())
                Log.d(TAG,  acc.address.toString())
                username = acc.address.toString()
                createSession(username)
            }
            val initialState = SignInState.SignedIn(username)
            signInStateMutable.emit(initialState)
            refreshCredentials()
        }
    }
    suspend fun connectResponse(requestId: String){
        when (api.connectResponse(requestId)){
            is ApiResult.Success -> {
                Log.d(TAG, "HELLO MOTO")
            }

            else -> {
                Log.d(TAG, "ERROR")
            }
        }
    }
    /**
     * Sends the username to the server. If it succeeds, the sign-in state will proceed to
     * [SignInState.SigningIn].
     */
    suspend fun createSession(wallet: String) {
        when (val result = api.createSession(wallet)) {
            ApiResult.SignedOutFromServer -> forceSignOut()
            is ApiResult.Success -> {
                dataStore.edit { prefs ->
                    prefs[USERNAME] = wallet
                    prefs[SESSION_ID] = result.sessionId!!
                }
                signInStateMutable.emit(SignInState.SignedIn(wallet))
                refreshCredentials()
            }
        }
    }

    private suspend fun refreshCredentials() {
        var sessionId = dataStore.read(SESSION_ID)
        val username = dataStore.read(USERNAME)
        if (username != null) {
            if (sessionId === null) {
                createSession(username)
                sessionId = dataStore.read(SESSION_ID)
            }
            if (sessionId != null) {
                when (val result = api.getKeys(sessionId)) {
                    ApiResult.SignedOutFromServer -> forceSignOut()
                    is ApiResult.Success -> {
                        dataStore.edit { prefs ->
                            result.sessionId?.let { prefs[SESSION_ID] = it }
                            prefs[CREDENTIALS] = result.data.toStringSet()
                        }
                    }
                }
            }
        }
    }

    private fun List<Credential>.toStringSet(): Set<String> {
        return mapIndexed { index, credential ->
            "$index;${credential.id};${credential.publicKey}"
        }.toSet()
    }

    private fun parseCredentials(set: Set<String>): List<Credential> {
        return set.map { s ->
            val (index, id, publicKey) = s.split(";")
            index to Credential(id, publicKey)
        }.sortedBy { (index, _) -> index }
            .map { (_, credential) -> credential }
    }

    /**
     * Clears the credentials.
     */
    suspend fun clearCredentials() {
        val username = dataStore.read(USERNAME)!!
        dataStore.edit { prefs ->
            prefs.remove(CREDENTIALS)
        }
    }

    /**
     * Clears all the sign-in information. The sign-in state will proceed to
     * [SignInState.SignedOut].
     */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(USERNAME)
            prefs.remove(SESSION_ID)
            prefs.remove(CREDENTIALS)
        }
        signInStateMutable.emit(SignInState.SignedOut)
    }

    private suspend fun forceSignOut() {
        Log.d(TAG, "Forcing Signout")
        dataStore.edit { prefs ->
            prefs.remove(USERNAME)
            prefs.remove(SESSION_ID)
            prefs.remove(CREDENTIALS)
        }
        signInStateMutable.emit(SignInState.SignInError("Signed out by server"))
    }

    /**
     * Starts to register a new credential to the server.
     */
    suspend fun attestationRequest(): PendingIntent? {
        Log.d(TAG, "Requesting for PublicKeyCredentialCreationOptions")
        fido2ApiClient?.let { client ->
            try {
                val sessionId = dataStore.read(SESSION_ID)!!
                Log.d(TAG, sessionId)
                when (val apiResult = api.attestationRequest(sessionId)) {
                    ApiResult.SignedOutFromServer -> forceSignOut()
                    is ApiResult.Success -> {
                        Log.d(TAG, "Received Options")
                        if (apiResult.sessionId != null) {
                            Log.d(TAG, apiResult.sessionId)
                            dataStore.edit { prefs ->
                                prefs[SESSION_ID] = apiResult.sessionId
                            }
                        }
                        val task = client.getRegisterPendingIntent(apiResult.data)
                        return task.await()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot call registerRequest", e)
            }
        }
        return null
    }

    /**
     * Finishes registering a new credential to the server. This should only be called after
     * a call to [attestationRequest] and a local FIDO2 API for public key generation.
     */
    suspend fun attestationResponse(credential: PublicKeyCredential) {
        try {
            val sessionId = dataStore.read(SESSION_ID)!!
            val credentialId = credential.rawId.toBase64()
            when (val result = api.attestationResponse(sessionId, credential)) {
                ApiResult.SignedOutFromServer -> forceSignOut()
                is ApiResult.Success -> {
                    dataStore.edit { prefs ->
                        result.sessionId?.let { prefs[SESSION_ID] = it }
                        prefs[CREDENTIALS] = result.data.toStringSet()
                        prefs[LOCAL_CREDENTIAL_ID] = credentialId
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Cannot call registerResponse", e)
        }
    }

    /**
     * Removes a credential registered on the server.
     */
    suspend fun deleteKey(credentialId: String) {
        try {
            val sessionId = dataStore.read(SESSION_ID)!!
            when (api.deleteKey(sessionId, credentialId)) {
                ApiResult.SignedOutFromServer -> forceSignOut()
                is ApiResult.Success -> refreshCredentials()
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Cannot call removeKey", e)
        }
    }

    /**
     * Starts to sign in with a FIDO2 credential. This should only be called when the sign-in state
     * is [SignInState.SigningIn].
     */
    suspend fun assertionRequest(): PendingIntent? {
        fido2ApiClient?.let { client ->
            val sessionId = dataStore.read(SESSION_ID)
            val credentialId = dataStore.read(LOCAL_CREDENTIAL_ID)
            if (credentialId != null) {
                when (val apiResult = api.assertionRequest(sessionId, credentialId)) {
                    ApiResult.SignedOutFromServer -> Log.d(TAG, "Shutdown")
                    is ApiResult.Success -> {
                        val task = client.getSignPendingIntent(apiResult.data)
                        return task.await()
                    }
                }
            }
        }
        return null
    }

    /**
     * Finishes to signing in with a FIDO2 credential. This should only be called after a call to
     * [assertionRequest] and a local FIDO2 API for key assertion.
     */
    suspend fun assertionResponse(credential: PublicKeyCredential) {
        try {
            val sessionId = dataStore.read(SESSION_ID)!!
            val credentialId = credential.rawId.toBase64()
            when (val result = api.assertionResponse(sessionId, credential)) {
                ApiResult.SignedOutFromServer -> forceSignOut()
                is ApiResult.Success -> {
                    dataStore.edit { prefs ->
                        result.sessionId?.let { prefs[SESSION_ID] = it }
                        prefs[CREDENTIALS] = result.data.toStringSet()
                        prefs[LOCAL_CREDENTIAL_ID] = credentialId
                    }
                    signInStateMutable.emit(SignInState.SignedIn("TODO"))
                    refreshCredentials()
                }
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Cannot call registerResponse", e)
        }
    }
}
