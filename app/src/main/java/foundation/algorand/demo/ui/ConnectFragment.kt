package foundation.algorand.demo.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.MainActivity
import foundation.algorand.demo.databinding.FragmentConnectBinding
import foundation.algorand.demo.R

@AndroidEntryPoint
class ConnectFragment : Fragment() {
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
                barcode.displayValue?.let { (activity as MainActivity).updateBaseURL(it) }
            }
            .addOnCanceledListener {
                Toast.makeText(requireContext(), R.string.cancelled, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            }
    }
}