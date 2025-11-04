package de.qsheltier.utils.time

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit.MILLISECONDS

fun Duration.toDurationString() = if (this == Duration.INFINITE) "∞" else toLong(MILLISECONDS).toDurationString()

fun Long.toDurationString() = units
	.map { ((this / it.timeUnit.toMillis(1)) % it.range) to it.sign }
	.dropWhile { it.first == 0L }
	.take(2)
	.ifEmpty { listOf(0L to "s") }
	.joinToString(" ") { "%d%s".format(it.first, it.second) }

private val units = listOf(
	DisplayTimeUnit(TimeUnit.DAYS, "d", Int.MAX_VALUE),
	DisplayTimeUnit(TimeUnit.HOURS, "h", 24),
	DisplayTimeUnit(TimeUnit.MINUTES, "m", 60),
	DisplayTimeUnit(TimeUnit.SECONDS, "s", 60)
)

private data class DisplayTimeUnit(val timeUnit: TimeUnit, val sign: String, val range: Int)
