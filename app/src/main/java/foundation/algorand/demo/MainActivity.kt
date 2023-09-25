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
//import foundation.algorand.demo.databinding.ActivityMainBinding
import foundation.algorand.demo.fido2.repository.SignInState
import foundation.algorand.demo.ui.auth.AuthFragment
import foundation.algorand.demo.ui.home.HomeFragment
import foundation.algorand.demo.ui.username.UsernameFragment


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        Toast.makeText(this@MainActivity, "loading", Toast.LENGTH_LONG).show()

        lifecycleScope.launchWhenStarted {
            Toast.makeText(this@MainActivity, "Launch on start", Toast.LENGTH_LONG).show()
            viewModel.signInState.collect { state ->
                when (state) {
                    is SignInState.SignedOut -> {
                        showFragment(UsernameFragment::class.java) { UsernameFragment() }
                    }
                    is SignInState.SigningIn -> {
                        showFragment(AuthFragment::class.java) { AuthFragment() }
                    }
                    is SignInState.SignInError -> {
                        Toast.makeText(this@MainActivity, state.error, Toast.LENGTH_LONG).show()
                        // return to username prompt
                        showFragment(UsernameFragment::class.java) { UsernameFragment() }
                    }
                    is SignInState.SignedIn -> {
                        showFragment(HomeFragment::class.java) { HomeFragment() }
                    }
                }
            }
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
        Toast.makeText(this@MainActivity, "show fragment", Toast.LENGTH_LONG).show()
        val manager = supportFragmentManager
        if (!clazz.isInstance(manager.findFragmentById(R.id.container))) {
            manager.commit {
                replace(R.id.container, create())
            }
        }
    }
}