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
import org.donations.direct.crypto.CryptoRepository
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
    private val cryptoRepository = CryptoRepository()
    private companion object {
        const val TAG = "fido2.AuthRepository"

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
    private val walletStateMutable = MutableSharedFlow<WalletState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
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
        Log.d(TAG, "init")
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
//                createSession(username)
            }
            // Create new Wallet
            else {
                val acc = Account(prvKey)
                dataStore.edit { prefs ->
                    prefs[USERNAME] = acc.address.toString()
                }
                account = acc
                val mnemonic = acc.toMnemonic()
                username = acc.address.toString()
//
                Log.w(TAG,  mnemonic)
                Log.w(TAG,  username)
                //createSession(username)
            }
            val initialState = SignInState.SignedIn(username)
            val initialWalletState = WalletState.Wallet(account!!.address.toString())
            walletStateMutable.emit(initialWalletState)
            signInStateMutable.emit(initialState)
//            refreshCredentials()
        }
    }
    suspend fun connectResponse(requestId: Double, challenge: String, signature: String, address: String, origin: String? ){
        Log.d(TAG, "connectResponse($requestId) with Wallet: $address")
        when (val result = api.connectResponse(requestId, address, challenge, signature, origin)){
            is ApiResult.Success -> {
                dataStore.edit { prefs ->
                    prefs[USERNAME] = address
                    prefs[SESSION_ID] = result.sessionId!!
                }
                Log.d(TAG, "Connect Auth Repository Success")
                signInStateMutable.emit(SignInState.SignedIn(address))
            }
            else -> {
                Log.d(TAG, "ERROR")
            }
        }
    }
    /**
     * Create session testing endpoint
     */
    private suspend fun createSession(wallet: String, origin: String) {
        Log.d(TAG, "createSession($wallet)")
        when (val result = api.createSession(wallet, origin)) {
            ApiResult.SignedOutFromServer -> forceSignOut()
            is ApiResult.Success -> {
                dataStore.edit { prefs ->
                    prefs[USERNAME] = wallet
                    prefs[SESSION_ID] = result.sessionId!!
                }
                signInStateMutable.emit(SignInState.SignedIn(wallet))
                walletStateMutable.emit(WalletState.WalletWithOrigin(wallet, origin))
                refreshCredentials(origin)
            }
        }
    }

    private suspend fun refreshCredentials(origin: String) {
        var sessionId = dataStore.read(SESSION_ID)
        val username = dataStore.read(USERNAME)
        Log.d(TAG, "refreshCredentials SessionId: $sessionId Wallet: $username")
        if (username != null) {
            if (sessionId === null) {
                createSession(username, origin)
                sessionId = dataStore.read(SESSION_ID)
            }
            if (sessionId != null) {
                when (val result = api.getKeys(sessionId, origin)) {
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
    suspend fun attestationRequest(origin: String): PendingIntent? {
        Log.d(TAG, "attestationRequest($origin)")
        fido2ApiClient?.let { client ->
            try {
                val sessionId = dataStore.read(SESSION_ID)!!
                Log.d(TAG, sessionId)
                when (val apiResult = api.attestationRequest(sessionId, origin)) {
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
    suspend fun attestationResponse(credential: PublicKeyCredential, origin: String) {
        Log.d(TAG, "attestationResponse($credential, $origin)")
        try {
            val sessionId = dataStore.read(SESSION_ID)!!
            val credentialId = credential.rawId.toBase64()
            when (val result = api.attestationResponse(sessionId, credential, origin)) {
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
    suspend fun deleteKey(credentialId: String, origin: String) {
        try {
            val sessionId = dataStore.read(SESSION_ID)!!
            when (api.deleteKey(sessionId, credentialId)) {
                ApiResult.SignedOutFromServer -> forceSignOut()
                is ApiResult.Success -> refreshCredentials(origin)
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Cannot call removeKey", e)
        }
    }

    /**
     * Starts to sign in with a FIDO2 credential. This should only be called when the sign-in state.
     */
    suspend fun assertionRequest(): PendingIntent? {
        Log.d(TAG, "Running assertion request")
        fido2ApiClient?.let { client ->
            val sessionId = dataStore.read(SESSION_ID)!!
            val credentialId = dataStore.read(LOCAL_CREDENTIAL_ID)
            if (credentialId != null) {
                Log.d(TAG, "CredentialId: $credentialId")
                when (val apiResult = api.assertionRequest(sessionId, credentialId)) {
                    ApiResult.SignedOutFromServer -> Log.d(TAG, "Shutdown")
                    is ApiResult.Success -> {
                        val task = client.getSignPendingIntent(apiResult.data)
                        return task.await()
                    }
                }
            } else {
                Log.w(TAG, "No credential found!")
            }
        }
        return null
    }

    /**
     * Finishes to signing in with a FIDO2 credential. This should only be called after a call to
     * [assertionRequest] and a local FIDO2 API for key assertion.
     */
    suspend fun assertionResponse(credential: PublicKeyCredential, origin: String) {
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
                    refreshCredentials(origin)
                }
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Cannot call registerResponse", e)
        }
    }
}
