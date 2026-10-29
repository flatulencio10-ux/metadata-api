package com.customlauncher.desktop

import com.customlauncher.core.model.FloatingWindow
import com.customlauncher.core.model.Node
import com.customlauncher.core.model.newId

/** Window placement rules (framework-free; unit tested). */
object WindowMath {
    const val MIN_W = 160f
    const val MIN_H = 120f
    const val TITLE_H = 36f

    /** Keeps at least part of the title bar on screen so a window can always be grabbed. */
    fun clamp(w: FloatingWindow, areaW: Float, areaH: Float): FloatingWindow {
        val keep = 64f
        return w.copy(
            x = w.x.coerceIn(keep - w.width, (areaW - keep).coerceAtLeast(0f)),
            y = w.y.coerceIn(0f, (areaH - TITLE_H).coerceAtLeast(0f)),
        )
    }

    fun move(w: FloatingWindow, dx: Float, dy: Float, areaW: Float, areaH: Float) =
        clamp(w.copy(x = w.x + dx, y = w.y + dy, maximized = false), areaW, areaH)

    fun resize(w: FloatingWindow, dw: Float, dh: Float, areaW: Float, areaH: Float) = w.copy(
        width = (w.width + dw).coerceIn(MIN_W, maxOf(MIN_W, areaW - w.x.coerceAtLeast(0f))),
        height = (w.height + dh).coerceIn(MIN_H, maxOf(MIN_H, areaH - w.y)),
        maximized = false,
    )

    /** Cascades new windows so they don't stack exactly on top of each other. */
    fun cascade(existing: List<FloatingWindow>, width: Float, height: Float, areaW: Float, areaH: Float): FloatingWindow {
        val step = 28f * (existing.size % 6)
        return clamp(
            FloatingWindow(widget = com.customlauncher.core.model.WidgetInstance(type = ""), x = 24f + step, y = 48f + step, width = width.coerceAtMost(areaW - 16f), height = height.coerceAtMost(areaH - 16f)),
            areaW, areaH,
        )
    }
}

/** Start menu search and categories (framework-free; unit tested). */
object StartMenuLogic {
    /** Ranks label prefix matches first, then word starts, then initials ("gm" → Google Maps), then substrings. */
    fun <T> search(items: List<T>, query: String, label: (T) -> String): List<T> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items.sortedBy { label(it).lowercase() }
        fun score(s: String): Int {
            val l = s.lowercase()
            val words = l.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
            return when {
                l.startsWith(q) -> 0
                words.any { it.startsWith(q) } -> 1
                words.joinToString("") { it.take(1) }.startsWith(q) -> 2
                l.contains(q) -> 3
                else -> -1
            }
        }
        return items.map { it to score(label(it)) }.filter { it.second >= 0 }
            .sortedWith(compareBy({ it.second }, { label(it.first).lowercase() })).map { it.first }
    }

    /** Categories in a stable order, "Other" last. */
    fun categories(all: List<String>): List<String> = all.distinct().sortedWith(compareBy({ it == "Other" }, { it }))
}

/** Copy / paste of nodes between pages (framework-free; unit tested). */
object NodeClipboard {
    /**
     * New copies of [nodes] with fresh ids (groups kept together under new group ids), moved
     * so their bounding box starts at ([x], [y]) and stacked above [maxZ].
     */
    fun paste(nodes: List<Node>, x: Float, y: Float, maxZ: Int, id: () -> String = ::newId): List<Node> {
        if (nodes.isEmpty()) return emptyList()
        val minX = nodes.minOf { it.x }
        val minY = nodes.minOf { it.y }
        val groups = nodes.mapNotNull { it.groupId }.distinct().associateWith { id() }
        return nodes.sortedBy { it.z }.mapIndexed { i, n ->
            n.copy(id = id(), x = x + (n.x - minX), y = y + (n.y - minY), z = maxZ + 1 + i, groupId = n.groupId?.let(groups::get))
        }
    }
}
