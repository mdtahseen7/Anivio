package com.nuvio.app.features.discord

/** Android startup hook that installs the Discord Rich Presence ports into the common layer. */
object DiscordBootstrap {
    fun install() {
        DiscordAuth.install(DiscordAuthRepository)
    }
}
