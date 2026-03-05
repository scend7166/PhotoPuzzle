package com.photopuzzle.app.game

import android.graphics.*
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

data class EdgeConnector(val sign: Int, val offset: Float, val size: Float, val depth: Float)

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
        val pieceW = bitmap.width.toFloat() / cols
        val pieceH = bitmap.height.toFloat() / rows

        val occupied = mutableSetOf<Pair<Int, Int>>()
        var count = 0
        outer@ for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (count >= pieceCount) break@outer
                occupied.add(Pair(col, row)); count++
            }
        }

        val hConn = HashMap<Pair<Int, Int>, EdgeConnector>()
        for (row in 0 until rows - 1)
            for (col in 0 until cols)
                if (Pair(col, row) in occupied && Pair(col, row + 1) in occupied)
                    hConn[Pair(col, row)] = randomConnector(rng)

        val vConn = HashMap<Pair<Int, Int>, EdgeConnector>()
        for (row in 0 until rows)
            for (col in 0 until cols - 1)
                if (Pair(col, row) in occupied && Pair(col + 1, row) in occupied)
                    vConn[Pair(col, row)] = randomConnector(rng)

        val pieces = mutableListOf<PieceShape>()
        var id = 0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (Pair(col, row) !in occupied) continue
                val topEdge    = hConn[Pair(col, row - 1)]?.copy(sign = -1)
                val bottomEdge = hConn[Pair(col, row)]?.copy(sign = +1)
                val leftEdge   = vConn[Pair(col - 1, row)]?.copy(sign = -1)
                val rightEdge  = vConn[Pair(col, row)]?.copy(sign = +1)

                val srcRect = RectF(col * pieceW, row * pieceH, (col+1)*pieceW, (row+1)*pieceH)
                val margin  = (maxOf(pieceW, pieceH) * 0.40f).toInt()
                val shaped  = renderShapedPiece(bitmap, srcRect, pieceW, pieceH,
                    topEdge, bottomEdge, leftEdge, rightEdge, margin)

                pieces.add(PieceShape(
                    id = id++, correctCol = col, correctRow = row,
                    topEdge = topEdge, bottomEdge = bottomEdge,
                    leftEdge = leftEdge, rightEdge = rightEdge,
                    bitmap = bitmap, srcRect = srcRect, shapedBitmap = shaped,
                    margin = margin, bitmapPieceW = pieceW, bitmapPieceH = pieceH
                ))
            }
        }
        return pieces
    }

    private fun randomConnector(rng: Random) = EdgeConnector(
        sign   = +1,
        offset = rng.nextFloat() * 0.3f + 0.35f,
        size   = rng.nextFloat() * 0.08f + 0.20f,
        depth  = rng.nextFloat() * 0.08f + 0.20f
    )

    private fun renderShapedPiece(
        src: Bitmap, srcRect: RectF, pieceW: Float, pieceH: Float,
        top: EdgeConnector?, bottom: EdgeConnector?,
        left: EdgeConnector?, right: EdgeConnector?, margin: Int
    ): Bitmap {
        val out = Bitmap.createBitmap(
            (pieceW + margin * 2).toInt(), (pieceH + margin * 2).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val path = buildPiecePath(pieceW, pieceH, top, bottom, left, right, margin.toFloat())
        canvas.save(); canvas.clipPath(path)
        canvas.translate(margin - srcRect.left, margin - srcRect.top)
        canvas.drawBitmap(src, 0f, 0f, null)
        canvas.restore()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.argb(60, 0, 0, 0); strokeWidth = 1.5f
        })
        return out
    }

    fun buildPiecePath(
        pw: Float, ph: Float,
        top: EdgeConnector?, bottom: EdgeConnector?,
        left: EdgeConnector?, right: EdgeConnector?,
        margin: Float
    ): Path {
        val x0 = margin;      val y0 = margin
        val x1 = margin + pw; val y1 = margin + ph
        val path = Path()
        path.moveTo(x0, y0)
        // perpSign: which direction the tab protrudes (+1 or -1 on that axis)
        hEdge(path, x0, y0, x1, y0, top,    pw, ph, perpSign = -1f) // top:    tab up    (−Y)
        vEdge(path, x1, y0, x1, y1, right,  pw, ph, perpSign = +1f) // right:  tab right (+X)
        hEdge(path, x1, y1, x0, y1, bottom, pw, ph, perpSign = +1f) // bottom: tab down  (+Y)
        vEdge(path, x0, y1, x0, y0, left,   pw, ph, perpSign = -1f) // left:   tab left  (−X)
        path.close()
        return path
    }

    /**
     * Horizontal edge from (x1,y) to (x2,y).
     *
     * Knob geometry — all control points sit at the tip X (kx ± perpSign*kd),
     * varying only in Y. This gives perfect mirror symmetry with the opposite piece.
     *
     *   kx  = knob centre X (along the edge)
     *   kh  = knob half-width  (along edge,    fraction of pw)
     *   kd  = knob depth       (perpendicular,  fraction of ph, signed by perpSign*sign)
     *   n   = 0.4*kh, neck offset for smooth S-curve
     *
     * Path (right-travelling, tab protruding upward as example):
     *   lineTo(kx-kh, y)                              ← approach near side
     *   cubicTo(tip_x, y-kh,  tip_x, y-n,  tip_x, y) ← sweep out to tip
     *   cubicTo(tip_x, y+n,   tip_x, y+kh, kx+kh, y) ← sweep back to far side
     *   lineTo(x2, y)
     */
    /**
     * Horizontal edge from (x1,y) to (x2,y).
     * Same 4-segment classic jigsaw shape, axes swapped.
     */
    private fun hEdge(
        path: Path, x1: Float, y1: Float, x2: Float, y2: Float,
        conn: EdgeConnector?, pw: Float, ph: Float, perpSign: Float
    ) {
        if (conn == null) { path.lineTo(x2, y2); return }
        val dir   = if (x2 >= x1) 1f else -1f
        val kx    = minOf(x1, x2) + pw * conn.offset
        val kh    = conn.size  * pw
        val kd    = conn.depth * ph * perpSign * conn.sign
        val tipY  = y1 + kd
        val neckY = y1 + kd * 0.45f
        val neckH = kh * 0.25f
        val adv   = kh * 0.55f
        val hcp   = kd * 0.45f

        val dip = kd * 0.30f
        // Corner → neck: one long cubic
        path.cubicTo(x1,                  y1 - dip,
                     kx - dir * neckH,    y1 - dip,
                     kx - dir * neckH,    neckY)
        path.cubicTo(kx - dir * hcp, neckY,
                     kx - dir * hcp, tipY,
                     kx,             tipY)
        path.cubicTo(kx + dir * hcp, tipY,
                     kx + dir * hcp, neckY,
                     kx + dir * neckH, neckY)
        // Neck → corner: one long cubic mirrors entry
        path.cubicTo(kx + dir * neckH,    y1 - dip,
                     x2,                  y1 - dip,
                     x2,                  y1)
    }

    /**
     * Vertical edge from (x,y1) to (x,y2).
     * Classic 4-segment jigsaw: entry → neck → head tip → neck → exit.
     * neckX = 50% depth, neck spans 62% of half-slot-height.
     * All terms use `dir` so dir=+1 (top→bottom) and dir=-1 (bottom→top) are exact mirrors.
     */
    private fun vEdge(
        path: Path, x1: Float, y1: Float, x2: Float, y2: Float,
        conn: EdgeConnector?, pw: Float, ph: Float, perpSign: Float
    ) {
        if (conn == null) { path.lineTo(x2, y2); return }
        val dir   = if (y2 >= y1) 1f else -1f
        val ky    = minOf(y1, y2) + ph * conn.offset
        val kh    = conn.size  * ph
        val kd    = conn.depth * pw * perpSign * conn.sign
        val tipX  = x1 + kd
        val neckX = x1 + kd * 0.45f   // neck at 45% of depth
        val neckH = kh * 0.25f         // narrow neck (25% of half-slot) — sharp constriction
        val adv   = kh * 0.55f         // large advance — path hugs edge before jumping to neck
        val hcp   = kd * 0.45f         // head CP — fraction of depth for circular head

        val dip = kd * 0.30f            // inward pull past edge — creates shoulder indent
        // Corner → neck: one long cubic so shoulder is a fluid curve from the piece corner
        path.cubicTo(x1 - dip, y1,
                     x1 - dip, ky - dir * neckH,
                     neckX,    ky - dir * neckH)
        // Neck → head tip
        path.cubicTo(neckX, ky - dir * hcp,
                     tipX,  ky - dir * hcp,
                     tipX,  ky)
        // Head tip → neck (mirror)
        path.cubicTo(tipX,  ky + dir * hcp,
                     neckX, ky + dir * hcp,
                     neckX, ky + dir * neckH)
        // Neck → corner: one long cubic mirrors entry
        path.cubicTo(x1 - dip, ky + dir * neckH,
                     x1 - dip, y2,
                     x1,       y2)
    }
}
