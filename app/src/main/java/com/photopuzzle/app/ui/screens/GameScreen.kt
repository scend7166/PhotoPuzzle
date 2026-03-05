package com.photopuzzle.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.photopuzzle.app.game.GameViewModel
import com.photopuzzle.app.game.TablePiece
import com.photopuzzle.app.game.TrayPiece

private val WoodDark  = Color(0xFF5D3A1A)
private val WoodMid   = Color(0xFF7B4F2E)
private val WoodGrain = Color(0xFF6B4226)
private val TrayBg    = Color(0xFF1E1E2E)
private val TrayGrid  = Color(0xFF2A2A3E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    imageUri: String,
    pieceCount: Int,
    onGameComplete: () -> Unit,
    onQuit: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var tableWidthPx  by remember { mutableFloatStateOf(0f) }
    var trayPiecePx   by remember { mutableFloatStateOf(0f) }

    // Root Y offset of the outer Box — used to convert local pointer coords → root coords
    var boxRootY by remember { mutableFloatStateOf(0f) }

    // Once we know aspect ratio from ViewModel, update tableBottomY
    val aspectRatio = state.imageAspectRatio
    LaunchedEffect(tableWidthPx, aspectRatio) {
        if (tableWidthPx > 0 && aspectRatio > 0) {
            val tableHeightPx = tableWidthPx / aspectRatio
            viewModel.tableBottomY = viewModel.tableTopY + tableHeightPx
        }
    }

    LaunchedEffect(tableWidthPx, trayPiecePx) {
        if (tableWidthPx > 0 && trayPiecePx > 0) {
            viewModel.initGame(imageUri, pieceCount, tableWidthPx, trayPiecePx)
        }
    }

    if (state.isSolved) {
        PuzzleSolvedDialog(
            elapsedSeconds = state.elapsedSeconds,
            pieceCount = pieceCount,
            onDismiss = onGameComplete
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatTime(state.elapsedSeconds), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    Text("$pieceCount pieces", modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onQuit) {
                        Icon(Icons.Default.Close, contentDescription = "Quit")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Capture this Box's own position in root coords once laid out
                .onGloballyPositioned { coords ->
                    boxRootY = coords.positionInRoot().y
                }
                // Single gesture handler — converts local → root by adding boxRootY
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startRootY = down.position.y + boxRootY
                        viewModel.onGlobalDragStart(down.position.x, startRootY)

                        drag(down.id) { change ->
                            @Suppress("DEPRECATION")
                            change.consumeAllChanges()
                            val rootY = change.position.y + boxRootY
                            viewModel.onGlobalDragMove(change.position.x, rootY)
                        }

                        val up = currentEvent.changes.firstOrNull()
                        val finalRootY = (up?.position?.y ?: down.position.y) + boxRootY
                        val finalX = up?.position?.x ?: down.position.x
                        viewModel.onGlobalDragEnd(finalX, finalRootY)
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Assembly Table — sized to match image aspect ratio ─────────
                val tableHeightDp = if (tableWidthPx > 0 && aspectRatio > 0)
                    with(LocalDensity.current) { (tableWidthPx / aspectRatio).toDp() }
                else 0.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (tableHeightDp > 0.dp) Modifier.height(tableHeightDp)
                            else Modifier.weight(1f)  // fallback before aspect ratio known
                        )
                        .onGloballyPositioned { coords ->
                            tableWidthPx = coords.size.width.toFloat()
                            val rootY = coords.positionInRoot().y
                            viewModel.tableTopY    = rootY
                            viewModel.tableBottomY = rootY + coords.size.height
                        }
                ) {
                    when {
                        state.isLoading -> Box(
                            Modifier.fillMaxSize().background(WoodMid),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = Color.White) }

                        state.errorMessage != null -> Box(
                            Modifier.fillMaxSize().background(WoodMid),
                            contentAlignment = Alignment.Center
                        ) { Text(state.errorMessage!!, color = Color.White) }

                        else -> TableCanvas(
                            tablePieces = state.tablePieces,
                            cols = state.cols,
                            rows = state.rows,
                            pieceW = state.pieceW,
                            pieceH = state.pieceH,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF3E2010), thickness = 3.dp)

                // ── Piece Tray ─────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(TrayBg)
                        .onGloballyPositioned { coords ->
                            val rootY = coords.positionInRoot().y
                            viewModel.trayTopY = rootY
                            if (trayPiecePx == 0f) {
                                trayPiecePx = coords.size.width.toFloat() / 5f
                            }
                        }
                ) {
                    when {
                        state.isLoading -> Unit
                        state.trayPieces.isEmpty() -> Box(
                            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                        ) { Text("All pieces on the board!", color = Color.White.copy(alpha = 0.5f)) }
                        else -> TrayGrid(pieces = state.trayPieces)
                    }
                }
            }

            // ── Floating drag ghost — scaled to table piece size ──────────────
            val drag = state.drag
            if (drag != null && drag.displayW > 0f && drag.displayH > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        val x = drag.fingerPosition.x - drag.bitmapOffset.x
                        val y = drag.fingerPosition.y - drag.bitmapOffset.y - boxRootY
                        val scaleX = drag.displayW / drag.bitmap.width
                        val scaleY = drag.displayH / drag.bitmap.height
                        val matrix = android.graphics.Matrix().apply {
                            postScale(scaleX, scaleY)
                            postTranslate(x, y)
                        }
                        val paint = android.graphics.Paint().apply { alpha = 210 }
                        canvas.nativeCanvas.drawBitmap(drag.bitmap, matrix, paint)
                    }
                }
            }
        }
    }
}

