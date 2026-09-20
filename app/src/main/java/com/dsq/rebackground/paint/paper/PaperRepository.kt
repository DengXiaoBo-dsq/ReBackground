package com.dsq.rebackground.paint.paper

/** Repository boundary for built-in and future imported paper recipes. */
interface PaperRepository {
    fun find(id: String): PaperDefinition?
    fun all(): List<PaperDefinition>
}

class InMemoryPaperRepository(definitions: Collection<PaperDefinition> = emptyList()) : PaperRepository {
    private val definitionsById = LinkedHashMap<String, PaperDefinition>()

    init {
        definitions.forEach(::put)
    }

    fun put(definition: PaperDefinition) {
        definitionsById[definition.id] = definition
    }

    override fun find(id: String): PaperDefinition? = definitionsById[id]
    override fun all(): List<PaperDefinition> = definitionsById.values.toList()
}
