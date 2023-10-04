package foundation.algorand.demo

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.fido.Fido
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.ui.ConnectFragment
import foundation.algorand.demo.ui.FidoWalletFragment
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val viewModel: MainViewModel by viewModels()

    /**
     * Update security provider for Algorand SDK
     */
    private fun setSecurity(){
        val providerName = "BC"
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 0)

        if (Security.getProvider(providerName) == null)
        {
            Log.d("algoDebug",providerName + " provider not installed");
        }
        else
        {
            Log.d("algoDebug",providerName + " is installed.");
        }
    }

    /**
     * Handle State Changes
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        setContentView( R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        setSecurity()

        sharedPreferences = getSharedPreferences("base-url", Context.MODE_PRIVATE)
        if (sharedPreferences.contains(viewModel.originKey)) {
            viewModel.baseURL = sharedPreferences.getString(viewModel.originKey, "")
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.uiState.collect {
                    val url = viewModel.baseURL
                    if(url === null){
                        showFragment(ConnectFragment::class.java) {ConnectFragment()}
                    } else {
                        showFragment(FidoWalletFragment::class.java) {FidoWalletFragment()}
                    }
//                }
            }
        }
    }
    fun updateBaseURL(url: String){
        Log.d("Main", url)
        viewModel.baseURL = url
    }
    override fun onResume() {
        super.onResume()
        viewModel.setFido2ApiClient(Fido.getFido2ApiClient(this@MainActivity))
//        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setFido2ApiClient(null)
    }

    private fun showFragment(clazz: Class<out Fragment>, create: () -> Fragment) {
        val manager = supportFragmentManager
        if (!clazz.isInstance(manager.findFragmentById(R.id.container))) {
            manager.commit {
                replace(R.id.container, create())
            }
        }
    }
}