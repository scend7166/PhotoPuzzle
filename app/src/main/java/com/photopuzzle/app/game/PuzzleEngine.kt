package com.photopuzzle.app.game

import android.graphics.*
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Describes the tab/blank connector on one edge of a puzzle piece.
 *
 * @param sign       +1 = tab protrudes outward, -1 = blank indents inward
 * @param offset     Centre of the tab along the edge, as a fraction of edge length (0.35–0.65)
 * @param tabHalf    Half-width of the tab as a fraction of edge length (0.18–0.26)
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

        val occupiedCells = mutableSetOf<Pair<Int, Int>>()
        var count = 0
        outer@ for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (count >= pieceCount) break@outer
                occupiedCells.add(Pair(col, row))
                count++
            }
        }

        // One shared connector per seam. The piece that owns the seam keeps the sign;
        // the neighbour inverts it so tabs always pair with blanks.
        val horizontalSeamConnectors = HashMap<Pair<Int, Int>, EdgeConnector>()
        for (row in 0 until rows - 1)
            for (col in 0 until cols)
                if (Pair(col, row) in occupiedCells && Pair(col, row + 1) in occupiedCells)
                    horizontalSeamConnectors[Pair(col, row)] = randomConnector(rng)

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

                val topEdge    = horizontalSeamConnectors[Pair(col, row - 1)]?.let { it.copy(sign = -it.sign) }
                val bottomEdge = horizontalSeamConnectors[Pair(col, row)]
                val leftEdge   = verticalSeamConnectors  [Pair(col - 1, row)]?.let { it.copy(sign = -it.sign) }
                val rightEdge  = verticalSeamConnectors  [Pair(col, row)]

                val sourceRect = RectF(
                    col * pieceWidth,       row * pieceHeight,
                    (col + 1) * pieceWidth, (row + 1) * pieceHeight
                )
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

    private fun randomConnector(rng: Random) = EdgeConnector(
        sign       = if (rng.nextBoolean()) +1 else -1,
        offset     = rng.nextFloat() * 0.30f + 0.35f,
        tabHalf    = rng.nextFloat() * 0.08f + 0.18f,
        protrusion = rng.nextFloat() * 0.08f + 0.20f,
    )

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

        canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(margin - sourceRect.left, margin - sourceRect.top)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
        canvas.restore()

        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            color       = Color.argb(60, 0, 0, 0)
            strokeWidth = 1.5f
        }
        canvas.drawPath(clipPath, edgePaint)

        return outputBitmap
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bernstein / Bézier evaluator
    // Ported from C#/Unity BezierCurve by [source provided by user].
    // Uses an N-degree Bernstein polynomial to evaluate a point on the curve.
    // ─────────────────────────────────────────────────────────────────────────

    private val FACTORIAL = floatArrayOf(
        1f, 1f, 2f, 6f, 24f, 120f, 720f, 5040f, 40320f, 362880f,
        3628800f, 39916800f, 479001600f, 6227020800f, 87178291200f,
        1307674368000f, 20922789888000f, 355687428096000f, 6402373705728000f
    )

    private fun binomial(n: Int, i: Int): Float {
        return FACTORIAL[n] / (FACTORIAL[i] * FACTORIAL[n - i])
    }

    private fun bernstein(n: Int, i: Int, t: Float): Float {
        return binomial(n, i) * t.pow(i) * (1f - t).pow(n - i)
    }

    /**
     * Evaluate a point on an N-degree Bézier curve at parameter t (0..1).
     * controlPoints are (x, y) pairs.
     */
    private fun bezierPoint(t: Float, controlPoints: List<Pair<Float, Float>>): Pair<Float, Float> {
        val n = controlPoints.size - 1
        var x = 0f
        var y = 0f
        for (i in controlPoints.indices) {
            val b = bernstein(n, i, t)
            x += b * controlPoints[i].first
            y += b * controlPoints[i].second
        }
        return Pair(x, y)
    }

    /**
     * Sample an N-degree Bézier curve into a list of (x,y) points.
     * [steps] controls smoothness — 32 is plenty for jigsaw connector curves.
     */
    private fun bezierPolyline(
        controlPoints: List<Pair<Float, Float>>,
        steps: Int = 32
    ): List<Pair<Float, Float>> {
        return (0..steps).map { bezierPoint(it.toFloat() / steps, controlPoints) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Jigsaw connector control points
    //
    // Each connector is a single 8-degree Bézier curve with 9 control points
    // travelling from one end of the edge to the other.
    //
    // Normalised coordinate system for a horizontal edge:
    //   - along-axis (u):   0 = left corner, 1 = right corner
    //   - perp-axis  (v):   0 = edge line, positive = outward (away from piece centre)
    //
    // The 9 points define: flat approach → shoulder → neck → head → neck → shoulder → flat approach
    // For a blank (sign=-1) the v-coordinates are simply negated, carving a cavity inward.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns 9 control points for a jigsaw connector in normalised (u,v) space.
     * u runs 0..1 along the edge; v is the perpendicular with positive = outward.
     * For a blank, pass sign=-1 to invert v.
     */
    /**
     * Returns three bezier segment control-point lists for a jigsaw connector.
     * Splitting into three segments ensures u increases monotonically within each,
     * preventing self-intersection (which occurs when the head is wider than the neck
     * and a single bezier must double back in u to draw the head).
     *
     *   Seg 1: edge-start → neck-left   (shoulder approach)
     *   Seg 2: neck-left  → neck-right  (over the head)
     *   Seg 3: neck-right → edge-end    (shoulder departure)
     */
    private fun connectorSegments(conn: EdgeConnector):
            Triple<List<Pair<Float,Float>>, List<Pair<Float,Float>>, List<Pair<Float,Float>>> {
        val c  = conn.offset
        val hw = conn.tabHalf
        val p  = conn.protrusion
        val s  = conn.sign.toFloat()

        val neckHw = hw * 0.22f   // narrow neck
        val neckD  = p  * 0.45f   // neck depth (45% of full protrusion)
        val sw     = hw * 1.8f    // shoulder spread

        val seg1 = listOf(
            Pair(0f,           0f        ),
            Pair(c - sw,       0f        ),
            Pair(c - hw * 0.8f, 0f       ),
            Pair(c - neckHw,   s * neckD ),
        )
        val seg2 = listOf(
            Pair(c - neckHw,   s * neckD ),
            Pair(c - hw * 0.7f, s * p    ),
            Pair(c,            s * p * 1.1f),
            Pair(c + hw * 0.7f, s * p    ),
            Pair(c + neckHw,   s * neckD ),
        )
        val seg3 = listOf(
            Pair(c + neckHw,   s * neckD ),
            Pair(c + hw * 0.8f, 0f       ),
            Pair(c + sw,       0f        ),
            Pair(1f,           0f        ),
        )
        return Triple(seg1, seg2, seg3)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Path building
    // ─────────────────────────────────────────────────────────────────────────

    fun buildPiecePath(
        pieceWidth: Float,
        pieceHeight: Float,
        topEdge: EdgeConnector?,
        bottomEdge: EdgeConnector?,
        leftEdge: EdgeConnector?,
        rightEdge: EdgeConnector?,
        margin: Float,
    ): Path {
        val l = margin
        val t = margin
        val r = margin + pieceWidth
        val b = margin + pieceHeight

        val path = Path()
        path.fillType = Path.FillType.EVEN_ODD
        path.moveTo(l, t)

        // Top edge: travels left→right, tab protrudes upward (−Y)
        appendHEdge(path, l, t, r, t, topEdge,    pieceWidth, pieceHeight, perpSign = -1f)
        // Right edge: travels top→bottom, tab protrudes rightward (+X)
        appendVEdge(path, r, t, r, b, rightEdge,  pieceWidth, pieceHeight, perpSign = +1f)
        // Bottom edge: travels right→left, tab protrudes downward (+Y)
        appendHEdge(path, r, b, l, b, bottomEdge, pieceWidth, pieceHeight, perpSign = +1f)
        // Left edge: travels bottom→top, tab protrudes leftward (−X)
        appendVEdge(path, l, b, l, t, leftEdge,   pieceWidth, pieceHeight, perpSign = -1f)

        path.close()
        return path
    }

    /**
     * Append a horizontal connector edge to [path].
     * Travels from (startX, edgeY) to (endX, edgeY).
     * [perpSign] = -1 for top edge (tab goes up), +1 for bottom edge (tab goes down).
     */
    private fun appendHEdge(
        path: Path,
        startX: Float, edgeY: Float,
        endX: Float,   @Suppress("UNUSED_PARAMETER") endY: Float,
        conn: EdgeConnector?,
        pieceWidth: Float, pieceHeight: Float,
        perpSign: Float,
    ) {
        if (conn == null) { path.lineTo(endX, edgeY); return }

        // For reverse edges (right→left), flip the connector offset so the tab
        // appears at the correct absolute position on the piece, not mirrored.
        val forward = endX >= startX
        val effectiveConn = if (forward) conn else conn.copy(offset = 1f - conn.offset)
        val (seg1, seg2, seg3) = connectorSegments(effectiveConn)

        // u=0 always maps to startX, u=1 to endX — (endX-startX) is negative for reverse,
        // so direction is handled automatically without flipping u.
        fun mapH(u: Float, v: Float): Pair<Float, Float> =
            Pair(startX + u * (endX - startX), edgeY + v * pieceHeight * perpSign)

        bezierPolyline(seg1).forEach         { (u,v) -> val (x,y) = mapH(u,v); path.lineTo(x,y) }
        bezierPolyline(seg2).drop(1).forEach { (u,v) -> val (x,y) = mapH(u,v); path.lineTo(x,y) }
        bezierPolyline(seg3).drop(1).forEach { (u,v) -> val (x,y) = mapH(u,v); path.lineTo(x,y) }
    }

    /**
     * Append a vertical connector edge to [path].
     * Travels from (edgeX, startY) to (edgeX, endY).
     * [perpSign] = +1 for right edge (tab goes right), -1 for left edge (tab goes left).
     */
    private fun appendVEdge(
        path: Path,
        edgeX: Float,  startY: Float,
        @Suppress("UNUSED_PARAMETER") endX: Float, endY: Float,
        conn: EdgeConnector?,
        pieceWidth: Float, pieceHeight: Float,
        perpSign: Float,
    ) {
        if (conn == null) { path.lineTo(edgeX, endY); return }

        val forward = endY >= startY
        val effectiveConn = if (forward) conn else conn.copy(offset = 1f - conn.offset)
        val (seg1, seg2, seg3) = connectorSegments(effectiveConn)

        fun mapV(u: Float, v: Float): Pair<Float, Float> =
            Pair(edgeX + v * pieceWidth * perpSign, startY + u * (endY - startY))

        bezierPolyline(seg1).forEach         { (u,v) -> val (x,y) = mapV(u,v); path.lineTo(x,y) }
        bezierPolyline(seg2).drop(1).forEach { (u,v) -> val (x,y) = mapV(u,v); path.lineTo(x,y) }
        bezierPolyline(seg3).drop(1).forEach { (u, v) -> val (x,y) = mapV(u,v); path.lineTo(x,y) }
    }
}
