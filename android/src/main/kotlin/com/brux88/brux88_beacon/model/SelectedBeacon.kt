package com.brux88.brux88_beacon.model

data class SelectedBeacon(
    val uuid: String,        // Identifier id1 (UUID)
    val major: String? = null, // Identifier id2 (Major) - opzionale
    val minor: String? = null, // Identifier id3 (Minor) - opzionale
    val name: String? = null   // Nome opzionale per l'identificazione user-friendly
)