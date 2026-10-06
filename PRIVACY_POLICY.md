# Privacy Policy — VaultXRP Wallet

**Effective date:** October 6, 2026
**App:** VaultXRP (Android, package `com.xrpwallet.myapp`)

VaultXRP is a non-custodial wallet for the XRP Ledger (XRPL). This policy explains what information the app handles, where it goes, and the choices you have. In short: **we do not collect, store, sell, or share your personal data.** Your wallet lives on your device.

## 1. Information we do not collect

VaultXRP has no user accounts, no sign-up, no analytics or advertising SDKs, no crash-reporting service, and no backend server operated by us. We do not collect:

- Your name, email address, phone number, or other contact details
- Your recovery phrase, seed, or private keys
- Your location
- Device identifiers or advertising IDs
- Usage analytics or behavioral data

## 2. Information stored only on your device

The app stores the following data locally, in app-private storage that other apps cannot read:

| Data | How it is protected |
| --- | --- |
| Recovery phrases / seeds and private keys | Encrypted with an AES-256-GCM key held in the Android Keystore. Never transmitted. |
| Wallet names, public addresses, public keys | App-private storage |
| Address book (contact names and XRPL addresses you add) | App-private storage |
| Settings (theme, auto-lock, hide balances) and pending transaction status | App-private storage |

Android cloud backup and device-to-device transfer are disabled for this app, so this data is not copied to Google Drive or another device. Uninstalling the app or deleting a wallet in the app permanently removes this data. Keep your recovery phrase written down offline. Without it, we cannot help you recover a lost wallet because we never have a copy.

### Encrypted backup files

If you choose to export a backup, the app encrypts it with a password you set (AES-256-GCM, key derived with PBKDF2-HMAC-SHA256, 310,000 iterations) and saves it where you choose. We do not receive or store backup files.

## 3. Device permissions

| Permission | Why it is used |
| --- | --- |
| **Camera** | Only to scan QR codes (such as a recipient address) when you open the scanner. QR codes are decoded on your device. No images or video are saved or sent anywhere. |
| **Biometric / fingerprint** | To confirm it's you before unlocking, signing, revealing, exporting, or deleting a wallet. Authentication is handled by Android. The app never receives your fingerprint or face data. |
| **Internet** | To read balances and submit transactions to the XRP Ledger, and to load market prices and token information (see Section 4). |

## 4. Network requests to third-party services

To function, the app connects directly from your device to the public services below. These requests do not include your name, recovery phrase, or private keys. Like any internet request, they reveal your device's IP address to the service, and blockchain requests include your **public** wallet address, which is already public on the ledger.

| Service | Purpose |
| --- | --- |
| XRPL public nodes (`xrplcluster.com`, `s1.ripple.com`, `s2.ripple.com`) | Read balances and history; submit transactions you have signed |
| CoinGecko (`api.coingecko.com`) | XRP price and chart data |
| XRPL Meta (`s1.xrplmeta.org`) | Token list and metadata |
| Bithomp CDN (`cdn.bithomp.com`) | Token icons |
| Flare network RPC (`flare-api.flare.network`, `rpc.ankr.com`) | Staking information, if you use the Stake feature |
| Upshift (`api.upshift.finance`) and DefiLlama (`api.llama.fi`) | Staking vault data, if you use the Stake feature |
| XRPL explorers (`livenet.xrpl.org` and test networks) | Opened in your browser only when you tap to view a transaction |

These services are operated by third parties under their own privacy policies. Transactions you submit are recorded on the public XRP Ledger. That is how blockchains work, and these records cannot be changed or deleted by anyone, including us.

All connections use HTTPS. Unencrypted traffic is blocked.

## 5. Data sharing and sale

We do not sell, rent, trade, or share any personal data, because we do not collect any.

## 6. Data retention and deletion

All app data stays on your device until you delete a wallet in the app, clear the app's storage, or uninstall the app. Data on the public XRP Ledger is permanent and outside our control.

## 7. Security

Sensitive operations require device authentication. Secrets are encrypted at rest with hardware-backed keys where your device supports them. No method of storage is perfectly secure, so protect your device with a screen lock and keep your recovery phrase offline.

## 8. Children's privacy

VaultXRP is a financial app and is not intended for anyone under 18. We do not knowingly collect information from children, and the app collects no personal information from any user.

## 9. Changes to this policy

If this policy changes, we will update this page and the effective date above. Significant changes will be noted in the app's release notes.

## 10. Contact

Questions about this policy or the app: <https://website-three-rho-99.vercel.app/support>
