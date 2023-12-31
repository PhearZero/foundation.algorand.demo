package foundation.algorand.demo

import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.gms.fido.Fido
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.databinding.ActivityMainBinding
import foundation.algorand.demo.ui.ConnectFragment
import foundation.algorand.demo.ui.FidoWalletFragment
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    companion object {
        private const val TAG = "fido2.MainActivity"
    }
    /**
     * Update security provider for Algorand SDK
     */
    private fun setSecurity(){
        val providerName = "BC"
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 0)

        if (Security.getProvider(providerName) == null)
        {
            Log.d(TAG,providerName + " provider not installed")
        }
        else
        {
            Log.d(TAG,providerName + " is installed.")
        }
    }

    /**
     * Handle State Changes
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        setSecurity()
        viewModel.origin.observe(this) {
            render(it)
        }
    }
    private fun render(url: String){
        binding.host.text = url
        if(url === ""){
            showFragment(ConnectFragment::class.java) {ConnectFragment()}
        } else {
            showFragment(FidoWalletFragment::class.java) {FidoWalletFragment()}
        }
    }
    fun updateBaseURL(url: String){
        Log.d(TAG, "updateBaseURL($url)")
        viewModel.setOrigin(url)
    }
    override fun onResume() {
        Log.d(TAG, "onResume()")
        super.onResume()
        viewModel.setFido2ApiClient(Fido.getFido2ApiClient(this@MainActivity))
    }

    override fun onPause() {
        Log.d(TAG, "onPause()")
        super.onPause()
        viewModel.setFido2ApiClient(null)
    }

    private fun showFragment(clazz: Class<out Fragment>, create: () -> Fragment) {
        Log.d(TAG, "showFragment()")
        val manager = supportFragmentManager
        if (!clazz.isInstance(manager.findFragmentById(R.id.container))) {
            manager.commit {
                replace(R.id.container, create())
            }
        }
    }
}