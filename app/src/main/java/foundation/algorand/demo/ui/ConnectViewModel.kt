package foundation.algorand.demo.ui

import android.app.PendingIntent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algorand.algosdk.mnemonic.Mnemonic
import com.algorand.algosdk.util.CryptoProvider
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.algorand.demo.fido2.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Base64
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import javax.inject.Inject

//https://github.com/algorand/java-algorand-sdk/blob/ed90c24a9b4439d3e5df603a04c4658668d119f1/src/main/java/com/algorand/algosdk/account/Account.java#L629
class FixedSecureRandom(fixedValue: ByteArray) : SecureRandom() {
    private val fixedValue: ByteArray
    private var index = 0

    init {
        this.fixedValue = fixedValue.copyOf(fixedValue.size)
    }

    override fun nextBytes(bytes: ByteArray) {
        if (index >= fixedValue.size) {
            // no more data to copy
            return
        }
        var len = bytes.size
        if (len > fixedValue.size - index) {
            len = fixedValue.size - index
        }
        System.arraycopy(fixedValue, index, bytes, 0, len)
        index += bytes.size
    }

    override fun generateSeed(numBytes: Int): ByteArray {
        val bytes = ByteArray(numBytes)
        nextBytes(bytes)
        return bytes
    }
}
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    val authRequestChannel = Channel<PendingIntent>(capacity = Channel.CONFLATED)
    val authRequests = authRequestChannel.receiveAsFlow()
    companion object {
        private const val TAG = "fido2.ConnectViewModel"
    }

    /**
     * Sign the given bytes, and wrap in Signature.
     * @param bytes the data to sign
     * @return a signature
     */
    @Throws(NoSuchAlgorithmException::class)
    private fun rawSignBytes(bytes: ByteArray, key: PrivateKey): ByteArray? {
        return try {
            CryptoProvider.setupIfNeeded()
            val signer = Signature.getInstance("EdDSA")
            signer.initSign(key)
            signer.update(bytes)
            return signer.sign()
        } catch (e: InvalidKeyException) {
            throw RuntimeException("unexpected behavior", e)
        } catch (e: SignatureException) {
            throw RuntimeException("unexpected behavior", e)
        }
    }
   suspend fun connectResponse(requestId: Double, challenge: String, origin: String){
       val seed = Mnemonic.toKey("industry kangaroo visa history swarm exotic doctor fade strike honey ride bicycle pistol large eager solution midnight loan give list company behave purpose abstract good")
       val fixedRandom = FixedSecureRandom(seed)
       //https://github.com/algorand/java-algorand-sdk/blob/ed90c24a9b4439d3e5df603a04c4658668d119f1/src/main/java/com/algorand/algosdk/account/Account.java#L71
       val gen = KeyPairGenerator.getInstance("Ed25519")
       gen.initialize(32 * 8, fixedRandom)
       val keyPair = gen.generateKeyPair()

       Log.d(TAG, "challenge: $challenge")
       val challengeBytes = rawSignBytes(challenge.toByteArray(StandardCharsets.UTF_8), keyPair.private)
       val signature = Base64.encodeBase64URLSafeString(challengeBytes)

       Log.d(TAG, "connectResponse($requestId, $origin)")
       try {
           repository.connectResponse(requestId, challenge, signature, origin)
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