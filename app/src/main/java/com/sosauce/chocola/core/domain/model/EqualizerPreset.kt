package com.sosauce.chocola.core.domain.model

import androidx.collection.FloatList

data class EqualizerPreset(
    val name: String,
    val emoji: String,
    val gains: FloatList
)
