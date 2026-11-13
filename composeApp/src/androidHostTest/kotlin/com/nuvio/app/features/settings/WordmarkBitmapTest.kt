package com.nuvio.app.features.settings

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordmarkBitmapTest {
    @Test
    fun runtimeWordmarkIsADecodableTransparentPng() {
        val drawable = File("src/commonMain/composeResources/drawable")
        val name = "app_logo_av_wordmark"
        assertFalse(File(drawable, "$name.xml").exists(), "Do not restore a vector runtime resource")
        assertFalse(File(drawable, "$name.svg").exists(), "SVG belongs only in design sources")
        val bitmap = ImageIO.read(File(drawable, "$name.png"))
        assertEquals(2328, bitmap.width)
        assertEquals(480, bitmap.height)
        assertTrue(bitmap.colorModel.hasAlpha())
        assertEquals(0, bitmap.getRGB(0, 0).ushr(24), "Background must be transparent")
        val pixels = bitmap.getRGB(0, 0, bitmap.width, bitmap.height, null, 0, bitmap.width)
        assertTrue(pixels.any { it.ushr(24) == 255 }, "Artwork must contain opaque strokes")
        assertTrue(pixels.any { it.ushr(24) in 1..254 }, "Edges must remain antialiased")
    }
}
