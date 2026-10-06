package com.nuvio.app.features.downloads

import com.nuvio.app.features.plugins.cryptointerop.CCCrypt
import com.nuvio.app.features.plugins.cryptointerop.kCCAlgorithmAES
import com.nuvio.app.features.plugins.cryptointerop.kCCDecrypt
import com.nuvio.app.features.plugins.cryptointerop.kCCOptionPKCS7Padding
import com.nuvio.app.features.plugins.cryptointerop.kCCSuccess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.size_tVar

@OptIn(ExperimentalForeignApi::class)
internal actual fun hlsAes128CbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    if (data.isEmpty()) return data
    // Output can grow by at most one AES block over the input for CBC.
    val outCapacity = data.size + 16
    val output = ByteArray(outCapacity)

    fun run(usePadding: Boolean): ByteArray? = memScoped {
        val moved = alloc<size_tVar>()
        val status = data.usePinned { dataPin ->
            key.usePinned { keyPin ->
                iv.usePinned { ivPin ->
                    output.usePinned { outPin ->
                        CCCrypt(
                            op = kCCDecrypt,
                            alg = kCCAlgorithmAES,
                            options = if (usePadding) kCCOptionPKCS7Padding.convert() else 0u,
                            key = keyPin.addressOf(0),
                            keyLength = key.size.convert(),
                            iv = ivPin.addressOf(0),
                            dataIn = dataPin.addressOf(0),
                            dataInLength = data.size.convert(),
                            dataOut = outPin.addressOf(0),
                            dataOutAvailable = outCapacity.convert(),
                            dataOutMoved = moved.ptr,
                        )
                    }
                }
            }
        }
        if (status == kCCSuccess) output.copyOf(moved.value.toInt()) else null
    }

    // PKCS7 first (RFC 8216); fall back to no-padding for block-aligned, unpadded providers.
    return run(usePadding = true) ?: run(usePadding = false)
    ?: error("AES-128 segment decryption failed")
}
