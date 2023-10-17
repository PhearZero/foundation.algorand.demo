package foundation.algorand.demo.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.algorand.demo.fido2.repository.AuthRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    companion object {
        private const val TAG = "ConnectViewModel"
    }

   suspend fun connectResponse(requestId: String){
       viewModelScope.launch {
           try {
               repository.connectResponse(requestId)
           } finally {
               Log.d(TAG, "Hello Moto")
           }
       }
   }
}