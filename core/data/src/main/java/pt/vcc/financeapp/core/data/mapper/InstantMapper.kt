package pt.vcc.financeapp.core.data.mapper

import java.time.Instant

internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

internal fun Long?.toInstantOrNull(): Instant? = this?.let(Instant::ofEpochMilli)
