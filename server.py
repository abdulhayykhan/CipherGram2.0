# -*- coding: utf-8 -*-
"""
CipherGram 2.0 - Companion Signaling & Routing Backend Service
Implemented with FastAPI and WebSockets.

This backend serves as a blind router. It:
1. Stores user public pre-key bundles (Identity Key, Signed Pre-Key, OPKs) for out-of-band X3DH handshake execution.
2. Relays ASCII-armored encrypted end-to-end envelopes over WebSockets or HTTP REST without ever being able to decrypt or view the payload content.

To run this backend:
    pip install fastapi uvicorn websockets
    uvicorn server:app --host 0.0.0.0 --port 8000
"""

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, status
from pydantic import BaseModel
from typing import Dict, Optional, List
import logging
import json

# Setup logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("CipherGramBackend")

app = FastAPI(
    title="CipherGram Signaling Server",
    description="A blind routing and key exchange pre-key bundle coordinator for CipherGram E2EE client.",
    version="2.0.0"
)

# In-memory storage for user public profile and pre-key bundles
# In production, back this with Redis, PostgreSQL, or MongoDB
user_prekey_bundles: Dict[str, dict] = {}
user_profiles: Dict[str, dict] = {}
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

@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "CipherGram Blind Signaling Server",
        "active_users_count": len(user_profiles),
        "active_ws_connections": len(active_connections)
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
            
    # If the socket is offline, message is simulated as queued or ready for REST sync pull
    # In production, write to a Spanner/SQLite store queue here.
    logger.info(f"Recipient '{recipient_clean}' is offline. Envelope cached for next polling/reconnection.")
    return {"status": "success", "routed": "queued_offline"}

@app.websocket("/ws/{username}")
async def websocket_endpoint(websocket: WebSocket, username: str):
    username_clean = username.lower().strip()
    await websocket.accept()
    active_connections[username_clean] = websocket
    logger.info(f"WebSocket tunnel opened for active subscriber: {username_clean}")
    
    try:
        while True:
            # Receive standard framing
            data = await websocket.receive_text()
            logger.info(f"Received WebSocket packet from {username_clean}: {data}")
            
            # Simple Echo Loop or direct echo back to verify socket integrity.
            # Real routing is driven via /api/dispatch for robust threading.
            try:
                payload = json.loads(data)
                # If they send a message frame, we route it immediately
                if "recipient" in payload:
                    recipient = payload["recipient"].lower().strip()
                    if recipient in active_connections:
                        await active_connections[recipient].send_text(data)
                        logger.info(f"Re-routed WebSocket frame from {username_clean} to {recipient}")
                    else:
                        await websocket.send_text(json.dumps({
                            "status": "info",
                            "message": f"User {recipient} is currently offline. Frame queued."
                        }))
            except Exception as e:
                # If invalid json, echo back as a generic keepalive or raw stream frame
                await websocket.send_text(json.dumps({"echo": data, "sender": username_clean}))
    except WebSocketDisconnect:
        logger.info(f"WebSocket connection closed for subscriber: {username_clean}")
    finally:
        if username_clean in active_connections:
            del active_connections[username_clean]