// ── Table Canvas ─────────────────────────────────────────────────────────────

@Composable
fun TableCanvas(
    tablePieces: List<TablePiece>,
    cols: Int,
    rows: Int,
    pieceW: Float,
    pieceH: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRect(color = WoodMid)
        val lineCount = (size.height / 18f).toInt()
        for (i in 0..lineCount) {
            val y = i * 18f + (i % 3) * 2f
            drawLine(WoodGrain.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y),
                strokeWidth = if (i % 4 == 0) 2.5f else 1f)
        }
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * pieceW; val y = row * pieceH
                drawRect(Color.Black.copy(alpha = 0.28f),
                    topLeft = Offset(x + 3f, y + 3f), size = Size(pieceW - 6f, pieceH - 6f))
                drawRect(WoodDark.copy(alpha = 0.55f),
                    topLeft = Offset(x + 2f, y + 2f), size = Size(pieceW - 4f, pieceH - 4f))
            }
        }
        tablePieces.forEach { piece ->
            drawIntoCanvas { canvas ->
                // Scale shaped bitmap from bitmap-space to table-space
                val scaleX = pieceW / piece.shape.bitmapPieceW
                val scaleY = pieceH / piece.shape.bitmapPieceH
                val matrix = android.graphics.Matrix().apply {
                    postScale(scaleX, scaleY)
                    postTranslate(piece.position.x, piece.position.y)
                }
                canvas.nativeCanvas.drawBitmap(piece.shape.shapedBitmap, matrix, null)

            }
        }
    }
}

// ── Tray Grid ─────────────────────────────────────────────────────────────────

@Composable
fun TrayGrid(pieces: List<TrayPiece>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true
    ) {
        itemsIndexed(pieces) { _, piece ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(TrayGrid, shape = RoundedCornerShape(4.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        val scale = minOf(
                            size.width / piece.trayBitmap.width,
                            size.height / piece.trayBitmap.height
                        )
                        val matrix = android.graphics.Matrix().apply { postScale(scale, scale) }
                        canvas.nativeCanvas.drawBitmap(piece.trayBitmap, matrix, null)
                    }
                }
            }
        }
    }
}

// ── Shared ────────────────────────────────────────────────────────────────────

@Composable
fun PuzzleSolvedDialog(elapsedSeconds: Long, pieceCount: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Puzzle Solved!", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Completed in ${formatTime(elapsedSeconds)}")
                Text("$pieceCount pieces  •  ${String.format("%.1f", elapsedSeconds.toDouble() / pieceCount)}s per piece")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

fun formatTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
