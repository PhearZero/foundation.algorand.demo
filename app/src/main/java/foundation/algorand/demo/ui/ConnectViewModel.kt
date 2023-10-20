package foundation.algorand.demo.ui

import android.app.PendingIntent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.algorand.demo.fido2.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    val authRequestChannel = Channel<PendingIntent>(capacity = Channel.CONFLATED)
    val authRequests = authRequestChannel.receiveAsFlow()
    companion object {
        private const val TAG = "fido2.ConnectViewModel"
    }

   suspend fun connectResponse(requestId: Double, origin: String){
       Log.d(TAG, "connectResponse($requestId, $origin)")
       try {
           repository.connectResponse(requestId, origin)
       } finally {
           Log.d(TAG, "Connect Response Finished")
       }
   }
    /**
     * Fetch an assertion from the API
     *
     * Uses the last known credential
     */
    suspend fun assertionRequest(origin: String): PendingIntent? {
        Log.d(TAG, "assertionRequest($origin)")
        try {
            return repository.assertionRequest()
        } finally {
            Log.d(TAG, "Assertion request finished")
        }
    }

    fun assertionResponse(credential: PublicKeyCredential, origin: String) {
        Log.d(TAG, "assertionRequest($origin)")
        viewModelScope.launch {
            try {
                repository.assertionResponse(credential, origin)
            } finally {
                Log.d(TAG, "Assertion finished")
            }
        }
    }
}