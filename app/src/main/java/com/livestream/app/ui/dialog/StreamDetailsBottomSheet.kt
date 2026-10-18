package com.livestream.app.ui.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.livestream.app.data.model.ExtractedStream
import com.livestream.app.databinding.DialogStreamInfoBinding

class StreamDetailsBottomSheet(
    private val channelName: String,
    private val stream: ExtractedStream
) : BottomSheetDialogFragment() {

    private var _binding: DialogStreamInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStreamInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvInfoChannelName.text = "Channel: $channelName (${stream.source})"
        binding.tvExtractedStreamUrl.text = stream.url
        binding.tvExtractedReferer.text = stream.referer.ifBlank { "None (Direct origin)" }

        binding.btnCopyStreamUrl.setOnClickListener {
            copyToClipboard("Stream URL", stream.url)
        }

        binding.btnCopyReferer.setOnClickListener {
            copyToClipboard("Referer Header", stream.referer)
        }

        binding.btnCloseStreamInfo.setOnClickListener {
            dismiss()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copied $label to clipboard!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
