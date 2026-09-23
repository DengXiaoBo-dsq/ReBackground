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
                roughness = 0.10f,
                absorption = 0.10f,
                fiberDensity = 0.10f,
                grainScale = 1.30f,
                heightAmplitude = 0.10f,
            ),
            seed = 101,
            visualStrength = 0.05f,
        ),
        PaperDefinition(
            id = MEDIUM_ID,
            displayName = "中粗纸",
            material = PaperMaterial(
                roughness = 0.50f,
                absorption = 0.50f,
                fiberDensity = 0.50f,
                grainScale = 1.0f,
                heightAmplitude = 0.40f,
            ),
            seed = 211,
            visualStrength = 0.20f,
        ),
        PaperDefinition(
            id = ROUGH_WATERCOLOR_ID,
            displayName = "粗糙水彩纸",
            material = PaperMaterial(
                roughness = 0.90f,
                absorption = 0.80f,
                fiberDensity = 0.90f,
                grainScale = 0.72f,
                heightAmplitude = 0.80f,
            ),
            seed = 307,
            visualStrength = 0.40f,
        ),
    )

    private val repository = InMemoryPaperRepository(definitions)

    fun all(): List<PaperDefinition> = repository.all()
    fun find(id: String): PaperDefinition? = repository.find(id)
    fun requireOrDefault(id: String): PaperDefinition = find(id) ?: find(DEFAULT_ID)!!
}
