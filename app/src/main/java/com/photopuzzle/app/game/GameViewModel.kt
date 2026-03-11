package com.photopuzzle.app.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photopuzzle.app.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

data class TrayPiece(
    val shape: PieceShape,
    val trayBitmap: Bitmap
)

data class TablePiece(
    val shape: PieceShape,
    val position: Offset,
    val slotCol: Int = -1,   // which slot this piece currently occupies (-1 = floating)
    val slotRow: Int = -1,
    val isSnapped: Boolean = false  // true when slotCol/Row matches shape.correctCol/Row
)

enum class DragSource { TRAY, TABLE }

data class DragState(
    val source: DragSource,
    val sourceTrayIndex: Int? = null,
    val sourceTableIndex: Int? = null,
    val bitmap: Bitmap,
    val fingerPosition: Offset,      // in root coords
    val bitmapOffset: Offset,        // where within bitmap finger touched
    val displayW: Float = 0f,        // target display width on table (px)
    val displayH: Float = 0f         // target display height on table (px)
)

data class GameUiState(
    val tableWidthPx: Float = 0f,
    val tableHeightPx: Float = 0f,
    val pieceW: Float = 0f,
    val pieceH: Float = 0f,
    val margin: Int = 0,
    val imageAspectRatio: Float = 0f,
    val trayPieces: List<TrayPiece> = emptyList(),
    val tablePieces: List<TablePiece> = emptyList(),
    val drag: DragState? = null,
    val cols: Int = 0,
    val rows: Int = 0,
    val elapsedSeconds: Long = 0L,
    val isSolved: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isPaused: Boolean = false,
    val sourceBitmap: Bitmap? = null,   // full image for the peek overlay
)

