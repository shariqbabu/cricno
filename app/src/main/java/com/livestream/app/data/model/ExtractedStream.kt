package com.livestream.app.data.model

data class ExtractedStream(
    val url: String,
    val referer: String,
    val userAgent: String,
    val source: String = "SNIFFER",
    val timestamp: Long = System.currentTimeMillis()
)
