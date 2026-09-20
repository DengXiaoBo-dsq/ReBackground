package com.dsq.rebackground.paint

import com.dsq.rebackground.paint.rendering.RenderGraph
import com.dsq.rebackground.paint.rendering.RenderTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderGraphTest {
    @Test fun graph_orders_dependencies_before_their_consumers() {
        val graph = RenderGraph(listOf(
            RenderGraph.RenderPass("composite", setOf("stamps")),
            RenderGraph.RenderPass("stamps")
        ))
        assertEquals(listOf("stamps", "composite"), graph.ordered().map { it.id })
    }

    @Test(expected = IllegalArgumentException::class) fun graph_rejects_dependency_cycles() {
        RenderGraph(listOf(
            RenderGraph.RenderPass("a", setOf("b")),
            RenderGraph.RenderPass("b", setOf("a"))
        ))
    }

    @Test(expected = IllegalArgumentException::class) fun target_requires_positive_dimensions() {
        RenderTarget(0, 100)
    }
}
