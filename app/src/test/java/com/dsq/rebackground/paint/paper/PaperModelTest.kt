package com.dsq.rebackground.paint.paper

import com.dsq.rebackground.paint.pigment.PigmentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperModelTest {
    @Test
    fun material_maps_to_more_absorbent_pigment_simulation() {
        val base = com.dsq.rebackground.paint.pigment.PigmentSimulationConfig()
        val mapped = PaperMaterial(roughness = .8f, absorption = .9f).toPigmentSimulationConfig(base)
        assertTrue(mapped.absorptionRate > base.absorptionRate)
    }

    @Test
    fun repository_replaces_by_id() {
        val first = PaperDefinition("paper", "Old")
        val replacement = PaperDefinition("paper", "New", material = PaperMaterial(absorption = .8f))
        val repository = InMemoryPaperRepository(listOf(first))
        repository.put(replacement)
        assertEquals(1, repository.all().size)
        assertEquals(replacement, repository.find("paper"))
    }

    @Test
    fun grain_and_composite_remain_normalized() {
        val grain = PaperGrainField(8, 8, seed = 7L).sample(3, 4)
        val composite = PaperCompositeModel().composite(
            PigmentColor.White,
            PigmentColor(1f, 0f, 0f),
            grain
        )
        assertTrue(composite.red in 0f..1f)
        assertTrue(composite.green in 0f..1f)
        assertTrue(composite.blue in 0f..1f)
    }
}
