package org.mercurio.client.buildlogic

import org.w3c.dom.Element

data class Counter(val covered: Int, val missed: Int)

internal fun Element.directCounters(): Map<String, Counter> {
    val counters = mutableMapOf<String, Counter>()
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element && child.nodeName == "counter") {
            counters[child.getAttribute("type")] =
                Counter(
                    covered = child.getAttribute("covered").toIntOrNull() ?: 0,
                    missed = child.getAttribute("missed").toIntOrNull() ?: 0,
                )
        }
    }
    return counters
}
