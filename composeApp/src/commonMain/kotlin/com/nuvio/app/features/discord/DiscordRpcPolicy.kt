package com.nuvio.app.features.discord

/**
 * Discord Rich Presence is driven by the account-token gateway client, which only ships on
 * Android. Other platforms hide the settings entry instead of showing a dead-end card.
 */
internal expect val discordRichPresenceSupported: Boolean
