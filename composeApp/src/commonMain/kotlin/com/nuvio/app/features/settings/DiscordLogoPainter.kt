package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Discord logo painter, platform-routed like the other integration logos: Android uses the XML
 * vector drawable (Compose `painterResource` cannot decode SVG resources on Android — that was a
 * crash), iOS keeps the compose SVG resource.
 */
@Composable
internal fun discordLogoPainter(): Painter = integrationLogoPainter(IntegrationLogo.Discord)
