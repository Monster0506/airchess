package com.airchess.platform

import kotlin.time.TimeSource

private val origin = TimeSource.Monotonic.markNow()

/** Milliseconds on a clock that never jumps backwards; only differences are meaningful. */
fun monotonicMillis(): Long = origin.elapsedNow().inWholeMilliseconds
