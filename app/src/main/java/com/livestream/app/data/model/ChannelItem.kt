package com.livestream.app.data.model

enum class ChannelStatus {
    IDLE,
    EXTRACTING,
    PLAYING,
    ERROR
}

data class ChannelItem(
    val id: String,
    var name: String,
    var embedUrl: String,
    var category: String = "Sports",
    var quality: String = "1080p HD",
    var logoUrl: String = "",
    var extractedStreamUrl: String? = null,
    var referer: String? = null,
    var status: ChannelStatus = ChannelStatus.IDLE,
    var lastExtractedTime: Long = 0L
) {
    fun isStreamValid(): Boolean {
        if (extractedStreamUrl.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        // Cache stream for 15 minutes before re-extracting
        return (now - lastExtractedTime) < 15 * 60 * 1000L
    }
}
