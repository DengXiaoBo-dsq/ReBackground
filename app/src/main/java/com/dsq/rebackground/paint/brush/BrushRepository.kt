package com.dsq.rebackground.paint.brush

/** Repository boundary for built-in, imported and future persisted brush recipes. */
interface BrushRepository {
    fun find(id: String): BrushDefinition?
    fun all(): List<BrushDefinition>
}

class InMemoryBrushRepository(definitions: Collection<BrushDefinition> = emptyList()) : BrushRepository {
    private val definitionsById = LinkedHashMap<String, BrushDefinition>()

    init { definitions.forEach(::put) }

    fun put(definition: BrushDefinition) {
        definitionsById[definition.id] = definition
    }

    override fun find(id: String): BrushDefinition? = definitionsById[id]
    override fun all(): List<BrushDefinition> = definitionsById.values.toList()
}
