package com.nuvio.app.features.downloads

/**
 * Decrypts one HLS AES-128 segment (CBC mode). HLS segments are PKCS7-padded per RFC 8216, but a
 * few providers emit unpadded (block-aligned) segments, so the implementation falls back to
 * no-padding when padding validation fails. [key] and [iv] are each 16 bytes.
 */
internal expect fun hlsAes128CbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
