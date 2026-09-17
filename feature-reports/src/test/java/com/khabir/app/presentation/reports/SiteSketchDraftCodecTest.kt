package com.khabir.app.presentation.reports

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSketchDraftCodecTest {
    @Test
    fun `round trip preserves background visibility and strokes`() {
        val original = listOf(
            SketchStroke(
                points = listOf(Offset(0.1f, 0.2f), Offset(0.9f, 0.8f)),
                color = Color(0xFFC62828),
                width = 0.005f
            ),
            SketchStroke(
                points = listOf(Offset(0f, 1f), Offset(1f, 0f)),
                color = Color(0xFF1565C0),
                width = 0.009f
            )
        )

        val encoded = SketchDraftCodec.encode(showBaseImage = false, strokes = original)
        val (showBase, restored) = SketchDraftCodec.decode(encoded)

        assertFalse(showBase)
        assertEquals(2, restored.size)
        assertEquals(original[0].color, restored[0].color)
        assertEquals(original[0].width, restored[0].width)
        assertEquals(original[0].points, restored[0].points)
        assertEquals(original[1].points, restored[1].points)
    }

    @Test
    fun `decoder ignores malformed strokes and clamps coordinates`() {
        val encoded = """
            v=1
            base=1
            s=-16777216|0.005|-2,3;0.5,0.5
            s=bad|bad|x,y
        """.trimIndent()

        val (showBase, restored) = SketchDraftCodec.decode(encoded)

        assertTrue(showBase)
        assertEquals(1, restored.size)
        assertEquals(Offset(0f, 1f), restored.single().points.first())
        assertEquals(Offset(0.5f, 0.5f), restored.single().points.last())
    }
}
