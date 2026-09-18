package com.nuvio.app.features.schedule

internal actual object LibraryScheduleClock {
    actual fun nowEpochMs(): Long = System.currentTimeMillis()
}
