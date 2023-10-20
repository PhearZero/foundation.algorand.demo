package foundation.algorand.demo.fido2.repository

/**
 * Wallet State
 *
 * Track changes to remote origins
 */
sealed class WalletState {

    /**
     * The user is signed out.
     */
    object NoWallet : WalletState()

    /**
     * The user sign-in failed.
     */
    data class WalletError(
        val error: String
    ) : WalletState()

    data class Wallet(
        val address: String
    ) : WalletState()
    /**
     * Wallet has an origin
     */
    data class WalletWithOrigin(
        val wallet: String,
        val origin: String
    ) : WalletState()
}
