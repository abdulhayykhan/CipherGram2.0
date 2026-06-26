# CipherGram2.0

An end-to-end encrypted (E2EE) messaging client built exclusively for Instagram Direct Messages, featuring inline media parsing and native OS share integration. 

## Architecture

* **Framework**: Native Android built with Kotlin and Jetpack Compose.
* **Database**: Room Database for secure, localized storage of cached message historical records.
* **State Management**: MVVM (Model-View-ViewModel) utilizing architectural decoupled data streams.
* **Security**: Client-side cryptography engines overriding insecure transmission layers with hardware-backed biometric checks.

## Features

* **End-to-End Encryption**: Robust local encryption layers mapping standard text wrappers seamlessly over network hooks via custom cryptographic pipelines.
* **Direct Previews**: In-app native extraction, parsing, and rendering architectures for modular processing of Instagram Posts and Reels.
* **OS-Level Share Sheet**: System-level integration targeting incoming external Send Intent registries for quick multi-contact sharing.
* **Cloud Infrastructure Sync**: Hybrid automated workflows handling state distribution through managed Firebase connection points.

## Security

Operates utilizing standard AndroidX Biometric cryptosystem pipelines coupled with secure asymmetric key processing engines (`E2EECryptoEngine`). Private key rings remain isolated entirely within the device's hardware Keystore boundary.

## Companion Signaling Server

A production-ready FastAPI backend is provided at the root (`server.py`) to manage out-of-band key exchanges (X3DH) and blind relaying of end-to-end encrypted message envelopes.

To run the signaling server locally:
```bash
pip install fastapi uvicorn websockets
uvicorn server:app --host 0.0.0.0 --port 8000
```

## Local Setup

**Prerequisites**: Android Studio, JDK 17+.

1. **Clone repository**:

```bash
git clone <repository-url>
cd CipherGram2.0
```

2. **Open Project**:
Launch Android Studio and select **Open**. Navigate to the `CipherGram2.0` directory.
3. **Sync Gradle**:
Allow Android Studio to download necessary AndroidX, Room, Firebase, and Compose dependencies.
4. **Run application**:
Connect a physical Android device via USB (enable USB debugging) or start an AVD emulator. Click **Run**.

## Deployment Strategy (CI/CD Pipeline)

1. **Automated Compilation**: The GitHub Actions workflow compiles both release and debug APK flavors concurrently.
2. **Graceful Fallback Signing**: Employs a robust `build.gradle.kts` validation sequence utilizing fallback debug keystores natively when production secrets are unavailable.
3. **Automated Release Notes**: Once completed, the build lifecycle outputs automated release tags containing high-contrast direct downlinks.

## Local Data Models

* `UserSession`: Controls individual auth session boundaries and active Instagram token states (persisted securely via local Preferences).
* `UserKeyPairEntity` (Room Entity): Stores the local user's E2EE public and private key pair mapped by Instagram username.
* `ContactKeyEntity` (Room Entity): Stores synced public keys of contacts.
* `ChatEntity` (Room Entity): Stores conversation threads, contact details, encryption status, and last message summary.
* `MessageEntity` (Room Entity): Stores chat messages, including transport layer payload (ciphertext/plaintext), decrypted cache, and shared Instagram media details (URLs, captions, thumbnails, and types).
* `CipherGramDao`: Exposes SQLite transaction layers wrapping target database channels.
* `E2EECryptoEngine`: Cryptographic engine running secure client-side asymmetric key processing operations.

## Project Structure

```text
CipherGram2.0/
├── .github/workflows/    # GitHub Actions android-release.yml compilation rules
├── app/src/main/java/com/example/
│   ├── cryptography/     # E2EECryptoEngine key processing operations
│   ├── database/         # Room schema declarations, entities, repositories
│   ├── ui/screens/       # Compose screens (ChatListScreen, ChatThreadScreen, LoginScreen, SecuritySettingsScreen, ShareDispatchSheet)
│   ├── ui/theme/         # System font, styling, color tokens
│   └── viewmodel/        # Unified state processors handling backend data bridges
├── app/src/main/res/     # Vector drawables and system XML configurations
└── build.gradle.kts      # Project module configurations 
```

## 📄 License

This project is open-source and available for educational and commercial use under the MIT License.

---

**Made with ❤️ by [Abdul Hayy Khan](https://www.google.com/search?q=https://www.linkedin.com/in/abdulhayykhan)**
