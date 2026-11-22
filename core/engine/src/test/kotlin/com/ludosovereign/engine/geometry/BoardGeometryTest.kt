package com.ludosovereign.engine.geometry

import com.ludosovereign.engine.BoardTopology
import java.io.File
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

class BoardGeometryTest {
    private fun dist(a: Pt, b: Pt) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    @Test fun trackIsContinuousAndNonOverlapping() {
        for (t in BoardTopology.entries) {
            val g = BoardGeometry(t)
            val pts = (0 until t.trackLength).map(g::trackCell)
            for (i in pts.indices) {
                val step = dist(pts[i], pts[(i + 1) % pts.size])
                assertTrue(step in 0.9..1.75, "$t step $i -> ${i + 1} = $step")
                for (j in i + 1 until pts.size) assertTrue(dist(pts[i], pts[j]) > 0.9, "$t cells $i and $j overlap")
            }
            for (seat in 0 until t.seats) {
                // Stretch starts next to the player's last track cell, and never overlaps the track.
                val last = g.trackCell(t.absoluteCell(seat, t.lastTrackProgress))
                assertTrue(dist(last, g.stretchCell(seat, 0)) in 0.9..1.1)
                for (i in 0..4) pts.forEach { assertTrue(dist(it, g.stretchCell(seat, i)) > 0.9) }
                // Yards stay clear of every track cell.
                for (k in 0..3) pts.forEach { assertTrue(dist(it, g.yardSlot(seat, k)) > 0.8, "$t yard $seat/$k too close") }
            }
            pts.forEach { assertTrue(kotlin.math.abs(it.x) <= g.extent && kotlin.math.abs(it.y) <= g.extent) }
        }
    }

    /** Finished tokens sit inside their own Home wedge, well apart, for every count from 1 to 4. */
    @Test fun homeSlotsStayInsideTheirWedge() {
        for (t in BoardTopology.entries) {
            val g = BoardGeometry(t)
            val poly = g.centerPolygon()
            for (seat in 0 until t.seats) {
                val a = poly[(seat - 1 + t.seats) % t.seats]; val b = poly[seat]
                for (n in 1..4) {
                    val pts = (0 until n).map { g.homeSlot(seat, it, n) }
                    pts.forEach { p -> assertTrue(insideTriangle(p, Pt(0f, 0f), a, b), "$t seat $seat n=$n: $p outside its wedge") }
                    for (i in pts.indices) for (j in i + 1 until pts.size) {
                        assertTrue(dist(pts[i], pts[j]) > 0.55, "$t seat $seat n=$n: slots $i and $j overlap")
                    }
                }
            }
        }
    }

    private fun insideTriangle(p: Pt, a: Pt, b: Pt, c: Pt): Boolean {
        fun cross(o: Pt, u: Pt, v: Pt) = (u.x - o.x) * (v.y - o.y) - (u.y - o.y) * (v.x - o.x)
        val d1 = cross(a, b, p); val d2 = cross(b, c, p); val d3 = cross(c, a, p)
        return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0))
    }

    /** Writes SVG previews to build/preview for eyeballing the layout. */
    @Test fun writeSvgPreviews() {
        val colors = listOf("#E53935", "#43A047", "#FDD835", "#1E88E5", "#8E24AA", "#FB8C00")
        for (t in BoardTopology.entries) {
            val g = BoardGeometry(t)
            val s = 40.0
            val e = g.extent * s
            val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="${-e} ${-e} ${2 * e} ${2 * e}" width="${2 * e}" height="${2 * e}" font-family="sans-serif">""")
            sb.append("""<rect x="${-e}" y="${-e}" width="${2 * e}" height="${2 * e}" fill="#f6f1e7"/>""")
            for (seat in 0 until t.seats) {
                val c = g.yardCenter(seat)
                sb.append("""<circle cx="${c.x * s}" cy="${c.y * s}" r="${g.yardRadius * s}" fill="${colors[seat]}" opacity="0.35"/>""")
                for (k in 0..3) g.yardSlot(seat, k).let { sb.append("""<circle cx="${it.x * s}" cy="${it.y * s}" r="${0.35 * s}" fill="${colors[seat]}"/>""") }
            }
            val poly = g.centerPolygon().joinToString(" ") { "${it.x * s},${it.y * s}" }
            sb.append("""<polygon points="$poly" fill="#ddd" stroke="#555"/>""")
            for (cell in 0 until t.trackLength) {
                val p = g.trackCell(cell)
                val safe = t.isSafeCell(cell)
                val owner = (0 until t.seats).firstOrNull { t.startCell(it) == cell }
                val fill = owner?.let { colors[it] } ?: if (safe) "#bbb" else "#fff"
                sb.append("""<rect x="${(p.x - 0.5) * s}" y="${(p.y - 0.5) * s}" width="$s" height="$s" fill="$fill" stroke="#444"/>""")
                sb.append("""<text x="${p.x * s}" y="${p.y * s + 4}" font-size="11" text-anchor="middle">$cell${if (safe && owner == null) "★" else ""}</text>""")
            }
            for (seat in 0 until t.seats) for (i in 0..4) {
                val p = g.stretchCell(seat, i)
                sb.append("""<rect x="${(p.x - 0.5) * s}" y="${(p.y - 0.5) * s}" width="$s" height="$s" fill="${colors[seat]}" opacity="0.7" stroke="#444"/>""")
                val h = g.homePoint(seat, i % 4)
                sb.append("""<circle cx="${h.x * s}" cy="${h.y * s}" r="${0.12 * s}" fill="${colors[seat]}"/>""")
            }
            sb.append("</svg>")
            File("build/preview").mkdirs()
            File("build/preview/${t.name.lowercase()}.svg").writeText(sb.toString())
        }
    }
}
