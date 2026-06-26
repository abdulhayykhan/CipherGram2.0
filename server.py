# -*- coding: utf-8 -*-
"""
CipherGram 2.0 - Companion Signaling & Routing Backend Service
Implemented with FastAPI, WebSockets, and Firebase Cloud Messaging (FCM).

This backend serves as a blind router. It:
1. Stores user public pre-key bundles (Identity Key, Signed Pre-Key, OPKs) for out-of-band X3DH handshake execution.
2. Relays ASCII-armored encrypted end-to-end envelopes over WebSockets or HTTP REST without ever being able to decrypt or view the payload content.
3. Automatically triggers FCM wake-up pings (action: WAKE_SYNC) to alert sleeping clients to poll the pending queue.
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, status, Header, Depends
from pydantic import BaseModel
from typing import Dict, Optional, List
import logging
import json
import firebase_admin
from firebase_admin import credentials, messaging, firestore, app_check

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("CipherGramBackend")

# Initialize Firebase Admin SDK
try:
    firebase_admin.initialize_app()
    logger.info("Firebase Admin SDK successfully initialized.")
except Exception as e:
    logger.warning(f"Could not initialize Firebase Admin SDK with default credentials: {e}.")

# Initialize Firestore client
db = firestore.client()

def verify_app_check_token(x_firebase_appcheck: Optional[str] = Header(None, alias="X-Firebase-AppCheck")):
    if not x_firebase_appcheck:
        logger.error("Missing X-Firebase-AppCheck header")
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Forbidden: Invalid App Check Token"
        )
    try:
        app_check.verify_token(x_firebase_appcheck)
    except Exception as e:
        logger.error(f"App Check token verification failed: {e}")
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Forbidden: Invalid App Check Token"
        )

app = FastAPI(
    title="CipherGram Signaling Server",
    description="A blind routing, key exchange pre-key bundle coordinator, and FCM notifier for CipherGram E2EE client.",
    version="2.0.0"
)

# Active connections map (Python WebSocket objects are kept in RAM as they cannot be serialized)
active_connections: Dict[str, WebSocket] = {}

class PreKeyBundleModel(BaseModel):
    identityKeyBase64: str
    signedPreKeyBase64: str
    signedPreKeySignature: str
    oneTimePreKeyBase64: Optional[str] = None
    oneTimePreKeyId: Optional[str] = None

class UserProfileModel(BaseModel):
    username: str
    fullName: str
    avatarUrl: str
    preKeyBundle: PreKeyBundleModel

class MessageEnvelopeModel(BaseModel):
    threadId: str
    sender: str
    recipient: str
    payload: str
    timestamp: int
    isEncrypted: bool

class DeviceTokenModel(BaseModel):
    username: str
    fcm_token: str

@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "CipherGram Blind Signaling Server with Firestore Persistence",
        "active_ws_connections": len(active_connections)
    }

@app.post("/api/register", status_code=status.HTTP_201_CREATED, dependencies=[Depends(verify_app_check_token)])
def register_user(profile: UserProfileModel):
    username_clean = profile.username.lower().strip()
    
    # Save profile to Firestore
    profile_data = {
        "username": username_clean,
        "fullName": profile.fullName,
        "avatarUrl": profile.avatarUrl
    }
    db.collection("user_profiles").document(username_clean).set(profile_data)
    
    # Save pre-key bundle to Firestore
    bundle_data = profile.preKeyBundle.dict()
    db.collection("pre_key_bundles").document(username_clean).set(bundle_data)
    
    logger.info(f"Registered user: {username_clean} with identity public key: {profile.preKeyBundle.identityKeyBase64[:16]}...")
    return {"status": "success", "message": f"User {username_clean} registered successfully"}

@app.post("/api/register_device")
@app.post("/register_device")
def register_device(device: DeviceTokenModel):
    username_clean = device.username.lower().strip()
    # Save FCM token to Firestore
    db.collection("device_tokens").document(username_clean).set({"fcm_token": device.fcm_token})
    logger.info(f"Registered device FCM token for user: {username_clean}")
    return {"status": "success", "message": f"Device registered successfully for user {username_clean}"}

@app.get("/api/bundle/{username}", response_model=PreKeyBundleModel)
def get_user_bundle(username: str):
    username_clean = username.lower().strip()
    doc = db.collection("pre_key_bundles").document(username_clean).get()
    if not doc.exists:
        logger.warning(f"Pre-key bundle query failed: user {username_clean} not found")
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {username_clean} pre-key bundle not registered yet"
        )
    return doc.to_dict()

@app.get("/api/profile/{username}")
def get_user_profile(username: str):
    username_clean = username.lower().strip()
    doc = db.collection("user_profiles").document(username_clean).get()
    if not doc.exists:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {username_clean} not found"
        )
    return doc.to_dict()

@app.get("/api/users")
def get_all_profiles():
    docs = db.collection("user_profiles").stream()
    return [doc.to_dict() for doc in docs]

@app.post("/api/dispatch", dependencies=[Depends(verify_app_check_token)])
async def dispatch_envelope(envelope: MessageEnvelopeModel):
    recipient_clean = envelope.recipient.lower().strip()
    logger.info(f"Dispatching envelope from '{envelope.sender}' to '{recipient_clean}' (encrypted: {envelope.isEncrypted})")
    
    # Check if recipient has a live WebSocket connection
    if recipient_clean in active_connections:
        ws = active_connections[recipient_clean]
        try:
            await ws.send_text(json.dumps(envelope.dict()))
            logger.info(f"Envelope successfully routed live over active WebSocket connection for: {recipient_clean}")
            return {"status": "success", "routed": "live_websocket"}
        except Exception as e:
            logger.error(f"Failed to route envelope to active WebSocket for {recipient_clean}: {e}")
            if recipient_clean in active_connections:
                del active_connections[recipient_clean]
                
    # If the recipient is offline, queue the message envelope in Firestore
    envelope_data = envelope.dict()
    db.collection("users").document(recipient_clean).collection("pending_envelopes").add(envelope_data)
    logger.info(f"Recipient '{recipient_clean}' is offline. Saved envelope to Firestore sub-collection.")
    
    # Send wake-up notification over FCM
    token_doc = db.collection("device_tokens").document(recipient_clean).get()
    if token_doc.exists:
        fcm_token = token_doc.to_dict().get("fcm_token")
        if fcm_token:
            try:
                msg = messaging.Message(
                    data={
                        "action": "WAKE_SYNC",
                        "threadId": envelope.threadId,
                        "sender": envelope.sender
                    },
                    token=fcm_token
                )
                resp = messaging.send(msg)
                logger.info(f"Successfully sent WAKE_SYNC FCM wake-up ping to {recipient_clean}. ID: {resp}")
            except Exception as e:
                logger.error(f"Failed to dispatch FCM wake-up ping to {recipient_clean}: {e}")
    else:
        logger.warning(f"No FCM token registered for offline recipient '{recipient_clean}'")

    return {"status": "success", "routed": "queued_offline_fcm_pinged"}

@app.websocket("/ws/{username}")
async def websocket_endpoint(websocket: WebSocket, username: str):
    username_clean = username.lower().strip()
    
    # Extract and verify App Check token from WebSocket handshake headers
    token = websocket.headers.get("x-firebase-appcheck")
    if not token:
        logger.error(f"WebSocket handshake missing X-Firebase-AppCheck header for {username_clean}")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Forbidden: Invalid App Check Token")
        return
        
    try:
        app_check.verify_token(token)
    except Exception as e:
        logger.error(f"WebSocket App Check token verification failed for {username_clean}: {e}")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Forbidden: Invalid App Check Token")
        return

    await websocket.accept()
    active_connections[username_clean] = websocket
    logger.info(f"WebSocket tunnel opened for active subscriber: {username_clean}")
    
    # Immediately flush and deliver any pending queued envelopes from Firestore
    try:
        pending_ref = db.collection("users").document(username_clean).collection("pending_envelopes")
        pending_docs = list(pending_ref.stream())
        if pending_docs:
            logger.info(f"Flushing and draining {len(pending_docs)} pending envelopes from Firestore for: {username_clean}")
            for doc in pending_docs:
                env_data = doc.to_dict()
                try:
                    await websocket.send_text(json.dumps(env_data))
                    pending_ref.document(doc.id).delete()
                except Exception as send_err:
                    logger.error(f"Failed to send pending envelope {doc.id} to {username_clean}: {send_err}")
                    break
            logger.info(f"Drained all envelopes successfully for: {username_clean}")
    except Exception as e:
        logger.error(f"Error flushing pending envelopes from Firestore: {e}")

    try:
        while True:
            # Receive standard framing
            data = await websocket.receive_text()
            logger.info(f"Received WebSocket packet from {username_clean}: {data}")
            
            # Simple routing loop driven via WebSocket
            try:
                payload = json.loads(data)
                if "recipient" in payload:
                    recipient = payload["recipient"].lower().strip()
                    if recipient in active_connections:
                        await active_connections[recipient].send_text(data)
                        logger.info(f"Re-routed WebSocket frame from {username_clean} to {recipient}")
                    else:
                        # Queue message in Firestore if recipient is offline
                        db.collection("users").document(recipient).collection("pending_envelopes").add(payload)
                        logger.info(f"Recipient '{recipient}' is offline. Saved frame to Firestore pending_envelopes.")
                        
                        token_doc = db.collection("device_tokens").document(recipient).get()
                        if token_doc.exists:
                            fcm_token = token_doc.to_dict().get("fcm_token")
                            if fcm_token:
                                try:
                                    msg = messaging.Message(
                                        data={
                                            "action": "WAKE_SYNC",
                                            "threadId": payload.get("threadId", f"thread_{username_clean}"),
                                            "sender": username_clean
                                        },
                                        token=fcm_token
                                    )
                                    messaging.send(msg)
                                except Exception as fcm_err:
                                    logger.error(f"WebSocket routing FCM error: {fcm_err}")
                        
                        await websocket.send_text(json.dumps({
                            "status": "info",
                            "message": f"User {recipient} is currently offline. Frame queued and FCM wake-up triggered."
                        }))
            except Exception as e:
                await websocket.send_text(json.dumps({"echo": data, "sender": username_clean}))
    except WebSocketDisconnect:
        logger.info(f"WebSocket connection closed for subscriber: {username_clean}")
    finally:
        if username_clean in active_connections:
            del active_connections[username_clean]
