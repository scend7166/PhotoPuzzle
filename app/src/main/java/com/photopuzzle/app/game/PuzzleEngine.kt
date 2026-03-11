package com.photopuzzle.app.game

import android.graphics.*
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Describes the tab/blank connector on one edge of a puzzle piece.
 *
 * @param sign       +1 = tab protrudes outward, -1 = blank indents inward
 * @param offset     Centre of the tab along the edge, as a fraction of edge length (0.35–0.65)
 * @param tabHalf    Half-width of the tab slot as a fraction of edge length (0.20–0.28)
 * @param protrusion Depth the tab protrudes as a fraction of the perpendicular dimension (0.20–0.28)
 */
data class EdgeConnector(
    val sign: Int,
    val offset: Float,
    val tabHalf: Float,
    val protrusion: Float,
)

data class PieceShape(
    val id: Int,
    val correctCol: Int,
    val correctRow: Int,
    val topEdge: EdgeConnector?,
    val bottomEdge: EdgeConnector?,
    val leftEdge: EdgeConnector?,
    val rightEdge: EdgeConnector?,
    val bitmap: Bitmap,
    val srcRect: RectF,
    val shapedBitmap: Bitmap,
    val margin: Int,
    val bitmapPieceW: Float,
    val bitmapPieceH: Float,
)

object PuzzleEngine {

    fun gridDimensions(pieceCount: Int): Pair<Int, Int> {
        val cols = sqrt(pieceCount.toDouble()).toInt().coerceAtLeast(1)
        val rows = ceil(pieceCount.toDouble() / cols).toInt()
        return Pair(cols, rows)
    }

