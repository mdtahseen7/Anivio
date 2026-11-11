package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun subtitleFontFamilyLabel(family: SubtitleFontFamily): String = stringResource(
    when (family) {
        SubtitleFontFamily.Default -> Res.string.settings_playback_subtitle_font_default
        SubtitleFontFamily.Sans -> Res.string.settings_playback_subtitle_font_sans
        SubtitleFontFamily.Serif -> Res.string.settings_playback_subtitle_font_serif
        SubtitleFontFamily.Monospace -> Res.string.settings_playback_subtitle_font_monospace
    },
)

@Composable
internal fun SubtitleFontFamilyDialog(
    selectedFamily: SubtitleFontFamily,
    onFamilySelected: (SubtitleFontFamily) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_playback_subtitle_font),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleFontFamily.entries.forEach { family ->
                DialogOption(
                    text = subtitleFontFamilyLabel(family),
                    selected = selectedFamily == family,
                    onClick = { onFamilySelected(family) },
                    leading = {
                        Text(
                            text = "Aa",
                            fontFamily = when (family) {
                                SubtitleFontFamily.Default -> FontFamily.Default
                                SubtitleFontFamily.Sans -> FontFamily.SansSerif
                                SubtitleFontFamily.Serif -> FontFamily.Serif
                                SubtitleFontFamily.Monospace -> FontFamily.Monospace
                            },
                        )
                    },
                )
            }
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_done), onClick = onDismiss)
        }
    }
}
