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
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.MainActivity
import foundation.algorand.demo.R
import foundation.algorand.demo.databinding.FragmentFidoWalletBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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



    companion object {
        private const val TAG = "fido2.WalletFragment"
        private const val FRAGMENT_DELETE_CONFIRMATION = "delete_confirmation"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel.getKeyPair(this)
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

       lifecycleScope.launch {
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
        val origin = (activity as MainActivity).viewModel.baseURL!!
        viewModel.deleteKey(credentialId, origin)
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
        val origin = (activity as MainActivity).viewModel.baseURL!!
        scope.launch {
            val intent = viewModel.attestationRequest(origin)
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

                lifecycleScope.launch {
                    Toast.makeText(
                        requireContext(),
                        "Sending Transaction",
                        Toast.LENGTH_LONG
                    )
                        .show()
                    val txn = viewModel.parseBarcodeTransaction(barcode)
                    txnId = viewModel.sendTransaction(txn)
                    Log.w(TAG, "Transaction sent with ID: $txnId")
                    Toast.makeText(
                        requireContext(),
                        "Transaction Sent",
                        Toast.LENGTH_LONG
                    ).show()

                }
            }
            .addOnCanceledListener {
                Toast.makeText(requireContext(), R.string.cancelled, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            }
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
                val credential = PublicKeyCredential.deserializeFromBytes(bytes)
                val response = credential.response
                if (response is AuthenticatorErrorResponse) {
                    Toast.makeText(requireContext(), response.errorMessage, Toast.LENGTH_LONG)
                        .show()
                } else {
                    val origin = (activity as MainActivity).viewModel.baseURL!!
                    viewModel.attestationResponse(credential, origin)
                    binding.fab.setImageResource(R.drawable.baseline_qr_code_scanner_24)
                }
            }
        }
    }


}