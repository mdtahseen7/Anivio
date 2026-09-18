package com.nuvio.app.features.schedule

/**
 * Wall-clock access for the schedule page. Reuses the library clock actuals (epoch milliseconds on
 * Android and iOS) rather than adding another expect/actual pair.
 */
internal expect object LibraryScheduleClock {
    fun nowEpochMs(): Long
}
