# CipherGram2.0

An end-to-end encrypted (E2EE) messaging client built for Instagram Direct Messages, featuring native media parsing, OS-level share sheet integration, and a dedicated signaling backend for secure key exchange.

## Architecture

* **Framework**: Native Android (Kotlin & Jetpack Compose).
* **Database**: Android Room (SQLite) for encrypted local caching.
* **Signaling**: Production-grade FastAPI backend with WebSocket-based blind routing.
* **Security**: Hardware-backed Android Keystore (ECDH/secp256r1) for all cryptographic operations.

## Features

* **X3DH Key Agreement**: Implements the Extended Triple Diffie-Hellman protocol for secure, forward-secrecy-compliant key exchange.
* **Native Previews**: Direct, in-app native extraction and rendering of Instagram Posts and Reels.
* **OS-Level Share Sheet**: Deeply integrated into native Android `SEND` intents for instant sharing from the official Instagram app.
* **FCM Wake-Up Pipeline**: Uses silent Firebase Cloud Messaging `WAKE_SYNC` pings to maintain connectivity and sync pending envelopes when the app is backgrounded.
* **Hardware Security**: Asymmetric identity keys are generated and stored strictly within the device's hardware TEE/SE boundary.

## Companion Signaling Server

The backend acts as a blind relay. It stores X3DH pre-key bundles in **Cloud Firestore** and routes ASCII-armored encrypted envelopes via WebSockets or FCM triggers.

**Deployment**:
Deployed to **Google Cloud Run** with a single-instance constraint to ensure consistent WebSocket message ordering.

## Deployment Strategy

1. **Backend**: Containerized via `Dockerfile` and deployed to Google Cloud Run.
2. **Android Release**: Compiled via `android-release.yml` GitHub Actions pipeline.
3. **Secrets**: Signing is handled via GitHub Secrets (`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_PASSWORD`).

## Local Data Models

* `UserKeyPairEntity`: Local user's identity keys (stored via hardware-backed Keystore).
* `ContactKeyEntity`: Cached public identity keys of chat contacts.
* `ChatEntity`: Stores conversation threads, metadata, and E2EE state.
* `MessageEntity`: Stores decrypted cache and encrypted transport payloads.

## Project Structure

```text
CipherGram2.0/
├── app/src/main/java/com/example/
│   ├── cryptography/     # HardwareCryptoEngine (TEE/SE), PreKeyBundleProtocol, E2EECryptoEngine
│   ├── database/         # CipherGramDatabase, Dao, Repository (SQLite)
│   ├── network/          # MessagingGateway (WebSocket/FCM), CipherGramFCMService
│   ├── ui/screens/       # ChatList, ChatThread, Login, SecuritySettings, ShareDispatchSheet
│   └── viewmodel/        # CipherGramViewModel (MVVM state management)
├── .github/workflows/    # CI/CD (android-release.yml)
├── server.py             # FastAPI Signaling Backend (Firestore + WebSockets)
└── Dockerfile            # Cloud Run containerization

```

## 📄 License

This project is open-source and available under the MIT License.

---

**Made with ❤️ by [Abdul Hayy Khan**](https://www.google.com/search?q=https://www.linkedin.com/in/abdulhayykhan)
