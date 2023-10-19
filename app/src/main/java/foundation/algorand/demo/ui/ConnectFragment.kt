package foundation.algorand.demo.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.algorand.algosdk.builder.transaction.PaymentTransactionBuilder
import com.algorand.algosdk.crypto.Address
import com.algorand.algosdk.transaction.Transaction
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
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentConnectBinding.inflate(inflater, container, false)

        // Barcode Scanner
        scanner = GmsBarcodeScanning.getClient(requireContext())
        binding.fab.setOnClickListener { handleBarCodeScannerClick() }
        return binding.root
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

    // TODO: move to viewModel
    private fun parseBarcodeTransaction(barcode: Barcode) {
        val jObject = JSONObject(barcode.displayValue.toString())
        // TODO: Parse transaction properties
        val origin = jObject.get("origin").toString()
        val requestId = jObject.get("requestId").toString().toDouble()
        Log.d("COnnectFragment", requestId.toString())
        lifecycleScope.launch {
            viewModel.connectResponse(requestId)
        }
    }
}