@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nuvio.app.features.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/** PCM gain and speaker downmix. Encoded passthrough is handled by the output device. */
internal class PlaybackAudioProcessor : BaseAudioProcessor() {
    private var layout = PlayerSettingsUiState()
    private var outputChannels = 2

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (format.encoding != C.ENCODING_PCM_16BIT && format.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        layout = PlayerSettingsRepository.uiState.value
        val requested = when (layout.audioChannels) { "mono" -> 1; "5.1" -> 6; "7.1" -> 8; else -> 2 }
        outputChannels = if (layout.audioDownmix) requested.coerceAtMost(format.channelCount) else format.channelCount
        return AudioProcessor.AudioFormat(format.sampleRate, outputChannels, format.encoding)
    }

    override fun queueInput(input: ByteBuffer) {
        val bytes = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val channels = inputAudioFormat.channelCount
        val frames = input.remaining() / (bytes * channels)
        val output = replaceOutputBuffer(frames * bytes * outputChannels).order(ByteOrder.nativeOrder())
        input.order(ByteOrder.nativeOrder())
        val settings = PlayerSettingsRepository.uiState.value
        val gain = 10.0.pow(settings.audioAmplificationDb.coerceIn(0f, 20f) / 20.0).toFloat()
        val samples = FloatArray(channels)
        repeat(frames) {
            for (channel in 0 until channels) samples[channel] = if (bytes == 4) input.float else input.short / 32768f
            if (outputChannels == channels) {
                samples.forEach { writeSample(output, it * gain, bytes) }
            } else if (outputChannels > 2) {
                for (channel in 0 until outputChannels) {
                    val foldsSurround = outputChannels == 6 && channels == 8 && channel in 4..5
                    val extra = if (foldsSurround) samples[channel + 2] * 0.707f else 0f
                    val scale = if (foldsSurround && !settings.audioMaintainVolume) 1.707f else 1f
                    writeSample(output, (samples[channel] + extra) * gain / scale, bytes)
                }
            } else {
                val hasCenter = channels == 3 || channels >= 5
                val center = if (hasCenter) samples[2] * settings.audioCenterMixLevel.coerceIn(0f, 1.5f) else 0f
                val surroundIndex = when (channels) { 4 -> 2; 5 -> 3; else -> 4 }
                val surroundLeft = (samples.getOrElse(surroundIndex) { 0f }) + (if (channels == 8) samples[6] else 0f)
                val surroundRight = (samples.getOrElse(surroundIndex + 1) { 0f }) + (if (channels == 8) samples[7] else 0f)
                val divisor = if (settings.audioMaintainVolume) 1f else 1f + (if (hasCenter) settings.audioCenterMixLevel else 0f) + (if (channels >= 4) 0.707f else 0f) + (if (channels == 8) 0.707f else 0f)
                val left = (samples[0] + center + surroundLeft * 0.707f) / divisor
                val right = (samples.getOrElse(1) { samples[0] } + center + surroundRight * 0.707f) / divisor
                if (outputChannels == 1) writeSample(output, (left + right) * 0.5f * gain, bytes)
                else { writeSample(output, left * gain, bytes); writeSample(output, right * gain, bytes) }
            }
        }
        output.flip()
    }

    private fun writeSample(output: ByteBuffer, value: Float, bytes: Int) {
        if (bytes == 4) output.putFloat(value.coerceIn(-1f, 1f))
        else output.putShort((value * 32768).roundToInt().coerceIn(-32768, 32767).toShort())
    }
}
