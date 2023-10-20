package foundation.algorand.demo

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.fido.fido2.Fido2ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.algorand.demo.fido2.repository.AuthRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    var baseURL: String? = null
    companion object {
        private const val TAG = "fido2.MainViewModel"
    }
    fun setFido2ApiClient(client: Fido2ApiClient?) {
        Log.d(TAG, "setFido2ApiClient($client)")
        repository.setFido2APiClient(client)
    }

}
