package foundation.algorand.demo.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.MainActivity
import foundation.algorand.demo.databinding.FragmentConnectBinding
import foundation.algorand.demo.R
import kotlinx.coroutines.launch
import org.json.JSONObject

@AndroidEntryPoint
class ConnectFragment : Fragment() {
    private val viewModel: ConnectViewModel by viewModels()
    private lateinit var scanner: GmsBarcodeScanner
    private lateinit var binding: FragmentConnectBinding
    companion object {
        private const val TAG = "fido2.ConnectFragment"
    }
    /**
     * Asserting existing Credentials
     */
    private val assertionIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ::handleAssertion
    )
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel.getKeyPair(this)
        binding = FragmentConnectBinding.inflate(inflater, container, false)
        val textView: TextView = binding.wallet
        viewModel.wallet.observe(viewLifecycleOwner) {
            textView.text = it
        }
        // Barcode Scanner
        scanner = GmsBarcodeScanning.getClient(requireContext())
        binding.fab.setOnClickListener { handleBarCodeScannerClick() }

        return binding.root
    }
    override fun onResume() {
        Log.d(TAG, "onResume()")
        super.onResume()
        // See if we can authenticate using FIDO.
        lifecycleScope.launch {
            val origin = (activity as MainActivity).viewModel.baseURL
            if(origin !== null){
                val intent = viewModel.assertionRequest(origin)
                if (intent != null) {
                    Log.d(TAG, "Assertion request has intent")
                    viewModel.authRequestChannel.send(intent)
                } else {
                    Log.w(TAG, "Assertion has no intent")
                }
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewModel.authRequests.collect { intent ->
                assertionIntentLauncher.launch(IntentSenderRequest.Builder(intent).build())
            }
        }
    }
    /**
     * Handle FAB Barcode Scanner Clicks
     */
    private fun handleBarCodeScannerClick() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                Toast.makeText(requireContext(), barcode.displayValue, Toast.LENGTH_LONG).show()
                barcode.displayValue?.let {
                    parseBarcodeTransaction(barcode)
                }
            }
            .addOnCanceledListener {
                Toast.makeText(requireContext(), R.string.cancelled, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            }
    }
    private fun parseBarcodeTransaction(barcode: Barcode) {
        val jObject = JSONObject(barcode.displayValue.toString())
        val origin = jObject.get("origin").toString()
        val requestId = jObject.get("requestId").toString().toDouble()
        val challenge = jObject.get("challenge").toString()
        val fragment = this
        lifecycleScope.launch {
            viewModel.connectResponse(fragment, requestId, challenge, origin)
            (activity as MainActivity).updateBaseURL(origin)
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
                    val origin = (activity as MainActivity).viewModel.baseURL!!
                    // Assert the credential with the API, this registers the Credential in the Database
                    viewModel.assertionResponse(credential, origin)
                }
            }
        }
    }
}