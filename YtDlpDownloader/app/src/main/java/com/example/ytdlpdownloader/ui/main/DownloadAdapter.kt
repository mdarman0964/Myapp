package com.example.ytdlpdownloader.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ytdlpdownloader.R
import com.example.ytdlpdownloader.databinding.ItemDownloadBinding
import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.DownloadItem
import com.example.ytdlpdownloader.domain.model.DownloadStatus

class DownloadAdapter(
    private val onPauseClick: (DownloadItem) -> Unit,
    private val onResumeClick: (DownloadItem) -> Unit,
    private val onCancelClick: (DownloadItem) -> Unit,
    private val onRetryClick: (DownloadItem) -> Unit,
    private val onRemoveClick: (DownloadItem) -> Unit,
    private val onOpenClick: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadAdapter.DownloadViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DownloadViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class DownloadViewHolder(
        private val binding: ItemDownloadBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: DownloadItem) {
            binding.apply {
                // Title
                textTitle.text = item.displayTitle
                
                // Thumbnail
                if (item.thumbnailUrl != null) {
                    Glide.with(imageThumbnail)
                        .load(item.thumbnailUrl)
                        .placeholder(R.drawable.ic_video_placeholder)
                        .error(R.drawable.ic_video_placeholder)
                        .centerCrop()
                        .into(imageThumbnail)
                } else {
                    imageThumbnail.setImageResource(R.drawable.ic_video_placeholder)
                }
                
                // Format and Quality badge
                textFormat.text = item.format.name
                textQuality.text = item.quality.name
                
                // Status
                textStatus.text = item.status.displayName()
                textStatus.setTextColor(getStatusColor(item.status))
                
                // Progress
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = item.progress
                        textProgress.visibility = View.VISIBLE
                        textProgress.text = "${item.progress}%"
                        
                        // Speed and ETA
                        textSpeedEta.visibility = View.VISIBLE
                        textSpeedEta.text = buildString {
                            item.downloadSpeed?.let { append(it) }
                            item.eta?.let { append(" • ETA: $it") }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = item.progress
                        textProgress.visibility = View.VISIBLE
                        textProgress.text = "${item.progress}%"
                        textSpeedEta.visibility = View.GONE
                    }
                    else -> {
                        progressBar.visibility = View.GONE
                        textProgress.visibility = View.GONE
                        textSpeedEta.visibility = View.GONE
                    }
                }
                
                // Playlist indicator
                if (item.isPlaylistItem) {
                    textPlaylistInfo.visibility = View.VISIBLE
                    textPlaylistInfo.text = "${item.playlistIndex}/${item.playlistTotal}"
                } else {
                    textPlaylistInfo.visibility = View.GONE
                }
                
                // Action buttons
                setupActionButtons(item)
            }
        }
        
        private fun setupActionButtons(item: DownloadItem) {
            binding.apply {
                // Hide all buttons first
                buttonPause.visibility = View.GONE
                buttonResume.visibility = View.GONE
                buttonCancel.visibility = View.GONE
                buttonRetry.visibility = View.GONE
                buttonRemove.visibility = View.GONE
                buttonOpen.visibility = View.GONE
                
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        buttonPause.visibility = View.VISIBLE
                        buttonCancel.visibility = View.VISIBLE
                        buttonPause.setOnClickListener { onPauseClick(item) }
                        buttonCancel.setOnClickListener { onCancelClick(item) }
                    }
                    DownloadStatus.PAUSED -> {
                        buttonResume.visibility = View.VISIBLE
                        buttonCancel.visibility = View.VISIBLE
                        buttonResume.setOnClickListener { onResumeClick(item) }
                        buttonCancel.setOnClickListener { onCancelClick(item) }
                    }
                    DownloadStatus.PENDING -> {
                        buttonCancel.visibility = View.VISIBLE
                        buttonCancel.setOnClickListener { onCancelClick(item) }
                    }
                    DownloadStatus.COMPLETED -> {
                        buttonOpen.visibility = View.VISIBLE
                        buttonRemove.visibility = View.VISIBLE
                        buttonOpen.setOnClickListener { onOpenClick(item) }
                        buttonRemove.setOnClickListener { onRemoveClick(item) }
                    }
                    DownloadStatus.FAILED -> {
                        buttonRetry.visibility = View.VISIBLE
                        buttonRemove.visibility = View.VISIBLE
                        buttonRetry.setOnClickListener { onRetryClick(item) }
                        buttonRemove.setOnClickListener { onRemoveClick(item) }
                    }
                    DownloadStatus.CANCELLED -> {
                        buttonRemove.visibility = View.VISIBLE
                        buttonRemove.setOnClickListener { onRemoveClick(item) }
                    }
                }
            }
        }
        
        private fun getStatusColor(status: DownloadStatus): Int {
            val colorRes = when (status) {
                DownloadStatus.PENDING -> R.color.status_pending
                DownloadStatus.DOWNLOADING -> R.color.status_downloading
                DownloadStatus.PAUSED -> R.color.status_paused
                DownloadStatus.COMPLETED -> R.color.status_completed
                DownloadStatus.FAILED -> R.color.status_failed
                DownloadStatus.CANCELLED -> R.color.status_cancelled
            }
            return ContextCompat.getColor(binding.root.context, colorRes)
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
            return oldItem == newItem
        }
    }
}
