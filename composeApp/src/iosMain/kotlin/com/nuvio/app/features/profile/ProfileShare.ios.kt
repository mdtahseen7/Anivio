package com.nuvio.app.features.profile

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
internal actual object ProfileShare {

    actual suspend fun shareImage(image: ImageBitmap, fileName: String, caption: String): Boolean {
        val uiImage = image.toUIImage() ?: return false
        val root = topViewController() ?: return false
        val items = buildList<Any> {
            add(uiImage)
            if (caption.isNotBlank()) add(caption)
        }
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        root.presentViewController(controller, animated = true, completion = null)
        return true
    }

    private fun ImageBitmap.toUIImage(): UIImage? {
        val pixels = toPixelMap()
        val width = pixels.width
        val height = pixels.height
        if (width <= 0 || height <= 0) return null

        // Build a tightly-packed RGBA8888 buffer from the Compose pixel map.
        val bytes = ByteArray(width * height * 4)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[x, y]
                bytes[i++] = (c.red * 255f).toInt().coerceIn(0, 255).toByte()
                bytes[i++] = (c.green * 255f).toInt().coerceIn(0, 255).toByte()
                bytes[i++] = (c.blue * 255f).toInt().coerceIn(0, 255).toByte()
                bytes[i++] = (c.alpha * 255f).toInt().coerceIn(0, 255).toByte()
            }
        }

        return bytes.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = (width * 4).toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: return@usePinned null
            val cgImage = CGBitmapContextCreateImage(context) ?: return@usePinned null
            UIImage.imageWithCGImage(cgImage)
        }
    }

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }
}
