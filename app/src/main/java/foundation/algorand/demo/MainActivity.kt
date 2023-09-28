package foundation.algorand.demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.fido.Fido
import dagger.hilt.android.AndroidEntryPoint
import foundation.algorand.demo.fido2.repository.SignInState
import foundation.algorand.demo.ui.FidoWalletFragment
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    /**
     * Update security provider for Algorand SDK
     */
    private fun setSecurity(){
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 0)
    }

    /**
     * Handle State Changes
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        setSecurity()

        // State Transitions
        lifecycleScope.launchWhenStarted {
            showFragment(FidoWalletFragment::class.java) {FidoWalletFragment()}
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setFido2ApiClient(Fido.getFido2ApiClient(this@MainActivity))
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