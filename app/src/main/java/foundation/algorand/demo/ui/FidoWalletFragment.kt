package foundation.algorand.demo.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.R
import foundation.algorand.demo.databinding.FragmentFidoWalletBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FidoWalletFragment : Fragment(), DeleteConfirmationFragment.Listener {
    private val viewModel: FidoWalletViewModel by viewModels()
    private lateinit var binding: FragmentFidoWalletBinding
    private lateinit var scanner: GmsBarcodeScanner

    private val attestationIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ::handleAttestationResult
    )

    private val assertionIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ::handleAssertionResult
    )

    companion object {
        private const val TAG = "FidoWallet"
        private const val FRAGMENT_DELETE_CONFIRMATION = "delete_confirmation"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFidoWalletBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        scanner = GmsBarcodeScanning.getClient(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val credentialAdapter = CredentialAdapter { credentialId ->
            DeleteConfirmationFragment.newInstance(credentialId)
                .show(childFragmentManager, FRAGMENT_DELETE_CONFIRMATION)
        }
        binding.credentials.adapter = credentialAdapter

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            launch {
                viewModel.authRequests.collect { intent ->
                    assertionIntentLauncher.launch(IntentSenderRequest.Builder(intent).build())
                }
            }
            // Load Stored Credentials
            viewModel.credentials.collect { credentials ->
                credentialAdapter.submitList(credentials)

                if (credentials.isEmpty()) {
                    binding.fab.setImageResource(R.drawable.ic_add)
                    binding.fab.setOnClickListener {
//                        viewModel.createSession()
                        Toast.makeText(requireContext(), "Clicked", Toast.LENGTH_LONG).show()
                        launch {
                            val intent = viewModel.attestationRequest()
                            if (intent != null) {
                                attestationIntentLauncher.launch(
                                    IntentSenderRequest.Builder(intent).build()
                                )
                            }
                        }
                    }
                } else {
                    binding.fab.setImageResource(R.drawable.baseline_qr_code_scanner_24)
                    binding.fab.setOnClickListener {

                        Toast.makeText(requireContext(), "Clicked", Toast.LENGTH_LONG).show()
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->

                                // Task completed successfully
//                    barcode.rawBytes?.let { it1 -> Log.d("SCANNER", it1) }
                                Toast.makeText(
                                    requireContext(),
                                    barcode.displayValue,
                                    Toast.LENGTH_LONG
                                )
                                    .show()
                            }
                            .addOnCanceledListener {
                                // Task canceled
                            }
                            .addOnFailureListener { e ->
                                // Task failed with an exception
                            }
                    }
                }
                binding.emptyCredentials.isVisible = credentials.isEmpty()
                binding.credentialsCaption.isVisible = credentials.isNotEmpty()
            }
        }
    }

    override fun onDeleteConfirmed(credentialId: String) {
        Log.d(TAG, "Delete Confirmed")
        viewModel.deleteKey(credentialId)
    }

    /**
     * Create a PublicKeyCredential
     *
     */
    private fun handleAssertionResult(activityResult: ActivityResult) {
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
                    viewModel.assertionResponse(credential)
                }
            }
        }
    }

    private fun handleAttestationResult(activityResult: ActivityResult) {
        Log.d(TAG, "Wow")
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
}