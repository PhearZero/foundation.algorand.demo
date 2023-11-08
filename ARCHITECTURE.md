# Overview

Application has two views, Connect and Wallet which connect to the [FIDO2 authentication
service](https://github.com/PhearZero/nest-fido2). Communication between the dApp and Wallet is
done through a QRCode and the dApp authentication service. 

## Connect Fragment

The dApp displays a QR code which is scanned by the Connect Fragment when the user clicks the FAB.

#### QR Connect Data
```json
{
  "requestId": "nonce-one-time-password",
  "challenge": "challenge to be signed by the wallet",
  "origin": "current dApp Origin"
}
```

The challenge is signed and posted to the [connect endpoint](https://github.com/PhearZero/nest-fido2/blob/main/ARCHITECTURE.md#post-connectresponse).
If the signature is valid, both the dApp's browser and the wallet are granted a session. This session
can be used to add credentials to be used for future authentications. 

#### Example POST Body

```json
{
  "requestId": "nonce-one-time-password",
  "challenge": "challenge that was signed by the wallet",
  "signature": "base64URL",
  "wallet": "58 character public key"
}
```

Creating a credential is done using the FIDO2 REST endpoints. See the [Architecture](https://github.com/PhearZero/nest-fido2/blob/main/ARCHITECTURE.md#fido2-endpoints) 
of the authentication service for more information.