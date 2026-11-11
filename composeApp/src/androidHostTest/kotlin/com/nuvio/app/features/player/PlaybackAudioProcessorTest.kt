package com.nuvio.app.features.player

import android.app.Application
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackAudioProcessorTest {
    @BeforeTest fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_player_settings", Context.MODE_PRIVATE).edit().clear().commit()
        PlayerSettingsStorage.initialize(context)
        PlayerSettingsRepository.onProfileChanged()
    }
    @AfterTest fun clean() = PlayerSettingsRepository.clearLocalState()

    private fun process(channels: Int, samples: ShortArray): Pair<Int, List<Short>> {
        val processor = PlaybackAudioProcessor()
        val format = processor.configure(AudioProcessor.AudioFormat(48000, channels, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        return format.channelCount to buildList { while (output.hasRemaining()) add(output.short) }
    }

    @Test fun gainClipsWithoutIntegerOverflow() {
        PlayerSettingsRepository.setAudioAmplificationDb(20f)
        assertEquals(listOf(Short.MAX_VALUE, Short.MIN_VALUE), process(2, shortArrayOf(10000, -10000)).second)
    }
    @Test fun surroundDialogueReachesBothStereoChannels() {
        PlayerSettingsRepository.setAudioDownmix(true)
        PlayerSettingsRepository.setAudioChannels("stereo")
        PlayerSettingsRepository.setAudioCenterMixLevel(1f)
        val (channels, samples) = process(6, shortArrayOf(0, 0, 12000, 0, 0, 0))
        assertEquals(2, channels)
        assertEquals(listOf<Short>(12000, 12000), samples)
    }
    @Test fun deviceDelayIsRestoredIndependently() {
        PlayerSettingsRepository.setRememberAudioDelay(true)
        PlayerSettingsRepository.setActiveAudioDevice("speaker")
        PlayerSettingsRepository.setAudioDelayMs(120)
        PlayerSettingsRepository.setActiveAudioDevice("headphones")
        assertEquals(0, PlayerSettingsRepository.uiState.value.audioDelayMs)
        PlayerSettingsRepository.setAudioDelayMs(-80)
        PlayerSettingsRepository.setActiveAudioDevice("speaker")
        assertEquals(120, PlayerSettingsRepository.uiState.value.audioDelayMs)
        PlayerSettingsRepository.onProfileChanged()
        PlayerSettingsRepository.setActiveAudioDevice("headphones")
        assertEquals(-80, PlayerSettingsRepository.uiState.value.audioDelayMs)
    }
}
