package foundation.algorand.demo.ui

import android.app.PendingIntent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algorand.algosdk.account.Account
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.common.Response
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse
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
    companion object {
        private const val TAG = "FidoWalletViewModel"
    }

    private var client: AlgodClient? = null

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "Loading...")

    val account = repository.account
    init {
        val host = "https://testnet-api.algonode.cloud"
        val port = 443
        val token = ""

        client = AlgodClient(
            host,
            port, token
        )
        // See if we can authenticate using FIDO.
        viewModelScope.launch {
            val intent = assertionRequest()
            if (intent != null) {
                authRequestChannel.send(intent)
            }
        }
    }
    fun getAccountPlease(): Account?{
        return account
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

    fun sendTransaction(){
        try {
            //GPRWRIWNEUEJXHEJGN5JKBLMPL327D7OAXVMEDVHK64KYDY7SXUF5VZP6A
            val acc =
                Account("curve spend coral camera ladder frost citizen volcano tobacco bronze weapon sustain taxi age donkey belt jeans civil fetch lonely enough swarm wet absorb coffee")
            val addr = acc.address.toString()
            Log.d(TAG, "Sending $addr")
            Log.d(TAG, acc.toMnemonic())
            Log.d(TAG, client.toString())
//            if (viewModel.currentAccount as Account? != null) {
            // Construct the transaction
            val RECEIVER = "L5EUPCF4ROKNZMAE37R5FY2T5DF2M3NVYLPKSGWTUKVJRUGIW4RKVPNPD4"
            val note = "Hello World"
            val params = client?.TransactionParams()?.execute()?.body()
            Log.d(TAG, params.toString())
            val txn: Transaction =
                Transaction.PaymentTransactionBuilder().sender(acc.address)
                    .note(note.toByteArray())
                    .amount(100000).receiver(Address(RECEIVER)).suggestedParams(params).build()
            Log.d(TAG, txn.toString())
            // Sign the transaction
            val signedTxn: SignedTransaction = acc.signTransaction(txn)
            Log.d("algoDebug", "Signed transaction with txid: " + signedTxn.transactionID)

            // Submit the transaction to the network
            val encodedTxBytes: ByteArray = Encoder.encodeToMsgPack(signedTxn)
            Log.d(TAG, encodedTxBytes.toString())
            val id = client?.RawTransaction()?.rawtxn(encodedTxBytes)?.execute()?.body()?.txId
            val idStr = id.toString()
            Log.d("algoDebug", "Successfully sent tx with ID: $id")

            // Wait for transaction confirmation
            waitForConfirmation(idStr)

            // Read the transaction
            val pTrx = client!!.PendingTransactionInformation(id).execute().body()
            Log.d("algoDebug", "Transaction information (with notes): $pTrx")
        } catch (e: java.lang.Exception) {
            Log.e("algoDebug", "Exception when calling algod#transactionInformation: " + e.message)
        }
    }

    @Throws(Exception::class)
    fun waitForConfirmation(txID: String) {
        //        if (client == null)
        //            this.client = connectToNetwork();
        var lastRound = client!!.GetStatus().execute().body().lastRound
        while (true) {
            try {
                // Check the pending tranactions
                val pendingInfo: Response<PendingTransactionResponse> =
                    client!!.PendingTransactionInformation(txID).execute()
                if (pendingInfo.body().confirmedRound != null && pendingInfo.body().confirmedRound > 0) {
                    // Got the completed Transaction
                    Log.d(
                        "algoDebug",
                        "Transaction " + txID + " confirmed in round " + pendingInfo.body().confirmedRound
                    )
                    break
                }
                lastRound = lastRound!! + 1
                client!!.WaitForBlock(lastRound).execute()
            } catch (e: Exception) {
                throw e
            }
        }
    }
}