    fun createPieces(bitmap: Bitmap, pieceCount: Int, rng: Random = Random.Default): List<PieceShape> {
        val (cols, rows) = gridDimensions(pieceCount)
        val pieceWidth  = bitmap.width.toFloat()  / cols
        val pieceHeight = bitmap.height.toFloat() / rows

        // Build set of occupied grid cells (handles non-square piece counts)
        val occupiedCells = mutableSetOf<Pair<Int, Int>>()
        var count = 0
        outer@ for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (count >= pieceCount) break@outer
                occupiedCells.add(Pair(col, row))
                count++
            }
        }

        // Generate one shared connector per horizontal seam (between row N and row N+1).
        // Keyed by the upper cell (col, row). The upper piece uses sign=+1 (tab down),
        // the lower piece copies it with sign=-1 (blank up).
        val horizontalSeamConnectors = HashMap<Pair<Int, Int>, EdgeConnector>()
        for (row in 0 until rows - 1)
            for (col in 0 until cols)
                if (Pair(col, row) in occupiedCells && Pair(col, row + 1) in occupiedCells)
                    horizontalSeamConnectors[Pair(col, row)] = randomConnector(rng)

        // Generate one shared connector per vertical seam (between col N and col N+1).
        // Keyed by the left cell (col, row). The left piece uses sign=+1 (tab right),
        // the right piece copies it with sign=-1 (blank left).
        val verticalSeamConnectors = HashMap<Pair<Int, Int>, EdgeConnector>()
        for (row in 0 until rows)
            for (col in 0 until cols - 1)
                if (Pair(col, row) in occupiedCells && Pair(col + 1, row) in occupiedCells)
                    verticalSeamConnectors[Pair(col, row)] = randomConnector(rng)

        val pieces = mutableListOf<PieceShape>()
        var nextId = 0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (Pair(col, row) !in occupiedCells) continue

                // Each piece looks up the shared seam connector and flips sign for the blank side
                val topEdge    = horizontalSeamConnectors[Pair(col, row - 1)]?.copy(sign = -1)
                val bottomEdge = horizontalSeamConnectors[Pair(col, row)]    ?.copy(sign = +1)
                val leftEdge   = verticalSeamConnectors  [Pair(col - 1, row)]?.copy(sign = -1)
                val rightEdge  = verticalSeamConnectors  [Pair(col, row)]    ?.copy(sign = +1)

                val sourceRect = RectF(
                    col * pieceWidth,       row * pieceHeight,
                    (col + 1) * pieceWidth, (row + 1) * pieceHeight
                )
                // Margin provides space for tabs that protrude beyond the base rectangle
                val margin = (maxOf(pieceWidth, pieceHeight) * 0.40f).toInt()

                val shapedBitmap = renderShapedPiece(
                    bitmap, sourceRect, pieceWidth, pieceHeight,
                    topEdge, bottomEdge, leftEdge, rightEdge, margin
                )

                pieces.add(PieceShape(
                    id           = nextId++,
                    correctCol   = col,
                    correctRow   = row,
                    topEdge      = topEdge,
                    bottomEdge   = bottomEdge,
                    leftEdge     = leftEdge,
                    rightEdge    = rightEdge,
                    bitmap       = bitmap,
                    srcRect      = sourceRect,
                    shapedBitmap = shapedBitmap,
                    margin       = margin,
                    bitmapPieceW = pieceWidth,
                    bitmapPieceH = pieceHeight,
                ))
            }
        }
        return pieces
    }

    /** Randomise a connector; sign is always +1 here and flipped per-piece at the call site. */
    private fun randomConnector(rng: Random) = EdgeConnector(
        sign       = +1,
        offset     = rng.nextFloat() * 0.30f + 0.35f,  // tab centre: 35%–65% along edge
        tabHalf    = rng.nextFloat() * 0.08f + 0.20f,  // slot half-width: 20%–28% of edge
        protrusion = rng.nextFloat() * 0.08f + 0.20f,  // tab depth: 20%–28% of perp dimension
    )

    /** Render the piece image clipped to its jigsaw shape into a new bitmap with margin space. */
    private fun renderShapedPiece(
        sourceBitmap: Bitmap,
        sourceRect: RectF,
        pieceWidth: Float,
        pieceHeight: Float,
        topEdge: EdgeConnector?,
        bottomEdge: EdgeConnector?,
        leftEdge: EdgeConnector?,
        rightEdge: EdgeConnector?,
        margin: Int,
    ): Bitmap {
        val outputBitmap = Bitmap.createBitmap(
            (pieceWidth  + margin * 2).toInt(),
            (pieceHeight + margin * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(outputBitmap)

        val clipPath = buildPiecePath(
            pieceWidth, pieceHeight,
            topEdge, bottomEdge, leftEdge, rightEdge,
            margin.toFloat()
        )

        // Draw the image clipped to the jigsaw shape
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(margin - sourceRect.left, margin - sourceRect.top)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
        canvas.restore()

        // Subtle outline so piece borders are visible against the background
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            color       = Color.argb(60, 0, 0, 0)
            strokeWidth = 1.5f
        }
        canvas.drawPath(clipPath, edgePaint)

        return outputBitmap
    }

    /**
     * Build the full clip path for one piece.
     *
     * The path travels clockwise: top → right → bottom → left.
     * [margin] offsets the piece rectangle inward from the bitmap edge,
     * leaving room for tabs that protrude beyond the base rectangle.
     *
     * [perpSign] controls which direction a tab protrudes on each edge:
     *   top    perpSign = -1  (tab goes up,    −Y)
     *   right  perpSign = +1  (tab goes right, +X)
     *   bottom perpSign = +1  (tab goes down,  +Y)
     *   left   perpSign = -1  (tab goes left,  −X)
     */
    fun buildPiecePath(
        pieceWidth: Float,
        pieceHeight: Float,
        topEdge: EdgeConnector?,
        bottomEdge: EdgeConnector?,
        leftEdge: EdgeConnector?,
        rightEdge: EdgeConnector?,
        margin: Float,
    ): Path {
        val leftX   = margin
        val topY    = margin
        val rightX  = margin + pieceWidth
        val bottomY = margin + pieceHeight

        val path = Path()
        path.moveTo(leftX, topY)
        hEdge(path, leftX,  topY,    rightX, topY,    topEdge,    pieceWidth, pieceHeight, perpSign = -1f)
        vEdge(path, rightX, topY,    rightX, bottomY, rightEdge,  pieceWidth, pieceHeight, perpSign = +1f)
        hEdge(path, rightX, bottomY, leftX,  bottomY, bottomEdge, pieceWidth, pieceHeight, perpSign = +1f)
        vEdge(path, leftX,  bottomY, leftX,  topY,    leftEdge,   pieceWidth, pieceHeight, perpSign = -1f)
        path.close()
        return path
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge path builders
    //
    // Each edge is drawn as 4 bezier segments producing the classic jigsaw profile:
    //
    //   1. Shoulder  corner → neck base
    //      A single long cubic starting at the piece corner. CP1 and CP2 both pull
    //      inward past the edge line (shoulderDip), creating the classic concave
    //      indent before the tab begins.
    //
    //   2. Neck → head tip
    //      Quick flare from the narrow neck out to the full protrusion depth.
    //      headCurveOffset controls the roundness — larger = rounder head.
    //
    //   3. Head tip → neck   (exact mirror of segment 2)
    //
    //   4. Neck → corner     (exact mirror of segment 1)
    //
    // Naming conventions used below:
    //   tabCentre       – position of tab centre along the edge (the knob's midpoint)
    //   tabHalfWidth/H  – half the slot width measured along the edge axis
    //   tabDepth        – signed protrusion (positive = outward tab, negative = inward blank)
    //   tipX / tipY     – coordinate of the outermost point of the head
    //   neckProtrusion  – how far the neck sits along the perp axis (45% of full depth)
    //   neckHalfWidth/H – how narrow the neck constriction is (25% of the full slot half-width)
    //   shoulderDip     – how far CP pulls inward past the edge line (30% of tabDepth)
    //   headCurveOffset – bezier CP distance that shapes the roundness of the head
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draw a horizontal edge from (startX, edgeY) to (endX, edgeY).
     * The tab protrudes in the Y direction (perpendicular to the edge).
     */
    private fun hEdge(
        path: Path,
        startX: Float, edgeY: Float,
        endX: Float,   endY: Float,
        conn: EdgeConnector?,
        pieceWidth: Float, pieceHeight: Float,
        perpSign: Float,
    ) {
        if (conn == null) { path.lineTo(endX, endY); return }

        val travelDir = if (endX >= startX) 1f else -1f

        // Tab centre: always measured from the left edge of the piece so both sides match
        val tabCentre      = minOf(startX, endX) + pieceWidth * conn.offset
        val tabHalfWidth   = conn.tabHalf    * pieceWidth
        val tabDepth       = conn.protrusion * pieceHeight * perpSign * conn.sign
        val tipY           = edgeY + tabDepth
        val neckProtrusion = edgeY + tabDepth * 0.45f  // neck Y (not full depth)
        val neckHalfWidth  = tabHalfWidth * 0.25f      // neck much narrower than full slot
        val shoulderDip    = tabDepth * 0.30f           // inward pull past edge line
        val headCurveOffset = tabDepth * 0.45f          // controls head roundness

        // 1. Corner → neck base (long shoulder, dips inward)
        path.cubicTo(
            startX,                                edgeY - shoulderDip,
            tabCentre - travelDir * neckHalfWidth, edgeY - shoulderDip,
            tabCentre - travelDir * neckHalfWidth, neckProtrusion
        )
        // 2. Neck → head tip
        path.cubicTo(
            tabCentre - travelDir * headCurveOffset, neckProtrusion,
            tabCentre - travelDir * headCurveOffset, tipY,
            tabCentre,                               tipY
        )
        // 3. Head tip → neck (mirror of 2)
        path.cubicTo(
            tabCentre + travelDir * headCurveOffset, tipY,
            tabCentre + travelDir * headCurveOffset, neckProtrusion,
            tabCentre + travelDir * neckHalfWidth,   neckProtrusion
        )
        // 4. Neck → corner (mirror of 1)
        path.cubicTo(
            tabCentre + travelDir * neckHalfWidth, edgeY - shoulderDip,
            endX,                                  edgeY - shoulderDip,
            endX,                                  edgeY
        )
    }

    /**
     * Draw a vertical edge from (edgeX, startY) to (edgeX, endY).
     * The tab protrudes in the X direction (perpendicular to the edge).
     */
    private fun vEdge(
        path: Path,
        edgeX: Float, startY: Float,
        endX: Float,  endY: Float,
        conn: EdgeConnector?,
        pieceWidth: Float, pieceHeight: Float,
        perpSign: Float,
    ) {
        if (conn == null) { path.lineTo(endX, endY); return }

        val travelDir = if (endY >= startY) 1f else -1f

        // Tab centre: always measured from the top edge of the piece so both sides match
        val tabCentre       = minOf(startY, endY) + pieceHeight * conn.offset
        val tabHalfHeight   = conn.tabHalf    * pieceHeight
        val tabDepth        = conn.protrusion * pieceWidth * perpSign * conn.sign
        val tipX            = edgeX + tabDepth
        val neckProtrusion  = edgeX + tabDepth * 0.45f  // neck X (not full depth)
        val neckHalfHeight  = tabHalfHeight * 0.25f     // neck much narrower than full slot
        val shoulderDip     = tabDepth * 0.30f           // inward pull past edge line
        val headCurveOffset = tabDepth * 0.45f           // controls head roundness

        // 1. Corner → neck base (long shoulder, dips inward)
        path.cubicTo(
            edgeX - shoulderDip, startY,
            edgeX - shoulderDip, tabCentre - travelDir * neckHalfHeight,
            neckProtrusion,      tabCentre - travelDir * neckHalfHeight
        )
        // 2. Neck → head tip
        path.cubicTo(
            neckProtrusion, tabCentre - travelDir * headCurveOffset,
            tipX,           tabCentre - travelDir * headCurveOffset,
            tipX,           tabCentre
        )
        // 3. Head tip → neck (mirror of 2)
        path.cubicTo(
            tipX,           tabCentre + travelDir * headCurveOffset,
            neckProtrusion, tabCentre + travelDir * headCurveOffset,
            neckProtrusion, tabCentre + travelDir * neckHalfHeight
        )
        // 4. Neck → corner (mirror of 1)
        path.cubicTo(
            edgeX - shoulderDip, tabCentre + travelDir * neckHalfHeight,
            edgeX - shoulderDip, endY,
            edgeX,               endY
        )
    }
}
