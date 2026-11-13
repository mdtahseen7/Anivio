package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AvSplashBackground
import androidx.compose.ui.graphics.toArgb
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class AvSplashBackgroundTest {
    @Test fun artworkAndNativeLaunchBackgroundsUseTheSameRgb() {
        val expected = 0xFF020200.toInt()
        assertEquals(expected, AvSplashBackground.toArgb())
        listOf("src/androidMain/res/drawable-nodpi/app_icon_av.jpg", "src/commonMain/composeResources/drawable/app_icon_av.jpg",
            "../iosApp/iosApp/Assets.xcassets/AppIconAV.appiconset/app-icon-1024.png").forEach { path ->
            val bitmap = ImageIO.read(File(path))
            listOf(0 to 0, bitmap.width - 1 to 0, 0 to bitmap.height - 1, bitmap.width - 1 to bitmap.height - 1).forEach { (x, y) ->
                assertEquals(expected, bitmap.getRGB(x, y), "$path corner $x,$y")
            }
        }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val colors = parser.parse(File("src/androidMain/res/values/colors.xml")).getElementsByTagName("color")
        val background = (0 until colors.length).map { colors.item(it) }.first { it.attributes.getNamedItem("name").nodeValue == "av_splash_background" }
        assertEquals("#020200", background.textContent.trim())
        val items = parser.parse(File("src/androidMain/res/values-v31/themes.xml")).getElementsByTagName("item")
        for (index in 0 until items.length) {
            val item = items.item(index)
            when (item.attributes.getNamedItem("name").nodeValue) {
                "windowSplashScreenBackground" -> assertEquals("@color/av_splash_background", item.textContent.trim())
                "windowSplashScreenAnimatedIcon" -> assertEquals("@drawable/ic_splash_empty", item.textContent.trim())
            }
        }
    }
}
