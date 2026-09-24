package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.ic_player_aspect_ratio
import nuvio.composeapp.generated.resources.ic_player_audio_filled
import nuvio.composeapp.generated.resources.ic_player_pause
import nuvio.composeapp.generated.resources.ic_player_play
import nuvio.composeapp.generated.resources.ic_player_subtitles
import nuvio.composeapp.generated.resources.library_add_plus
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun appIconPainter(icon: AppIconResource): Painter =
    painterResource(
        when (icon) {
            AppIconResource.PlayerPlay -> Res.drawable.ic_player_play
            AppIconResource.PlayerPause -> Res.drawable.ic_player_pause
            AppIconResource.PlayerAspectRatio -> Res.drawable.ic_player_aspect_ratio
            AppIconResource.PlayerSubtitles -> Res.drawable.ic_player_subtitles
            AppIconResource.PlayerAudioFilled -> Res.drawable.ic_player_audio_filled
            // ponytail: iOS placeholder icons — the new-player-UI port is Android-only, so no
            // ic_player_source/ic_player_episodes SVGs were added. Add real SVGs to composeResources
            // and swap these if iOS support is picked up.
            AppIconResource.PlayerSource -> Res.drawable.ic_player_subtitles
            AppIconResource.PlayerEpisodes -> Res.drawable.ic_player_aspect_ratio
            AppIconResource.LibraryAddPlus -> Res.drawable.library_add_plus
        }
    )
