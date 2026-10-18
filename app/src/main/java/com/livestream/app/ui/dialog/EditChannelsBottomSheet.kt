package com.livestream.app.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.livestream.app.data.model.ChannelItem
import com.livestream.app.data.repository.ChannelRepository
import com.livestream.app.databinding.DialogEditChannelsBinding

class EditChannelsBottomSheet(
    private val currentChannels: List<ChannelItem>,
    private val onChannelsUpdated: (List<ChannelItem>) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogEditChannelsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditChannelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate fields from current list
        if (currentChannels.isNotEmpty()) {
            binding.etNameCh1.setText(currentChannels[0].name)
            binding.etUrlCh1.setText(currentChannels[0].embedUrl)
        }
        if (currentChannels.size > 1) {
            binding.etNameCh2.setText(currentChannels[1].name)
            binding.etUrlCh2.setText(currentChannels[1].embedUrl)
        }
        if (currentChannels.size > 2) {
            binding.etNameCh3.setText(currentChannels[2].name)
            binding.etUrlCh3.setText(currentChannels[2].embedUrl)
        }
        if (currentChannels.size > 3) {
            binding.etNameCh4.setText(currentChannels[3].name)
            binding.etUrlCh4.setText(currentChannels[3].embedUrl)
        }
        if (currentChannels.size > 4) {
            binding.etNameCh5.setText(currentChannels[4].name)
            binding.etUrlCh5.setText(currentChannels[4].embedUrl)
        }
        if (currentChannels.size > 5) {
            binding.etNameCh6.setText(currentChannels[5].name)
            binding.etUrlCh6.setText(currentChannels[5].embedUrl)
        }

        binding.btnSaveChannels.setOnClickListener {
            val updatedList = mutableListOf<ChannelItem>()

            // 1
            val n1 = binding.etNameCh1.text.toString().trim()
            val u1 = binding.etUrlCh1.text.toString().trim()
            if (n1.isNotBlank() && u1.isNotBlank()) {
                updatedList.add(ChannelItem(id = "star4b", name = n1, embedUrl = u1, category = "Target Stream"))
            }

            // 2
            val n2 = binding.etNameCh2.text.toString().trim()
            val u2 = binding.etUrlCh2.text.toString().trim()
            if (n2.isNotBlank() && u2.isNotBlank()) {
                updatedList.add(ChannelItem(id = "starhindi", name = n2, embedUrl = u2, category = "Sports Live"))
            }

            // 3
            val n3 = binding.etNameCh3.text.toString().trim()
            val u3 = binding.etUrlCh3.text.toString().trim()
            if (n3.isNotBlank() && u3.isNotBlank()) {
                updatedList.add(ChannelItem(id = "star2", name = n3, embedUrl = u3, category = "Sports Live"))
            }

            // 4
            val n4 = binding.etNameCh4.text.toString().trim()
            val u4 = binding.etUrlCh4.text.toString().trim()
            if (n4.isNotBlank() && u4.isNotBlank()) {
                updatedList.add(ChannelItem(id = "sony1", name = n4, embedUrl = u4, category = "Sports Ten"))
            }

            // 5
            val n5 = binding.etNameCh5.text.toString().trim()
            val u5 = binding.etUrlCh5.text.toString().trim()
            if (n5.isNotBlank() && u5.isNotBlank()) {
                updatedList.add(ChannelItem(id = "willow", name = n5, embedUrl = u5, category = "Willow Live"))
            }

            // 6
            val n6 = binding.etNameCh6.text.toString().trim()
            val u6 = binding.etUrlCh6.text.toString().trim()
            if (n6.isNotBlank() && u6.isNotBlank()) {
                updatedList.add(ChannelItem(id = "ddsports", name = n6, embedUrl = u6, category = "National Sports"))
            }

            if (updatedList.isEmpty()) {
                Toast.makeText(requireContext(), "Please configure at least one channel", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ChannelRepository.saveChannels(requireContext(), updatedList)
            onChannelsUpdated(updatedList)
            Toast.makeText(requireContext(), "Channels saved successfully!", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.btnResetToSecrets.setOnClickListener {
            ChannelRepository.resetToDefaults(requireContext())
            val defaultList = ChannelRepository.getChannels(requireContext())
            onChannelsUpdated(defaultList)
            Toast.makeText(requireContext(), "Reset to GitHub Secrets / Default channels!", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
