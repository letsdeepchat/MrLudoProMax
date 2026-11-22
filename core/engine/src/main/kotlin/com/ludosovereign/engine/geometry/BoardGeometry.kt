package com.ludosovereign.engine.geometry

import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BoardTopology.Companion.BASE
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in board units (1 unit = 1 cell), origin at the board centre, y pointing down. */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
}

/**
 * One generic geometry for both boards. Each seat owns an *arm* 3 cells wide and 6 deep that
 * points away from the centre; arms sit on the edges of a central square (4 seats) or hexagon
 * (6 seats), and each seat's yard sits in the wedge clockwise of its arm.
 *
 * Arm-local frame: `lane ∈ {-1, 0, +1}` across the arm, `depth ∈ 0..5` from the centre outward.
 * The shared track runs out along lane −1, across the tip (lane 0, depth 5), and back in along
 * lane +1, then continues on the next arm clockwise. The middle lane (depth 4..0) is the home
 * stretch, entered from the tip, and the centre polygon is Home.
 *
 * Pure Kotlin so it can be unit-tested and previewed on the JVM; the Compose canvas only scales it.
 */
class BoardGeometry(val topology: BoardTopology) {
    val seats = topology.seats

    /** Distance from the centre to the base of every arm. */
    val armBase: Float = if (seats == 4) 1.5f else (3.0 * sqrt(3.0) / 2.0).toFloat()

    /** Half-size of the square that contains the whole board. */
    val extent: Float = if (seats == 4) 7.5f else armBase + 6f + 0.6f

    fun armAngle(seat: Int): Double = PI / 2 + seat * 2 * PI / seats

    private fun armPoint(seat: Int, lane: Int, depth: Float): Pt {
        val a = armAngle(seat)
        val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        val vx = -uy; val vy = ux
        val r = armBase + depth + 0.5f
        return Pt(ux * r + vx * lane, uy * r + vy * lane)
    }

    /** Arm and position 0..12 along that arm's 13 track cells for an absolute track cell. */
    fun armSlot(cell: Int): Pair<Int, Int> {
        val shifted = Math.floorMod(cell + 8, topology.trackLength)
        return (shifted / 13) to (shifted % 13)
    }

    fun laneDepthOf(cell: Int): Triple<Int, Int, Int> {
        val (arm, j) = armSlot(cell)
        return when {
            j <= 5 -> Triple(arm, -1, j)
            j == 6 -> Triple(arm, 0, 5)
            else -> Triple(arm, 1, 12 - j)
        }
    }

    fun trackCell(cell: Int): Pt {
        val (arm, lane, depth) = laneDepthOf(cell)
        return armPoint(arm, lane, depth.toFloat())
    }

    /** Home-stretch cell [i] (0 = first, entered from the tip; 4 = last before Home). */
    fun stretchCell(seat: Int, i: Int): Pt = armPoint(seat, 0, (4 - i).toFloat())

    /** Where finished tokens rest: inside the centre polygon, toward the seat's arm. */
    fun homePoint(seat: Int, localIndex: Int = 0): Pt {
        val base = armPoint(seat, 0, -0.5f - armBase * 0.45f)
        val a = armAngle(seat)
        val spread = (localIndex - 1.5f) * 0.32f
        return Pt(base.x - sin(a).toFloat() * spread, base.y + cos(a).toFloat() * spread)
    }

    /**
     * Balanced resting spot for the [k]-th of [n] finished tokens inside [seat]'s Home wedge
     * (the triangle between the centre and the base of its arm): one or two sit mid-wedge, three
     * and four form a small pyramid, so finished pawns stay large instead of shrinking to dots.
     */
    fun homeSlot(seat: Int, k: Int, n: Int): Pt {
        // (t, s): t = fraction of the way from the centre to the arm base, s = fraction of the
        // wedge's half-width at that depth (negative = counter-clockwise side).
        val (t, s) = when (n) {
            1 -> 0.62f to 0f
            2 -> 0.62f to (if (k == 0) -0.5f else 0.5f)
            3 -> listOf(0.42f to 0f, 0.8f to -0.56f, 0.8f to 0.56f)[k]
            4 -> listOf(0.38f to 0f, 0.8f to -0.64f, 0.8f to 0f, 0.8f to 0.64f)[k]
            else -> 0.7f to ((k - (n - 1) / 2f) / n * 1.4f)
        }
        val a = armAngle(seat)
        val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        val d = t * armBase
        val lane = s * d * kotlin.math.tan(PI / seats).toFloat()
        return Pt(ux * d - uy * lane, uy * d + ux * lane)
    }

    fun yardCenter(seat: Int): Pt {
        val a = armAngle(seat) + PI / seats
        val r = if (seats == 4) 4.5f * sqrt(2f) else armBase + 4.1f
        return Pt((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
    }

    /** Radius of the yard disc. */
    val yardRadius: Float = if (seats == 4) 2.4f else 1.75f

    fun yardSlot(seat: Int, localIndex: Int): Pt {
        val c = yardCenter(seat)
        // Cross board: four bases on a 1.9-cell square inside the yard's white 4×4 box.
        val d = if (seats == 4) 0.95f else yardRadius * 0.5f
        val off = when (localIndex) {
            0 -> Pt(-d, -d); 1 -> Pt(d, -d); 2 -> Pt(-d, d); else -> Pt(d, d)
        }
        return c + off
    }

    /** Resting point for a token given its owner's seat and progress. */
    fun tokenPoint(seat: Int, progress: Int, localIndex: Int): Pt = when {
        progress == BASE -> yardSlot(seat, localIndex)
        topology.isOnTrack(progress) -> trackCell(topology.absoluteCell(seat, progress))
        topology.isHome(progress) -> homePoint(seat, localIndex)
        else -> stretchCell(seat, progress - topology.firstStretchProgress)
    }

    /** Cell-by-cell waypoints for animating a move (excluding the start point). */
    fun path(seat: Int, from: Int, to: Int, localIndex: Int): List<Pt> {
        if (from == BASE) return listOf(tokenPoint(seat, 0, localIndex))
        if (to == BASE) return listOf(yardSlot(seat, localIndex))
        return ((from + 1)..to).map { tokenPoint(seat, it, localIndex) }
    }

    /** Corners of the centre polygon (square or hexagon), for drawing Home. */
    fun centerPolygon(): List<Pt> {
        val n = seats
        val circum = if (n == 4) 1.5f * sqrt(2f) else 3f
        return (0 until n).map { i ->
            val a = armAngle(i) + PI / n
            Pt((cos(a) * circum).toFloat(), (sin(a) * circum).toFloat())
        }
    }
}
