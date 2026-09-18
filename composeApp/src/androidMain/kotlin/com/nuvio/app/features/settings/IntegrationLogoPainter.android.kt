package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.nuvio.app.R
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.introdb_favicon
import nuvio.composeapp.generated.resources.rating_imdb
import nuvio.composeapp.generated.resources.rating_tmdb
import org.jetbrains.compose.resources.painterResource as composePainterResource

@Composable
internal actual fun integrationLogoPainter(logo: IntegrationLogo): Painter =
    when (logo) {
        IntegrationLogo.Tmdb -> composePainterResource(Res.drawable.rating_tmdb)
        IntegrationLogo.MdbList -> painterResource(id = R.drawable.mdblist_logo)
        IntegrationLogo.IntroDb -> composePainterResource(Res.drawable.introdb_favicon)
        IntegrationLogo.Imdb -> composePainterResource(Res.drawable.rating_imdb)
        IntegrationLogo.Discord -> painterResource(id = R.drawable.discord_logo)
    }
