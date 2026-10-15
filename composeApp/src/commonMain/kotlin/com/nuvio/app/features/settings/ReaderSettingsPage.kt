package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.details.MangaReaderSettingsRepository
import com.nuvio.app.features.details.MangaReadingMode
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_reader_mode_ltr
import nuvio.composeapp.generated.resources.settings_reader_mode_ltr_description
import nuvio.composeapp.generated.resources.settings_reader_mode_rtl
import nuvio.composeapp.generated.resources.settings_reader_mode_rtl_description
import nuvio.composeapp.generated.resources.settings_reader_mode_webtoon
import nuvio.composeapp.generated.resources.settings_reader_mode_webtoon_description
import nuvio.composeapp.generated.resources.settings_reader_section_mode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.readerSettingsContent(
    isTablet: Boolean,
) {
    item {
        MangaReaderSettingsRepository.ensureLoaded()
        val uiState by MangaReaderSettingsRepository.uiState.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.settings_reader_section_mode),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                MangaReadingMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                    ReaderModeRow(
                        mode = mode,
                        selected = uiState.readingMode == mode,
                        isTablet = isTablet,
                        onClick = { MangaReaderSettingsRepository.setReadingMode(mode) },
                    )
                }
            }
        }
    }
}

private data class ReaderModeText(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
)

private val MangaReadingMode.text: ReaderModeText
    get() = when (this) {
        MangaReadingMode.WEBTOON -> ReaderModeText(
            Res.string.settings_reader_mode_webtoon,
            Res.string.settings_reader_mode_webtoon_description,
        )
        MangaReadingMode.LEFT_TO_RIGHT -> ReaderModeText(
            Res.string.settings_reader_mode_ltr,
            Res.string.settings_reader_mode_ltr_description,
        )
        MangaReadingMode.RIGHT_TO_LEFT -> ReaderModeText(
            Res.string.settings_reader_mode_rtl,
            Res.string.settings_reader_mode_rtl_description,
        )
    }

@Composable
private fun ReaderModeRow(
    mode: MangaReadingMode,
    selected: Boolean,
    isTablet: Boolean,
    onClick: () -> Unit,
) {
    val text = mode.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 14.dp else 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(text.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(text.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
