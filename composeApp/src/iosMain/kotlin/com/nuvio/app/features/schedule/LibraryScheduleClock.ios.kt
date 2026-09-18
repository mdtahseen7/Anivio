package com.nuvio.app.features.schedule

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

internal actual object LibraryScheduleClock {
    @OptIn(ExperimentalForeignApi::class)
    actual fun nowEpochMs(): Long = time(null) * 1000L
}
