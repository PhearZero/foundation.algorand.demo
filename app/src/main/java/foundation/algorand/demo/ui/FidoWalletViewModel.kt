package foundation.algorand.demo.ui

import android.app.PendingIntent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.algorand.demo.fido2.repository.AuthRepository
import foundation.algorand.demo.fido2.repository.SignInState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FidoWalletViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val authRequestChannel = Channel<PendingIntent>(capacity = Channel.CONFLATED)
    val authRequests = authRequestChannel.receiveAsFlow()

    // Handle Processing State
    private val _processing = MutableStateFlow(false)
    val processing = _processing.asStateFlow()

    // FIDO Credentials
    val credentials = repository.credentials.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), emptyList()
    )

    val currentUsername = repository.signInState.map { state ->
        when (state) {
            is SignInState.SignedIn -> repository.account!!.address.toString()
            else -> "(public key)"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "(user)")

    val account = repository.account
    init {
        // See if we can authenticate using FIDO.
        viewModelScope.launch {
            val intent = assertionRequest()
            if (intent != null) {
                authRequestChannel.send(intent)
            }
        }
    }

    /**
     * Delete Credential Key
     */
    fun deleteKey(credentialId: String) {
        viewModelScope.launch {
            _processing.value = true
            try {
                repository.deleteKey(credentialId)
            } finally {
                _processing.value = false
            }
        }
    }

    /**
     * Fetch an assertion from the API
     *
     * Uses the last known credential
     */
    suspend fun assertionRequest(): PendingIntent? {
        _processing.value = true
        try {
            return repository.assertionRequest()
        } finally {
            _processing.value = false
        }
    }

    fun assertionResponse(credential: PublicKeyCredential) {
        viewModelScope.launch {
            _processing.value = true
            try {
                repository.assertionResponse(credential)
            } finally {
                _processing.value = false
            }
        }
    }

    suspend fun attestationRequest(): PendingIntent? {
        _processing.value = true
        try {
            return repository.attestationRequest()
        } finally {
            _processing.value = false
        }
    }

    fun attestationResponse(credential: PublicKeyCredential) {
        viewModelScope.launch {
            _processing.value = true
            try {
                repository.attestationResponse(credential)
            } finally {
                _processing.value = false
            }
        }
    }
}