package foundation.algorand.demo.ui

import android.app.PendingIntent
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algorand.algosdk.account.Account
import com.algorand.algosdk.builder.transaction.PaymentTransactionBuilder
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.mlkit.vision.barcode.common.Barcode
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.algorand.demo.fido2.repository.AuthRepository
import foundation.algorand.demo.fido2.repository.SignInState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.donations.direct.crypto.CryptoRepository
import org.json.JSONObject
import java.security.KeyPair
import javax.inject.Inject

@HiltViewModel
class FidoWalletViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val cryptoRepository = CryptoRepository()
    companion object {
        private const val TAG = "fido2.WalletViewModel"
    }
    private val _wallet = MutableLiveData<String>().apply {
        value = ""
    }
    val wallet: LiveData<String> = _wallet
    private var client: AlgodClient? = null



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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "Loading...")

    init {
        val host = "https://testnet-api.algonode.cloud"
        val port = 443
        val token = ""

        client = AlgodClient(
            host,
            port, token
        )

    }
    fun getKeyPair(fragment: Fragment) {
        _wallet.value = Account(cryptoRepository.getKeyPair(fragment)).address.toString()
    }
    /**
     * Delete Credential Key
     */
    fun deleteKey(credentialId: String, origin: String) {
        viewModelScope.launch {
            _processing.value = true
            try {
                repository.deleteKey(credentialId, origin)
            } finally {
                _processing.value = false
            }
        }
    }




    suspend fun attestationRequest(origin: String): PendingIntent? {
        _processing.value = true
        try {
            return repository.attestationRequest(origin)
        } finally {
            _processing.value = false
        }
    }

    fun attestationResponse(credential: PublicKeyCredential, origin: String) {
        viewModelScope.launch {
            _processing.value = true
            try {
                repository.attestationResponse(credential, origin)
            } finally {
                _processing.value = false
            }
        }
    }
    fun parseBarcodeTransaction(barcode: Barcode): PaymentTransactionBuilder<*> {
        val jObject = JSONObject(barcode.displayValue.toString())
        // TODO: Parse transaction properties
        val amount = Integer.parseInt(jObject.get("amount").toString())
        val sender = Address(jObject.get("from").toString())
        val receiver = Address(jObject.get("to").toString())
        val note = "FIDO2 Local Wallet Transfer"
        // TODO: Handle all transaction types
        // val type = "pay"

        // TODO: Use Generic Transaction Builder
        return Transaction.PaymentTransactionBuilder()
            .sender(sender)
            .receiver(receiver)
            .amount(amount)
            .note(note.toByteArray())
    }
    fun sendTransaction(fragment: Fragment, txn: PaymentTransactionBuilder<*>): String? {
        try {
            val acc =
                Account(cryptoRepository.getKeyPair(fragment))

            val params = client?.TransactionParams()?.execute()?.body()
            // Sign the transaction
            val signedTxn: SignedTransaction =
                acc.signTransaction(txn.suggestedParams(params).build())
            Log.d("algoDebug", "Signed transaction with txid: " + signedTxn.transactionID)

            // Submit the transaction to the network
            val encodedTxBytes: ByteArray = Encoder.encodeToMsgPack(signedTxn)
            Log.d(TAG, encodedTxBytes.toString())
            val id = client?.RawTransaction()?.rawtxn(encodedTxBytes)?.execute()?.body()?.txId
            val idStr = id.toString()
            return idStr
        } catch (e: java.lang.Exception) {
            Log.e("algoDebug", "Exception when calling algod#transactionInformation: " + e.message)
            return null
        }
    }
}