package foundation.algorand.demo.ui

import android.app.PendingIntent
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algorand.algosdk.account.Account
import com.algorand.algosdk.mnemonic.Mnemonic
import com.algorand.algosdk.util.CryptoProvider
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.algorand.demo.fido2.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Base64
import org.donations.direct.crypto.CryptoRepository
import org.donations.direct.crypto.FixedSecureRandom
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val cryptoRepository = CryptoRepository()
    val authRequestChannel = Channel<PendingIntent>(capacity = Channel.CONFLATED)
    val authRequests = authRequestChannel.receiveAsFlow()

    private val _wallet = MutableLiveData<String>().apply {
        value = ""
    }
    val wallet: LiveData<String> = _wallet
    companion object {
        private const val TAG = "fido2.ConnectViewModel"
    }

    fun getKeyPair(fragment: Fragment): KeyPair {
        Log.d(TAG, "getKeyPair(${wallet.value})")
        val keyPair = cryptoRepository.getKeyPair(fragment)
        _wallet.value = Account(keyPair).address.toString()
        return keyPair
    }

   suspend fun connectResponse(fragment: Fragment, requestId: Double, challenge: String, origin: String){
       Log.d(TAG, "connectResponse($requestId, $challenge, $origin)")
       val keyPair = getKeyPair(fragment);
       val challengeBytes = cryptoRepository.rawSignBytes(challenge.toByteArray(StandardCharsets.UTF_8), keyPair.private)
       val signature = Base64.encodeBase64URLSafeString(challengeBytes)
       try {
           repository.connectResponse(requestId, challenge, signature, wallet.value!!, origin)
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