@HiltViewModel
class GameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var imageUri: String = ""
    private var pieceCount: Int = 0
    private var storedTableWidthPx: Float = 0f
    private var storedTrayPieceSizePx: Float = 0f
    private var initialized = false

    // Layout boundaries set by the UI
    var tableTopY: Float = 0f
    var tableBottomY: Float = 0f   // = tableTopY + tableHeightPx
    var trayTopY: Float = 0f

    fun initGame(
        imageUri: String,
        pieceCount: Int,
        tableWidthPx: Float,
        trayPieceSizePx: Float
    ) {
        if (initialized) return
        initialized = true
        this.imageUri = imageUri
        this.pieceCount = pieceCount
        this.storedTableWidthPx = tableWidthPx
        this.storedTrayPieceSizePx = trayPieceSizePx

        viewModelScope.launch {
            try {
                _uiState.value = GameUiState(isLoading = true)

                data class Loaded(
                    val shapes: List<PieceShape>, val cols: Int, val rows: Int,
                    val pieceW: Float, val pieceH: Float, val margin: Int,
                    val aspectRatio: Float, val tableHeightPx: Float,
                    val sourceBitmap: Bitmap,
                )

                val loaded = withContext(Dispatchers.Default) {
                    val bitmap = loadBitmap(imageUri)
                    val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    // Derive table height from actual image aspect ratio
                    val tableHeightPx = tableWidthPx / aspectRatio
                    val maxDim = maxOf(tableWidthPx, tableHeightPx).toInt()
                    val scaled = scaleBitmapToFit(bitmap, maxDim)
                    val (c, r) = PuzzleEngine.gridDimensions(pieceCount)
                    val pw = tableWidthPx / c
                    val ph = tableHeightPx / r
                    val mg = (maxOf(pw, ph) * 0.45f).toInt()
                    Loaded(PuzzleEngine.createPieces(scaled, pieceCount), c, r, pw, ph, mg, aspectRatio, tableHeightPx, scaled)
                }

                val thumbSize = (trayPieceSizePx * 1.4f).toInt().coerceAtLeast(40)
                val tray = loaded.shapes.map { shape ->
                    TrayPiece(
                        shape = shape,
                        trayBitmap = Bitmap.createScaledBitmap(shape.shapedBitmap, thumbSize, thumbSize, true)
                    )
                }

                _uiState.value = GameUiState(
                    tableWidthPx = tableWidthPx,
                    tableHeightPx = loaded.tableHeightPx,
                    pieceW = loaded.pieceW,
                    pieceH = loaded.pieceH,
                    margin = loaded.margin,
                    imageAspectRatio = loaded.aspectRatio,
                    trayPieces = tray,
                    cols = loaded.cols,
                    rows = loaded.rows,
                    isLoading = false,
                    sourceBitmap = loaded.sourceBitmap,
                )
                startTimer()
            } catch (e: Exception) {
                _uiState.value = GameUiState(isLoading = false, errorMessage = "Failed to load: ${e.message}")
            }
        }
    }

    /** Reset the puzzle to its initial unsolved state, reshuffling pieces. */
    fun resetGame() {
        initialized = false
        initGame(imageUri, pieceCount, storedTableWidthPx, storedTrayPieceSizePx)
    }

    // ── Global drag handlers (called from single root pointerInput) ───────────

    fun onGlobalDragStart(rootX: Float, rootY: Float) {
        val state = _uiState.value
        if (isOnTable(rootY)) {
            startTableDrag(state, rootX - 0f, rootY - tableTopY)
        } else if (isOnTray(rootY)) {
            startTrayDrag(state, rootX, rootY)
        }
    }

    fun onGlobalDragMove(rootX: Float, rootY: Float) {
        val state = _uiState.value
        val drag = state.drag ?: return

        val newDrag = drag.copy(fingerPosition = Offset(rootX, rootY))
        var newState = state.copy(drag = newDrag)

        // If dragging a table piece, update its position live
        if (drag.source == DragSource.TABLE) {
            val idx = drag.sourceTableIndex ?: return
            val tableX = rootX - drag.bitmapOffset.x
            val tableY = rootY - tableTopY - drag.bitmapOffset.y
            val mutable = newState.tablePieces.toMutableList()
            if (idx < mutable.size) {
                mutable[idx] = mutable[idx].copy(position = Offset(tableX, tableY), isSnapped = false)
                newState = newState.copy(tablePieces = mutable)
            }
        }

        // If dragging a tray piece over another tray slot → reorder
        if (drag.source == DragSource.TRAY && isOnTray(rootY)) {
            val hoverIdx = trayIndexAt(newState, rootX, rootY)
            val srcIdx = drag.sourceTrayIndex
            if (hoverIdx != null && srcIdx != null && hoverIdx != srcIdx) {
                val mutable = newState.trayPieces.toMutableList()
                val moved = mutable.removeAt(srcIdx)
                mutable.add(hoverIdx, moved)
                newState = newState.copy(
                    trayPieces = mutable,
                    drag = newDrag.copy(sourceTrayIndex = hoverIdx)
                )
            }
        }

        _uiState.value = newState
    }

    fun onGlobalDragEnd(rootX: Float, rootY: Float) {
        val state = _uiState.value
        val drag = state.drag ?: return

        val mutableTray = state.trayPieces.toMutableList()
        val mutableTable = state.tablePieces.toMutableList()

        when {
            // ── Dropped on the table ──────────────────────────────────────────
            isOnTable(rootY) -> {
                val tableY = rootY - tableTopY
                val slot = slotForDrop(rootX, tableY, state)

                if (slot != null) {
                    val shape = when (drag.source) {
                        DragSource.TRAY -> {
                            val idx = drag.sourceTrayIndex ?: 0
                            if (idx < mutableTray.size) mutableTray.removeAt(idx).shape else null
                        }
                        DragSource.TABLE -> {
                            val idx = drag.sourceTableIndex ?: return
                            if (idx < mutableTable.size) mutableTable.removeAt(idx).shape else null
                        }
                    } ?: return

                    // Place in slot — if slot occupied, displaced piece returns to tray
                    val displaced = placeInSlot(shape, slot, mutableTable, state)
                    if (displaced != null) {
                        val insertAt = trayIndexAt(state, rootX, rootY)
                            ?.coerceIn(0, mutableTray.size) ?: mutableTray.size
                        mutableTray.add(insertAt, TrayPiece(
                            shape = displaced.shape,
                            trayBitmap = makeThumb(displaced.shape, state)
                        ))
                    }
                } else {
                    // Dropped outside grid — return table pieces to tray
                    if (drag.source == DragSource.TABLE) {
                        val idx = drag.sourceTableIndex ?: return
                        if (idx < mutableTable.size) {
                            val shape = mutableTable.removeAt(idx).shape
                            mutableTray.add(TrayPiece(shape = shape, trayBitmap = makeThumb(shape, state)))
                        }
                    }
                }
            }

            // ── Dropped on the tray ───────────────────────────────────────────
            isOnTray(rootY) -> {
                if (drag.source == DragSource.TABLE) {
                    val idx = drag.sourceTableIndex ?: return
                    if (idx < mutableTable.size) {
                        val shape = mutableTable.removeAt(idx).shape
                        val insertAt = trayIndexAt(state, rootX, rootY)
                            ?.coerceIn(0, mutableTray.size) ?: mutableTray.size
                        mutableTray.add(insertAt, TrayPiece(shape = shape, trayBitmap = makeThumb(shape, state)))
                    }
                }
                // Tray→tray reorder handled live in onGlobalDragMove
            }

            else -> { /* outside both zones — no-op */ }
        }

        val solved = mutableTray.isEmpty() && mutableTable.isNotEmpty() &&
            mutableTable.all { it.isSnapped }

        _uiState.value = state.copy(
            trayPieces = mutableTray,
            tablePieces = mutableTable,
            drag = null,
            isSolved = solved
        )

        if (solved) { timerJob?.cancel(); saveResult() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun startTableDrag(state: GameUiState, tableX: Float, tableY: Float) {
        val idx = state.tablePieces.indices.reversed().firstOrNull { i ->
            val p = state.tablePieces[i]
            val bw = p.shape.shapedBitmap.width.toFloat()
            val bh = p.shape.shapedBitmap.height.toFloat()
            tableX >= p.position.x && tableX <= p.position.x + bw &&
            tableY >= p.position.y && tableY <= p.position.y + bh
        } ?: return

        val mutable = state.tablePieces.toMutableList()
        val piece = mutable.removeAt(idx)
        mutable.add(piece.copy(isSnapped = false))

        val shape = piece.shape
        val scaleX = state.pieceW / shape.bitmapPieceW
        val scaleY = state.pieceH / shape.bitmapPieceH
        val displayW = shape.shapedBitmap.width * scaleX
        val displayH = shape.shapedBitmap.height * scaleY
        _uiState.value = state.copy(
            tablePieces = mutable,
            drag = DragState(
                source = DragSource.TABLE,
                sourceTableIndex = mutable.lastIndex,
                bitmap = shape.shapedBitmap,
                fingerPosition = Offset(tableX, tableY + tableTopY),
                bitmapOffset = Offset(tableX - piece.position.x, tableY - piece.position.y),
                displayW = displayW,
                displayH = displayH
            )
        )
    }

    private fun startTrayDrag(state: GameUiState, rootX: Float, rootY: Float) {
        val idx = trayIndexAt(state, rootX, rootY) ?: return
        val piece = state.trayPieces[idx]
        val shape = piece.shape
        val displayW = state.pieceW + shape.margin * 2 * (state.pieceW / shape.bitmapPieceW)
        val displayH = state.pieceH + shape.margin * 2 * (state.pieceH / shape.bitmapPieceH)
        _uiState.value = state.copy(
            drag = DragState(
                source = DragSource.TRAY,
                sourceTrayIndex = idx,
                bitmap = shape.shapedBitmap,
                fingerPosition = Offset(rootX, rootY),
                bitmapOffset = Offset(displayW / 2f, displayH / 2f),
                displayW = displayW,
                displayH = displayH
            )
        )
    }

    private fun trayIndexAt(state: GameUiState, rootX: Float, rootY: Float): Int? {
        if (state.trayPieces.isEmpty()) return null
        val cols = 5
        val trayWidth = state.tableWidthPx
        val cellW = trayWidth / cols
        val cellH = cellW  // square cells
        val col = ((rootX / cellW).toInt()).coerceIn(0, cols - 1)
        val row = (((rootY - trayTopY) / cellH).toInt()).coerceAtLeast(0)
        val idx = row * cols + col
        return if (idx < state.trayPieces.size) idx else state.trayPieces.size - 1
    }

    private fun isOnTable(rootY: Float) = rootY in tableTopY..tableBottomY
    private fun isOnTray(rootY: Float) = rootY >= trayTopY

    /**
     * Given a drop position on the table, returns which (col, row) slot it landed in.
     * Returns null if outside the grid entirely.
     */
    private fun slotForDrop(dropX: Float, dropY: Float, state: GameUiState): Pair<Int, Int>? {
        val col = (dropX / state.pieceW).toInt()
        val row = (dropY / state.pieceH).toInt()
        if (col < 0 || col >= state.cols || row < 0 || row >= state.rows) return null
        return Pair(col, row)
    }

    /**
     * Returns the position (top-left of shaped bitmap) for a piece snapped into (col, row).
     */
    private fun positionForSlot(col: Int, row: Int, shape: PieceShape, state: GameUiState): Offset {
        val scaleX = state.pieceW / shape.bitmapPieceW
        val scaleY = state.pieceH / shape.bitmapPieceH
        return Offset(
            col * state.pieceW - shape.margin * scaleX,
            row * state.pieceH - shape.margin * scaleY
        )
    }

    /**
     * Snaps a piece into a specific slot, swapping out any existing occupant.
     * Returns updated (tablePieces, displaced piece or null).
     */
    private fun placeInSlot(
        shape: PieceShape,
        slot: Pair<Int, Int>,
        mutableTable: MutableList<TablePiece>,
        state: GameUiState
    ): TablePiece? {
        val (col, row) = slot
        val pos = positionForSlot(col, row, shape, state)
        val isCorrect = col == shape.correctCol && row == shape.correctRow

        // Find any existing piece already in this slot
        val existingIdx = mutableTable.indexOfFirst { it.slotCol == col && it.slotRow == row }
        val displaced = if (existingIdx >= 0) mutableTable.removeAt(existingIdx) else null

        mutableTable.add(TablePiece(
            shape = shape,
            position = pos,
            slotCol = col,
            slotRow = row,
            isSnapped = isCorrect
        ))
        return displaced
    }

    private fun makeThumb(shape: PieceShape, state: GameUiState): Bitmap {
        val size = (state.tableWidthPx / 5f * 1.4f).toInt().coerceAtLeast(40)
        return Bitmap.createScaledBitmap(shape.shapedBitmap, size, size, true)
    }

    fun pauseGame() {
        if (_uiState.value.isPaused || _uiState.value.isSolved) return
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resumeGame() {
        if (!_uiState.value.isPaused) return
        _uiState.value = _uiState.value.copy(isPaused = false)
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) { delay(1000); _uiState.value = _uiState.value.copy(elapsedSeconds = _uiState.value.elapsedSeconds + 1) }
        }
    }

    private fun saveResult() {
        viewModelScope.launch {
            statsRepository.recordResult(pieceCount, _uiState.value.elapsedSeconds, imageUri)
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBitmap(uri: String): Bitmap {
        val u = Uri.parse(uri)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, u)) { dec, _, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else MediaStore.Images.Media.getBitmap(context.contentResolver, u)
    }

    private fun scaleBitmapToFit(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    override fun onCleared() { super.onCleared(); timerJob?.cancel() }
}
