package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    
    var isStartingTunnel by remember { mutableStateOf(false) }
    var currentStepText by remember { mutableStateOf("") }
    var currentProgress by remember { mutableStateOf(0f) }

    val instagramGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF833AB4), // Purple
            Color(0xFFF77737), // Orange
            Color(0xFFE1306C)  // Pink
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // CipherGram Branding Logo
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(instagramGradient, RoundedCornerShape(28.dp))
                    .padding(2.5.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                    contentDescription = "CipherGram Logo",
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CipherGram 2.0",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = OnSurfaceColor,
                letterSpacing = 1.sp
            )

            Text(
                text = "End-to-End Encrypted Instagram Client",
                fontSize = 14.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!isStartingTunnel) {
                // Fields Panel
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Instagram Username") },
                    placeholder = { Text("e.g. alex_priv") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = CyberPrimary,
                        cursorColor = CyberPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input")
                        .padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Alex Riverstone") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = CyberPrimary,
                        cursorColor = CyberPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fullname_input")
                        .padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Instagram Password") },
                    placeholder = { Text("Password verified locally only") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPrimary,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = CyberPrimary,
                        cursorColor = CyberPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input")
                        .padding(bottom = 24.dp)
                )

                Button(
                    onClick = {
                        if (username.isNotEmpty()) {
                            isStartingTunnel = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = username.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock icon",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Initialize Secure Session",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                // Interactive cryptographic sequence diagram / loading tunnel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            progress = { currentProgress },
                            color = CyberSecondary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "SECURE TUNNEL PIPELINE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberSecondary,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        AnimatedContent(
                            targetState = currentStepText,
                            transitionSpec = {
                                slideInVertically { height -> height } + fadeIn() togetherWith
                                        slideOutVertically { height -> -height } + fadeOut()
                            },
                            label = "step_text"
                        ) { text ->
                            Text(
                                text = text,
                                fontSize = 15.sp,
                                color = OnSurfaceColor,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.height(48.dp)
                            )
                        }

                        // Code-like debug output console below progress bar
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .border(1.dp, Color(0xFF1E1E24), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "CIPHER_INIT: SECURE_CONTAINER_OK\nKEYS_DIR: AndroidKeyStore\nCURVE: secp256r1 (NIST-P256)\nCIPHER: AES/GCM/NoPadding (128bit Tag)\nSTATUS: Pipeline running...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Run simulated handshake steps
                LaunchedEffect(Unit) {
                    val steps = listOf(
                        "1. Querying Android Keystore providers..." to 0.15f,
                        "2. Generating Elliptic Curve identity keys (secp256r1)..." to 0.40f,
                        "3. Storing cryptographic keypairs locally in hardware secure element..." to 0.65f,
                        "4. Authenticating session token with Instagram Messager gateway..." to 0.85f,
                        "5. Securing E2EE tunnels with active contacts..." to 1.0f
                    )

                    for (step in steps) {
                        currentStepText = step.first
                        val targetVal = step.second
                        while (currentProgress < targetVal) {
                            currentProgress += 0.05f
                            delay(50)
                        }
                        delay(600)
                    }

                    onLoginSuccess(username, fullName)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Security disclaimer badge
            Row(
                modifier = Modifier
                    .background(Color(0xFF151517), RoundedCornerShape(12.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Key Icon",
                    tint = CyberSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "No private keys are ever shared. All encryption occurs locally on-device.",
                    fontSize = 11.sp,
                    color = TextGray,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
