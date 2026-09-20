package com.dsq.rebackground.paint.input

enum class PointerPhase { DOWN, MOVE, UP, CANCEL }
data class QueuedPointerSample(val phase: PointerPhase, val sample: PointerSample)

/** Fixed-capacity FIFO to decouple Android event delivery from stroke processing without per-event lists. */
class InputQueue(private val capacity: Int = 512) {
    init { require(capacity > 0) }
    private val items = arrayOfNulls<QueuedPointerSample>(capacity)
    private var head = 0
    private var size = 0

    fun offer(item: QueuedPointerSample): Boolean {
        if (size == capacity) return false
        items[(head + size) % capacity] = item
        size++
        return true
    }

    fun poll(): QueuedPointerSample? {
        if (size == 0) return null
        val item = items[head]
        items[head] = null
        head = (head + 1) % capacity
        size--
        return item
    }

    fun isEmpty(): Boolean = size == 0
}
