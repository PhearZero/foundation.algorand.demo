package foundation.algorand.demo.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.algorand.algosdk.account.Account
import com.algorand.algosdk.builder.transaction.PaymentTransactionBuilder
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.common.Response
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.R
import foundation.algorand.demo.databinding.FragmentFidoWalletBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject


@AndroidEntryPoint
class FidoWalletFragment : Fragment(), DeleteConfirmationFragment.Listener {
    private var client: AlgodClient? = null
    private val scope = MainScope()
    private val viewModel: FidoWalletViewModel by viewModels()
    private lateinit var binding: FragmentFidoWalletBinding
    private lateinit var scanner: GmsBarcodeScanner

    /**
     * Creating Credentials
     */
    private val attestationIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ::handleAttestation
    )

    /**
     * Asserting existing Credentials
     */
    private val assertionIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ::handleAssertion
    )

    companion object {
        private const val TAG = "FidoWalletFragment"
        private const val FRAGMENT_DELETE_CONFIRMATION = "delete_confirmation"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Barcode Scanner
        scanner = GmsBarcodeScanning.getClient(requireContext())

        // Algorand Client
        client = AlgodClient(
            "https://testnet-api.algonode.cloud",
            443, ""
        )

        binding = FragmentFidoWalletBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val credentialAdapter = CredentialAdapter { credentialId ->
            DeleteConfirmationFragment.newInstance(credentialId)
                .show(childFragmentManager, FRAGMENT_DELETE_CONFIRMATION)
        }
        binding.credentials.adapter = credentialAdapter

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            // Launch credential check
            launch {
                viewModel.authRequests.collect { intent ->
                    assertionIntentLauncher.launch(IntentSenderRequest.Builder(intent).build())
                }
            }
            // Load Stored Credentials
            viewModel.credentials.collect { credentials ->
                credentialAdapter.submitList(credentials)
                if (credentials.isEmpty()) {
                    displayEmptyCredentials()
                } else {
                    displayBarcodeScanner()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onDeleteConfirmed(credentialId: String) {
        Log.d(TAG, "Delete Confirmed")
        viewModel.deleteKey(credentialId)
    }

    /**
     * When there are no credentials, configure the view for credential creation
     */
    private fun displayEmptyCredentials() {
        binding.emptyCredentials.isVisible = true
        binding.credentialsCaption.isVisible = false
        binding.fab.setImageResource(R.drawable.ic_add)
        binding.fab.setOnClickListener { handleCreateCredentialClick() }
    }

    /**
     * Handle FAB Create Credential Clicks
     */
    private fun handleCreateCredentialClick() {
        scope.launch {
            val intent = viewModel.attestationRequest()
            if (intent != null) {
                attestationIntentLauncher.launch(
                    IntentSenderRequest.Builder(intent).build()
                )
            }
        }
    }

    /**
     * When there are credentials, unlock the application
     */
    private fun displayBarcodeScanner() {
        binding.emptyCredentials.isVisible = false
        binding.credentialsCaption.isVisible = true
        binding.fab.setImageResource(R.drawable.baseline_qr_code_scanner_24)
        binding.fab.setOnClickListener { handleBarCodeScannerClick() }
    }

    /**
     * Handle FAB Barcode Scanner Clicks
     */
    private fun handleBarCodeScannerClick() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                var txnId: String?

                scope.launch {
                    Toast.makeText(
                        requireContext(),
                        "Sending Transaction",
                        Toast.LENGTH_LONG
                    )
                        .show()
                    val txn = parseBarcodeTransaction(barcode);
                    txnId = sendTransaction(txn)

                    Toast.makeText(
                        requireContext(),
                        "Transaction Sent",
                        Toast.LENGTH_LONG
                    ).show()

                }
//                scope.launch {
//                    Log.d("algoDebug", "Successfully sent tx with ID: $id")
//
//                    // Wait for transaction confirmation
//
//                    txnId?.let { waitForConfirmation(it) }
//                    // Read the transaction
//                    val pTrx = client!!.PendingTransactionInformation(txnId).execute().body()
//                    Log.d("algoDebug", "Transaction information (with notes): $pTrx")
//
//
//                }

            }
            .addOnCanceledListener {
                Toast.makeText(requireContext(), R.string.cancelled, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            }
    }

    // TODO: move to View Model
    @Throws(Exception::class)
    fun waitForConfirmation(txID: String) {
        var lastRound = client!!.GetStatus().execute().body().lastRound
        while (true) {
            try {
                // Check the pending tranactions
                val pendingInfo: Response<PendingTransactionResponse> =
                    client!!.PendingTransactionInformation(txID).execute()
                if (pendingInfo.body().confirmedRound != null && pendingInfo.body().confirmedRound > 0) {
                    // Got the completed Transaction
                    Toast.makeText(
                        requireContext(),
                        "Transaction " + txID + " confirmed in round " + pendingInfo.body().confirmedRound,
                        Toast.LENGTH_LONG
                    ).show()
                    break
                }
                lastRound = lastRound!! + 1
                client!!.WaitForBlock(lastRound).execute()
            } catch (e: Exception) {
                throw e
            }
        }
    }

    //TODO: move to viewmodel
    fun sendTransaction(txn: PaymentTransactionBuilder<*>): String? {
        try {
            //GPRWRIWNEUEJXHEJGN5JKBLMPL327D7OAXVMEDVHK64KYDY7SXUF5VZP6A
            val acc =
                Account("industry kangaroo visa history swarm exotic doctor fade strike honey ride bicycle pistol large eager solution midnight loan give list company behave purpose abstract good")

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

    // TODO: move to viewModel
    private fun parseBarcodeTransaction(barcode: Barcode): PaymentTransactionBuilder<*> {
        val jObject = JSONObject(barcode.displayValue.toString())
        // TODO: Parse transaction properties
        val amount = Integer.parseInt(jObject.get("amount").toString())
        val sender = Address(jObject.get("from").toString())
        val receiver = Address(jObject.get("to").toString())
        val note = "FIDO2 Local Wallet Transfer"
        // TODO: Handle all transaction types
        val type = "pay"

        // TODO: Use Generic Transaction Builder
        return Transaction.PaymentTransactionBuilder()
            .sender(sender)
            .receiver(receiver)
            .amount(amount)
            .note(note.toByteArray())
    }


    /**
     * Creates a new Credential and submits it to the API
     */
    private fun handleAttestation(activityResult: ActivityResult) {
        Log.d(TAG, "Handle Attestation")
        val bytes = activityResult.data?.getByteArrayExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA)
        when {
            activityResult.resultCode != Activity.RESULT_OK ->
                Toast.makeText(requireContext(), R.string.cancelled, Toast.LENGTH_LONG).show()

            bytes == null ->
                Toast.makeText(requireContext(), R.string.credential_error, Toast.LENGTH_LONG)
                    .show()

            else -> {
                Log.d(TAG, bytes.toString())
                val credential = PublicKeyCredential.deserializeFromBytes(bytes)
                val response = credential.response
                if (response is AuthenticatorErrorResponse) {
                    Toast.makeText(requireContext(), response.errorMessage, Toast.LENGTH_LONG)
                        .show()
                } else {
                    viewModel.attestationResponse(credential)
                    binding.fab.setImageResource(R.drawable.baseline_qr_code_scanner_24)
                }
            }
        }
    }

    /**
     * Assert an existing credential
     */
    private fun handleAssertion(activityResult: ActivityResult) {
        Log.d(TAG, "Handle Assertion")
        val bytes = activityResult.data?.getByteArrayExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA)
        when {
            activityResult.resultCode != Activity.RESULT_OK ->
                Toast.makeText(requireContext(), R.string.cancelled, Toast.LENGTH_LONG).show()

            bytes == null ->
                Toast.makeText(requireContext(), R.string.auth_error, Toast.LENGTH_LONG).show()

            else -> {
                val credential = PublicKeyCredential.deserializeFromBytes(bytes)
                val response = credential.response
                if (response is AuthenticatorErrorResponse) {
                    Toast.makeText(requireContext(), response.errorMessage, Toast.LENGTH_LONG)
                        .show()
                } else {
                    // Assert the credential with the API, this registers the Credential in the Database
                    viewModel.assertionResponse(credential)
                }
            }
        }
    }
}