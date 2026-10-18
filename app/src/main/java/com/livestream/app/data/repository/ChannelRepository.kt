package com.livestream.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.livestream.app.BuildConfig
import com.livestream.app.data.model.ChannelItem

object ChannelRepository {

    private const val PREFS_NAME = "livestream_channels_prefs"
    private const val KEY_CUSTOM_CHANNELS = "custom_channels_json"
    private val gson = Gson()

    fun getChannels(context: Context): List<ChannelItem> {
        val prefs = getPrefs(context)
        val savedJson = prefs.getString(KEY_CUSTOM_CHANNELS, null)

        if (!savedJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<ChannelItem>>() {}.type
                val customList: List<ChannelItem>? = gson.fromJson(savedJson, type)
                if (!customList.isNullOrEmpty()) {
                    return customList.map { item ->
                        if (item.embedUrl.contains(".zu/")) {
                            item.copy(embedUrl = item.embedUrl.replace(".zu/", ".ru/"))
                        } else item
                    }
                }
            } catch (_: Exception) {}
        }

        // Check if full JSON secret is supplied via BuildConfig
        if (BuildConfig.CHANNELS_JSON.isNotBlank()) {
            try {
                val type = object : TypeToken<List<ChannelItem>>() {}.type
                val secretList: List<ChannelItem>? = gson.fromJson(BuildConfig.CHANNELS_JSON, type)
                if (!secretList.isNullOrEmpty()) {
                    return secretList
                }
            } catch (_: Exception) {}
        }

        // Build default 6 channels from BuildConfig / GitHub Secrets
        val channels = mutableListOf<ChannelItem>()

        channels.add(
            ChannelItem(
                id = "star4b",
                name = BuildConfig.CHANNEL_1_NAME,
                embedUrl = BuildConfig.CHANNEL_1_URL,
                category = "Cricket Live",
                quality = "1080p HD"
            )
        )

        channels.add(
            ChannelItem(
                id = "starhindi",
                name = BuildConfig.CHANNEL_2_NAME,
                embedUrl = BuildConfig.CHANNEL_2_URL,
                category = "Hindi Cricket",
                quality = "1080p HD"
            )
        )

        channels.add(
            ChannelItem(
                id = "star2",
                name = BuildConfig.CHANNEL_3_NAME,
                embedUrl = BuildConfig.CHANNEL_3_URL,
                category = "Sports Live",
                quality = "1080p HD"
            )
        )

        channels.add(
            ChannelItem(
                id = "sony1",
                name = BuildConfig.CHANNEL_4_NAME,
                embedUrl = BuildConfig.CHANNEL_4_URL,
                category = "Sports Ten",
                quality = "1080p HD"
            )
        )

        channels.add(
            ChannelItem(
                id = "willow",
                name = BuildConfig.CHANNEL_5_NAME,
                embedUrl = BuildConfig.CHANNEL_5_URL,
                category = "Willow Live",
                quality = "1080p HD"
            )
        )

        channels.add(
            ChannelItem(
                id = "ddsports",
                name = BuildConfig.CHANNEL_6_NAME,
                embedUrl = BuildConfig.CHANNEL_6_URL,
                category = "National Sports",
                quality = "1080p HD"
            )
        )

        return channels
    }

    fun saveChannels(context: Context, channels: List<ChannelItem>) {
        val sanitized = channels.map {
            it.copy(
                extractedStreamUrl = null,
                referer = null,
                status = com.livestream.app.data.model.ChannelStatus.IDLE,
                lastExtractedTime = 0L
            )
        }
        val json = gson.toJson(sanitized)
        getPrefs(context).edit().putString(KEY_CUSTOM_CHANNELS, json).apply()
    }

    fun resetToDefaults(context: Context) {
        getPrefs(context).edit().remove(KEY_CUSTOM_CHANNELS).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
