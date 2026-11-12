package com.sosauce.chocola.core.data.bakery.result

data class DayTotal(
    val epochDay: Long,
    val totalListens: Int,
    val totalListenedMillis: Long
)
