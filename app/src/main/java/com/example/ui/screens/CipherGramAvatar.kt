package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs

// Elegant, modern pastel/cyber colors for dynamic initials background
private val AvatarColors = listOf(
    Color(0xFF3B82F6), // Vibrant Blue
    Color(0xFF10B981), // Emerald Green
    Color(0xFF8B5CF6), // Purple
    Color(0xFFF43F5E), // Rose Pink
    Color(0xFFD946EF), // Fuchsia
    Color(0xFFF59E0B), // Amber/Orange
    Color(0xFF06B6D4), // Cyan
    Color(0xFFEC4899), // Hot Pink
    Color(0xFF14B8A6)  // Teal
)

@Composable
fun CipherGramAvatar(
    avatarUrl: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val cleanName = name.trim()
    val isDefaultOrEmpty = avatarUrl.isNullOrBlank() || 
            avatarUrl.contains("photo-1535713875002-d1d0cf377fde") || // Default male photo
            avatarUrl.contains("photo-1472099645785-5658abf4ff4e")    // Any other common templates

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF1E1E2E)) // Fallback dark surface
    ) {
        if (isDefaultOrEmpty) {
            // Draw stunning initials avatar
            val initials = getInitials(cleanName)
            val backgroundColor = getAvatarColor(cleanName)
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp, // Dynamic size matching the container
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            // Load real avatar image from network/storage
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$name Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Extracts 1 or 2 capitalized letters for the initials avatar.
 * Examples:
 *   "Alysha Javed" -> "AJ"
 *   "_alyshajaved" -> "A"
 *   "alysha" -> "A"
 */
private fun getInitials(name: String): String {
    if (name.isEmpty()) return "?"
    
    // Clean symbols or underscores from name start for better initials (e.g. "_alyshajaved" -> "alyshajaved")
    val clean = name.dropWhile { !it.isLetterOrDigit() }
    if (clean.isEmpty()) return name.take(1).uppercase()

    val parts = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (parts.size >= 2) {
        val first = parts[0].take(1).uppercase()
        val second = parts[1].take(1).uppercase()
        first + second
    } else {
        clean.take(2).uppercase()
    }
}

/**
 * Deterministically generates a beautiful color based on the hash of the user's name.
 */
private fun getAvatarColor(name: String): Color {
    if (name.isEmpty()) return AvatarColors[0]
    val index = abs(name.hashCode()) % AvatarColors.size
    return AvatarColors[index]
}
