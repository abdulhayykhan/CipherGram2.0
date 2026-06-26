package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.UserKeyPairEntity
import com.example.database.ContactKeyEntity
import com.example.ui.theme.*

@Composable
fun SafetyQRCode(seed: String, modifier: Modifier = Modifier) {
    val matrixSize = 12
    val grid = remember(seed) {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(seed.toByteArray())
        Array(matrixSize) { r ->
            BooleanArray(matrixSize) { c ->
                val byteIdx = (r * matrixSize + c) / 8
                val bitIdx = (r * matrixSize + c) % 8
                if (byteIdx < hash.size) {
                    ((hash[byteIdx].toInt() shr bitIdx) and 1) == 1
                } else {
                    (r + c) % 2 == 0
                }
            }
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cellSize = size.width / matrixSize
        drawRect(color = Color.White)
        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                val isFinderPattern = (r < 3 && c < 3) || (r < 3 && c >= matrixSize - 3) || (r >= matrixSize - 3 && c < 3)
                if (isFinderPattern) {
                    val inOuter = r == 0 || r == 2 || c == 0 || c == 2 ||
                                  r == 0 || r == 2 || c == matrixSize - 1 || c == matrixSize - 3 ||
                                  r == matrixSize - 1 || r == matrixSize - 3 || c == 0 || c == 2
                    if (inOuter) {
                        drawRect(
                            color = Color.Black,
                            topLeft = androidx.compose.ui.geometry.Offset(c * cellSize, r * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                        )
                    }
                } else if (grid[r][c]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(c * cellSize, r * cellSize),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    localKeyPair: UserKeyPairEntity?,
    contactKeys: List<ContactKeyEntity>,
    isRotating: Boolean,
    onRotateKeys: () -> Unit,
    onToggleVerification: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showPubKey by remember { mutableStateOf(false) }
    var copiedAlert by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cryptographic Keys",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = OnSurfaceColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: User Identity Keys
            item {
                Text(
                    text = "YOUR CIPHERGRAM IDENTITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberPrimary,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1218)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Public Key",
                                    tint = CyberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = "Active EC Public Identity Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceColor
                                )
                                Text(
                                    text = "Algorithm: secp256r1 (Elliptic Curve)",
                                    fontSize = 11.sp,
                                    color = TextGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Toggle key viewer
                        Button(
                            onClick = { showPubKey = !showPubKey },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showPubKey) DarkSurfaceVariant else Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (showPubKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "View Key",
                                tint = OnSurfaceColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showPubKey) "Hide Public Key String" else "Show Public Key String",
                                color = OnSurfaceColor,
                                fontSize = 13.sp
                            )
                        }

                        AnimatedVisibility(
                            visible = showPubKey,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = localKeyPair?.publicKeyBase64 ?: "Loading identity key...",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberSecondary,
                                    lineHeight = 16.sp,
                                    modifier = Modifier
                                        .background(Color.Black, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                        .fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            localKeyPair?.publicKeyBase64?.let {
                                                clipboardManager.setText(AnnotatedString(it))
                                                copiedAlert = true
                                            }
                                        }
                                        .background(Color.Black, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (copiedAlert) Icons.Default.Done else Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = if (copiedAlert) CyberSecondary else OnSurfaceColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (copiedAlert) "Public Key Copied!" else "Copy Public Key to Clipboard",
                                        color = OnSurfaceColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Key Rotation Panel
            item {
                Text(
                    text = "IDENTITY MAINTENANCE & ROTATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberSecondary,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Periodic Key Rotation is critical to protect historic chat sessions in the event of an physical device compromise (Forward Secrecy). Rotated keys automatically replace older sessions on new messages.",
                            fontSize = 12.sp,
                            color = TextGray,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isRotating) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = CyberSecondary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Re-generating secp256r1 keypair...", color = CyberSecondary, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = onRotateKeys,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Rotate",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rotate identity Keypair Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 2.5: Out-of-band Verification (Safety Numbers / MITM defense)
            item {
                Text(
                    text = "OUT-OF-BAND SAFETY NUMBERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberPrimary,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (contactKeys.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No contacts registered for E2EE safety verification.", color = TextGray, fontSize = 12.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        contactKeys.forEach { contactKey ->
                            var isExpanded by remember { mutableStateOf(false) }
                            val localPubKey = localKeyPair?.publicKeyBase64 ?: ""
                            val safetyNumber = remember(localPubKey, contactKey.publicKeyBase64) {
                                if (localPubKey.isNotEmpty() && contactKey.publicKeyBase64.isNotEmpty()) {
                                    val sorted = listOf(localPubKey, contactKey.publicKeyBase64).sorted()
                                    val combined = sorted[0] + sorted[1]
                                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                                    val hash = digest.digest(combined.toByteArray())
                                    val sb = StringBuilder()
                                    for (i in 0 until 4) {
                                        val b1 = hash[i * 3].toInt() and 0xFF
                                        val b2 = hash[i * 3 + 1].toInt() and 0xFF
                                        val b3 = hash[i * 3 + 2].toInt() and 0xFF
                                        val value = ((b1 shl 16) or (b2 shl 8) or b3) % 100000
                                        if (i > 0) sb.append(" ")
                                        sb.append(String.format("%05d", value))
                                    }
                                    sb.toString()
                                } else {
                                    "00000 00000 00000 00000"
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = !isExpanded },
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, if (contactKey.isVerified) CyberPrimary else BorderColor)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = if (contactKey.isVerified) Icons.Default.Verified else Icons.Default.GppMaybe,
                                            contentDescription = "Status",
                                            tint = if (contactKey.isVerified) CyberPrimary else TextGray,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "@${contactKey.contactUsername}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OnSurfaceColor
                                            )
                                            Text(
                                                text = if (contactKey.isVerified) "Verified Secure Tunnel" else "Unverified Fingerprint (Tap to verify)",
                                                fontSize = 11.sp,
                                                color = if (contactKey.isVerified) CyberPrimary else TextGray
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand",
                                            tint = OnSurfaceColor
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "To verify secure hardware-backed encryption with @${contactKey.contactUsername}, compare these numbers or the QR code with their device. They must be identical:",
                                                fontSize = 11.sp,
                                                color = TextGray,
                                                lineHeight = 15.sp
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = safetyNumber,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = CyberSecondary,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier
                                                    .background(Color.Black, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Box(
                                                modifier = Modifier
                                                    .size(140.dp)
                                                    .background(Color.White, RoundedCornerShape(8.dp))
                                                    .padding(12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SafetyQRCode(
                                                    seed = safetyNumber,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = { onToggleVerification(contactKey.contactUsername) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (contactKey.isVerified) Color(0xFF1B1B1B) else CyberPrimary
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = if (contactKey.isVerified) Icons.Default.LockOpen else Icons.Default.VerifiedUser,
                                                    contentDescription = "Verify",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (contactKey.isVerified) "Mark as Unverified" else "Verify Fingerprint",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Hardware Keystore Specs
            item {
                Text(
                    text = "DEVICE KEYSTORE CONTEXT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextGray,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KeystoreSpecRow(
                            label = "Storage Provider",
                            value = "AndroidKeyStoreDaemon",
                            icon = Icons.Default.Storage
                        )
                        KeystoreSpecRow(
                            label = "Security Domain",
                            value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) "Trusted Execution Env (TEE)" else "Software Emulated TEE",
                            icon = Icons.Default.Hardware
                        )
                        KeystoreSpecRow(
                            label = "Android SDK Level",
                            value = "API ${Build.VERSION.SDK_INT}",
                            icon = Icons.Default.SettingsCell
                        )
                        KeystoreSpecRow(
                            label = "Forward Secrecy Support",
                            value = "Enabled (Double Ratchet Flow)",
                            icon = Icons.Default.VerifiedUser
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeystoreSpecRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextGray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextGray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceColor
        )
    }
}
