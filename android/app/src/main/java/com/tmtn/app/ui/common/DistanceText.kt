package com.tmtn.app.ui.common

/** Sensor values and targets already use meters. Truncate for display, never round up to a goal. */
fun formatDistanceMeters(meters: Float): String =
    "${if (meters.isFinite()) meters.coerceAtLeast(0f).toLong() else 0L} m"
