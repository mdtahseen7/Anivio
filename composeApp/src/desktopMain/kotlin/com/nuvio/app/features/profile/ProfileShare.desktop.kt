package com.nuvio.app.features.profile

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

internal actual object ProfileShare {
    actual suspend fun shareImage(image: ImageBitmap, fileName: String, caption: String): Boolean {
        val destination = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                dialogTitle = "Save profile card"
                fileFilter = FileNameExtensionFilter("PNG image", "png")
                selectedFile = File("$fileName.png")
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
            val selected = chooser.selectedFile
            val file = if (selected.extension.equals("png", true)) selected else File(selected.path + ".png")
            if (file.exists() && JOptionPane.showConfirmDialog(
                    null, "Replace ${file.name}?", "Save profile card", JOptionPane.YES_NO_OPTION,
                ) != JOptionPane.YES_OPTION
            ) return@withContext null
            file
        } ?: return false

        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val pixels = IntArray(image.width * image.height)
                image.readPixels(pixels)
                val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
                output.setRGB(0, 0, image.width, image.height, pixels, 0, image.width)
                ImageIO.write(output, "png", destination)
            }.getOrDefault(false)
        }
        if (saved && caption.isNotBlank()) withContext(Dispatchers.Swing) {
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(caption), null)
            }
        }
        return saved
    }
}
