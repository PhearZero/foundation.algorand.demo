package org.donations.direct.crypto

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import com.algorand.algosdk.account.Account
import com.algorand.algosdk.mnemonic.Mnemonic
import com.algorand.algosdk.util.CryptoProvider
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Signature
import java.security.SignatureException
interface CryptoRepository {
    fun rawSignBytes(bytes: ByteArray, key: PrivateKey): ByteArray?
    fun getKeyPair(): KeyPair
    fun getKeyPair(mnemonic: String): KeyPair
    fun getKeyPair(context: Context): KeyPair
    fun getKeyPair(fragment: Fragment): KeyPair
}
fun CryptoRepository(): CryptoRepository = Repository()
class Repository: CryptoRepository {
    companion object {
        const val TAG = "CryptoRepository"
        const val SHARED_PREFERENCE = "wallet"
        const val SHARED_PREFERENCE_KEY = "mnemonic"
        const val KEY_ALGO =  "Ed25519"
        private const val SK_SIZE = 32
        const val SK_SIZE_BITS = SK_SIZE * 8
    }
    private val generator: KeyPairGenerator =  KeyPairGenerator.getInstance(KEY_ALGO)
    init {
        CryptoProvider.setupIfNeeded()
    }
    @Throws(NoSuchAlgorithmException::class)
    override fun rawSignBytes(bytes: ByteArray, key: PrivateKey): ByteArray? {
        try {
            val signer = Signature.getInstance("EdDSA")
            signer.initSign(key)
            signer.update(bytes)
            return signer.sign()
        } catch (e: InvalidKeyException) {
            throw RuntimeException("unexpected behavior", e)
        } catch (e: SignatureException) {
            throw RuntimeException("unexpected behavior", e)
        }
    }
    override fun getKeyPair(): KeyPair {
        return getKeyPair(Account().toMnemonic())
    }
    override fun getKeyPair(mnemonic: String): KeyPair {
        Log.d(TAG, "getKeyPair(******)")
        val seed = Mnemonic.toKey(mnemonic)
        val fixedRandom = FixedSecureRandom(seed)
        generator.initialize(SK_SIZE_BITS, fixedRandom)
        return generator.genKeyPair()
    }
    override fun getKeyPair(fragment: Fragment): KeyPair {
        Log.d(TAG, "getKeyPair($fragment)")
        return getKeyPair(fragment.requireContext())
    }
    override fun getKeyPair(context: Context): KeyPair {
        Log.d(TAG, "getKeyPair($context)")
        val sharedPref = context.getSharedPreferences(SHARED_PREFERENCE, Context.MODE_PRIVATE)
        val mnemonicPref = sharedPref.getString(SHARED_PREFERENCE_KEY, null)
        return if(mnemonicPref === null){
            val mnemonic = Account().toMnemonic()
            // TODO: Find a more secure storage
            // Save to preferences, this is very insecure!!
            with (sharedPref.edit()){
                putString("mnemonic", mnemonic)
                apply()
            }
            getKeyPair(mnemonic)
        } else {
            getKeyPair(mnemonicPref)
        }
    }
}
