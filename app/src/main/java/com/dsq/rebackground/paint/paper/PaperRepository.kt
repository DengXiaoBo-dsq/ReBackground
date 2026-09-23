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

/** Built-in canvas papers. The stable id is the persisted canvas property. */
object PaperPresets {
    const val SMOOTH_ID = "smooth"
    const val MEDIUM_ID = "medium"
    const val ROUGH_WATERCOLOR_ID = "rough-watercolor"
    const val DEFAULT_ID = MEDIUM_ID

    private val definitions = listOf(
        PaperDefinition(
            id = SMOOTH_ID,
            displayName = "光滑纸",
            material = PaperMaterial(
                roughness = 0.08f,
                absorption = 0.24f,
                fiberDensity = 0.18f,
                grainScale = 1.35f,
                heightAmplitude = 0.04f,
            ),
            seed = 101,
        ),
        PaperDefinition(
            id = MEDIUM_ID,
            displayName = "中粗纸",
            material = PaperMaterial(
                roughness = 0.48f,
                absorption = 0.35f,
                fiberDensity = 0.52f,
                grainScale = 1.0f,
                heightAmplitude = 0.55f,
            ),
            seed = 211,
        ),
        PaperDefinition(
            id = ROUGH_WATERCOLOR_ID,
            displayName = "粗糙水彩纸",
            material = PaperMaterial(
                roughness = 0.88f,
                absorption = 0.62f,
                fiberDensity = 0.86f,
                grainScale = 0.72f,
                heightAmplitude = 0.94f,
            ),
            seed = 307,
        ),
    )

    private val repository = InMemoryPaperRepository(definitions)

    fun all(): List<PaperDefinition> = repository.all()
    fun find(id: String): PaperDefinition? = repository.find(id)
    fun requireOrDefault(id: String): PaperDefinition = find(id) ?: find(DEFAULT_ID)!!
}
