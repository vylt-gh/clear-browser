package dev.clearbrowser.feature.reading

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShapeTest {

    @Test
    fun bubbleShapeProducesGenericOutline() {
        val shape = BubbleShape(
            cornerRadius = 16.dp,
            arrowWidth = 16.dp,
            arrowHeight = 10.dp,
            arrowOffsetFromRight = 16.dp,
        )
        val density = Density(density = 2f, fontScale = 1f)
        val outline = shape.createOutline(
            size = Size(width = 400f, height = 600f),
            layoutDirection = LayoutDirection.Ltr,
            density = density,
        )
        assertTrue(outline is Outline.Generic)
    }
}
