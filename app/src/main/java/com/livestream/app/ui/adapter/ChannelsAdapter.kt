package com.livestream.app.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.livestream.app.R
import com.livestream.app.data.model.ChannelItem
import com.livestream.app.data.model.ChannelStatus
import com.livestream.app.databinding.ItemChannelBinding

class ChannelsAdapter(
    private val channels: List<ChannelItem>,
    private val onChannelClick: (ChannelItem) -> Unit
) : RecyclerView.Adapter<ChannelsAdapter.ChannelViewHolder>() {

    private var selectedChannelId: String? = null

    fun setSelectedChannel(channelId: String?) {
        val prevIndex = channels.indexOfFirst { it.id == selectedChannelId }
        val newIndex = channels.indexOfFirst { it.id == channelId }
        selectedChannelId = channelId

        if (prevIndex != -1) notifyItemChanged(prevIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    fun updateChannelStatus(channelId: String, status: ChannelStatus) {
        val index = channels.indexOfFirst { it.id == channelId }
        if (index != -1) {
            channels[index].status = status
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int = channels.size

    inner class ChannelViewHolder(private val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: ChannelItem) {
            val isSelected = channel.id == selectedChannelId

            binding.tvChannelTitle.text = channel.name
            binding.tvCategory.text = channel.category
            binding.tvQuality.text = channel.quality

            if (isSelected) {
                binding.channelContainer.setBackgroundResource(R.drawable.bg_channel_card_active)
            } else {
                binding.channelContainer.setBackgroundResource(R.drawable.bg_channel_card)
            }

            when (channel.status) {
                ChannelStatus.PLAYING -> {
                    binding.tvChannelStatus.text = "🔴 PLAYING"
                    binding.tvChannelStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.accent_red))
                }
                ChannelStatus.EXTRACTING -> {
                    binding.tvChannelStatus.text = "⏳ EXTRACTING"
                    binding.tvChannelStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.accent_yellow))
                }
                ChannelStatus.ERROR -> {
                    binding.tvChannelStatus.text = "⚠️ RETRY"
                    binding.tvChannelStatus.setTextColor(Color.parseColor("#EF4444"))
                }
                ChannelStatus.IDLE -> {
                    binding.tvChannelStatus.text = if (channel.isStreamValid()) "⚡ CACHED" else "TAP TO PLAY"
                    binding.tvChannelStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_secondary))
                }
            }

            binding.root.setOnClickListener {
                onChannelClick(channel)
            }
        }
    }
}
