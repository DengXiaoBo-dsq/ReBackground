package com.dsq.rebackground.paint.rendering

/**
 * Dependency-only render graph. Execution is intentionally delegated to GLPaintRenderer so scheduling can be unit tested
 * without a GPU context.
 */
class RenderGraph(passes: Collection<RenderPass>) {
    data class RenderPass(val id: String, val dependsOn: Set<String> = emptySet()) {
        init { require(id.isNotBlank()) }
    }

    private val passesById = passes.associateBy { it.id }
    private val orderedPasses: List<RenderPass>

    init {
        require(passesById.size == passes.size) { "Render pass ids must be unique" }
        passesById.values.forEach { pass ->
            require(pass.dependsOn.all { it in passesById }) { "Unknown render-pass dependency in ${pass.id}" }
        }
        val temporary = HashSet<String>()
        val permanent = HashSet<String>()
        val result = ArrayList<RenderPass>(passes.size)
        fun visit(id: String) {
            require(id !in temporary) { "Render graph contains a cycle at $id" }
            if (!permanent.add(id)) return
            temporary += id
            passesById.getValue(id).dependsOn.sorted().forEach(::visit)
            temporary -= id
            result += passesById.getValue(id)
        }
        passesById.keys.sorted().forEach(::visit)
        orderedPasses = result
    }

    fun ordered(): List<RenderPass> = orderedPasses
}
