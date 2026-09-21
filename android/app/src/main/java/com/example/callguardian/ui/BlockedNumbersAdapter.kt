package com.example.callguardian.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.callguardian.R
import com.example.callguardian.data.BlockedNumber
import com.example.callguardian.databinding.ItemBlockedNumberBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedNumbersAdapter(
    private val onUnblockClick: (BlockedNumber) -> Unit
) : ListAdapter<BlockedNumber, BlockedNumbersAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockedNumberBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBlockedNumberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BlockedNumber) {
            binding.tvPhoneNumber.text = item.phoneNumber
            binding.tvBlockedDate.text = binding.root.context.getString(
                R.string.blocked_at,
                dateFormat.format(Date(item.timestamp))
            )
            binding.tvReason.text = item.reason

            binding.btnUnblock.setOnClickListener {
                onUnblockClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BlockedNumber>() {
        override fun areItemsTheSame(oldItem: BlockedNumber, newItem: BlockedNumber): Boolean {
            return oldItem.phoneNumber == newItem.phoneNumber
        }

        override fun areContentsTheSame(oldItem: BlockedNumber, newItem: BlockedNumber): Boolean {
            return oldItem == newItem
        }
    }
}
