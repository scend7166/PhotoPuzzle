package com.photopuzzle.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.photopuzzle.app.data.models.PUZZLE_SIZES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartGame: (imageUri: String, pieceCount: Int) -> Unit,
    onViewStats: () -> Unit
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPieceCount by remember { mutableIntStateOf(25) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedImageUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Puzzle", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onViewStats) {
                        Icon(Icons.Default.BarChart, contentDescription = "Stats")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Image picker area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to choose a photo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Random photo button
            OutlinedButton(
                onClick = { pickRandomPhoto(context) { selectedImageUri = it } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick Random Photo")
            }

            // Piece count selector
            Text(
                "Puzzle Size",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            PieceSizeSelector(
                sizes = PUZZLE_SIZES,
                selected = selectedPieceCount,
                onSelect = { selectedPieceCount = it }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Start button
            Button(
                onClick = {
                    selectedImageUri?.let { uri ->
                        onStartGame(uri.toString(), selectedPieceCount)
                    }
                },
                enabled = selectedImageUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Puzzle", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PieceSizeSelector(sizes: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val chunked = sizes.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunked.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { size ->
                    val isSelected = size == selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(size) },
                        label = { Text("$size pieces") },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining slots if row is not full
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Picks a random image from the device's MediaStore.
 */
private fun pickRandomPhoto(context: android.content.Context, onResult: (Uri?) -> Unit) {
    try {
        val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
        val cursor = context.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, null
        )
        cursor?.use {
            if (it.count == 0) { onResult(null); return }
            val randomPos = (0 until it.count).random()
            it.moveToPosition(randomPos)
            val id = it.getLong(it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID))
            val uri = Uri.withAppendedPath(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            onResult(uri)
        } ?: onResult(null)
    } catch (e: Exception) {
        onResult(null)
    }
}
