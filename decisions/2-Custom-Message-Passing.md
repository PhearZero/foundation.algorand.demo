# Overview

- Abandon custom authenticator approach, restricted by browser since 2021
- Implement Wallet linking strategy
- References ARC-31 and ARC-00014 as options
- Search for Message Passing transports that allows the least amount of friction

### Implementation

This implementation settled on a QR Code strategy that allows the wallet to scan a `nonce` which is used to authenticate a
remote session. See the [Architecture](../ARCHITECTURE.md) documentation or the [Bidirectional Communication](https://github.com/PhearZero/nest-fido2/blob/main/decisions/2-Bidirectional-Communication.md) 
decision in `nest-fido2` for more information

In the future, integrations to "Credential Managers" and|or custom authenticators will be more seamless.
See [PassKeys support](https://passkeys.dev/device-support/). This implementation should focus on
just the native FIDO2 Api but be aware of the upcoming features of [Credential Manager](https://developer.android.com/training/sign-in/fido2-migration)
