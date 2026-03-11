package com.photopuzzle.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedImageUri = uri }

    // Camera: create a temp file URI before launching so we have somewhere to write the photo
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) selectedImageUri = cameraImageUri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission required") }
        }
    }

    fun onTakePhoto() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = createCameraImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Permission needed to read MediaStore on Android 12 and below
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    // Launched when permission is granted — immediately picks a random photo
    val randomPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickRandomPhoto(context) { uri ->
                if (uri != null) selectedImageUri = uri
                else scope.launch { snackbarHostState.showSnackbar("No photos found on device") }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Permission required to access photos") }
        }
    }

    fun onPickRandom() {
        val already = ContextCompat.checkSelfPermission(context, storagePermission) ==
                PackageManager.PERMISSION_GRANTED
        if (already) {
            pickRandomPhoto(context) { uri ->
                if (uri != null) selectedImageUri = uri
                else scope.launch { snackbarHostState.showSnackbar("No photos found on device") }
            }
        } else {
            randomPhotoPicker.launch(storagePermission)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            // Image preview — display only, not clickable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        RoundedCornerShape(16.dp)
                    ),
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
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Photo source buttons — evenly spaced, icon only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 1. Choose from gallery
                OutlinedIconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Choose a photo")
                }
                // 2. Take a picture
                OutlinedIconButton(onClick = { onTakePhoto() }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Take a picture")
                }
                // 3. Random photo
                OutlinedIconButton(onClick = { onPickRandom() }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Select random photo")
                }
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

/** Creates a temp file URI via FileProvider for the camera to write into. */
private fun createCameraImageUri(context: android.content.Context): Uri {
    val file = File.createTempFile("camera_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
