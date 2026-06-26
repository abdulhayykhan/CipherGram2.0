# -*- coding: utf-8 -*-
"""
CipherGram 2.0 - Companion Signaling & Routing Backend Service
Implemented with FastAPI, WebSockets, and Firebase Cloud Messaging (FCM).

This backend serves as a blind router. It:
1. Stores user public pre-key bundles (Identity Key, Signed Pre-Key, OPKs) for out-of-band X3DH handshake execution.
2. Relays ASCII-armored encrypted end-to-end envelopes over WebSockets or HTTP REST without ever being able to decrypt or view the payload content.
3. Automatically triggers FCM wake-up pings (action: WAKE_SYNC) to alert sleeping clients to poll the pending queue.
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, status
from pydantic import BaseModel
from typing import Dict, Optional, List
import logging
import json
import firebase_admin
from firebase_admin import credentials, messaging

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("CipherGramBackend")

# Initialize Firebase Admin SDK
try:
    firebase_admin.initialize_app()
    logger.info("Firebase Admin SDK successfully initialized.")
except Exception as e:
    logger.warning(f"Could not initialize Firebase Admin SDK with default credentials: {e}. Running with mock FCM support.")

app = FastAPI(
    title="CipherGram Signaling Server",
    description="A blind routing, key exchange pre-key bundle coordinator, and FCM notifier for CipherGram E2EE client.",
    version="2.0.0"
)

# In-memory storage for user public profiles, pre-key bundles, device FCM tokens, and offline envelope queues
user_prekey_bundles: Dict[str, dict] = {}
user_profiles: Dict[str, dict] = {}
active_connections: Dict[str, WebSocket] = {}
device_tokens: Dict[str, str] = {}
pending_envelopes: Dict[str, List[dict]] = {}

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
        "service": "CipherGram Blind Signaling Server",
        "active_users_count": len(user_profiles),
        "active_ws_connections": len(active_connections),
        "registered_devices_count": len(device_tokens)
    }

@app.post("/api/register", status_code=status.HTTP_201_CREATED)
def register_user(profile: UserProfileModel):
    username_clean = profile.username.lower().strip()
    user_profiles[username_clean] = {
        "username": username_clean,
        "fullName": profile.fullName,
        "avatarUrl": profile.avatarUrl
    }
    user_prekey_bundles[username_clean] = profile.preKeyBundle.dict()
    logger.info(f"Registered user: {username_clean} with identity public key: {profile.preKeyBundle.identityKeyBase64[:16]}...")
    return {"status": "success", "message": f"User {username_clean} registered successfully"}

@app.post("/api/register_device")
@app.post("/register_device")
def register_device(device: DeviceTokenModel):
    username_clean = device.username.lower().strip()
    device_tokens[username_clean] = device.fcm_token
    logger.info(f"Registered device FCM token for user: {username_clean}")
    return {"status": "success", "message": f"Device registered successfully for user {username_clean}"}

@app.get("/api/bundle/{username}", response_model=PreKeyBundleModel)
def get_user_bundle(username: str):
    username_clean = username.lower().strip()
    if username_clean not in user_prekey_bundles:
        logger.warning(f"Pre-key bundle query failed: user {username_clean} not found")
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {username_clean} pre-key bundle not registered yet"
        )
    return user_prekey_bundles[username_clean]

@app.get("/api/profile/{username}")
def get_user_profile(username: str):
    username_clean = username.lower().strip()
    if username_clean not in user_profiles:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {username_clean} not found"
        )
    return user_profiles[username_clean]

@app.get("/api/users")
def get_all_profiles():
    return list(user_profiles.values())

@app.post("/api/dispatch")
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
            del active_connections[recipient_clean]
            
    # If the recipient is offline, queue the message envelope
    if recipient_clean not in pending_envelopes:
        pending_envelopes[recipient_clean] = []
    pending_envelopes[recipient_clean].append(envelope.dict())
    logger.info(f"Recipient '{recipient_clean}' is offline. Envelope cached in pending queue. Queue size: {len(pending_envelopes[recipient_clean])}")
    
    # Send wake-up notification over FCM
    fcm_token = device_tokens.get(recipient_clean)
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
    await websocket.accept()
    active_connections[username_clean] = websocket
    logger.info(f"WebSocket tunnel opened for active subscriber: {username_clean}")
    
    # Immediately flush and deliver any pending queued envelopes
    if username_clean in pending_envelopes and pending_envelopes[username_clean]:
        queued_count = len(pending_envelopes[username_clean])
        logger.info(f"Flushing and draining {queued_count} pending envelopes for: {username_clean}")
        try:
            for env in pending_envelopes[username_clean]:
                await websocket.send_text(json.dumps(env))
            pending_envelopes[username_clean] = []
            logger.info(f"Drained all envelopes successfully for: {username_clean}")
        except Exception as e:
            logger.error(f"Failed to deliver pending envelopes to {username_clean}: {e}")

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
                        # Queue message if recipient is offline
                        if recipient not in pending_envelopes:
                            pending_envelopes[recipient] = []
                        pending_envelopes[recipient].append(payload)
                        
                        fcm_token = device_tokens.get(recipient)
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
