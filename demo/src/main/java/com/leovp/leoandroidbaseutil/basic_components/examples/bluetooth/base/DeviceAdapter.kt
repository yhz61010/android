package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.leovp.leoandroidbaseutil.databinding.RecyclerviewBluetoothDeviceItemBinding

/**
 * Author: Michael Leo
 * Date: 21-3-3 下午6:13
 */
class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.ItemViewHolder>() {
    private lateinit var binding: RecyclerviewBluetoothDeviceItemBinding

    private val dataArray: MutableList<DeviceModel> = mutableListOf()
    var onItemClickListener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        binding = RecyclerviewBluetoothDeviceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding.root)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
        val currentItem = dataArray[holder.adapterPosition]
//        LogContext.log.d(ITAG, "Current item[${holder.adapterPosition}]=${currentItem.toJsonString()}")
        currentItem.index = holder.adapterPosition + 1
        holder.bind(currentItem)
        holder.itemView.setOnClickListener {
            // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            onItemClickListener?.onItemClick(currentItem, holder.layoutPosition)
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = position.toLong()

    fun insertAdd(item: DeviceModel) {
        dataArray.add(item)
        notifyItemRangeInserted(0, 1)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearAndAddList(list: MutableList<DeviceModel>) {
        dataArray.clear()
        dataArray.addAll(list)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        dataArray.clear()
        notifyDataSetChanged()
    }

    // =============================================
    interface OnItemClickListener {
        fun onItemClick(item: DeviceModel, position: Int)
    }

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val binding = RecyclerviewBluetoothDeviceItemBinding.bind(view)

        fun bind(item: DeviceModel) {
            binding.txtIndex.text = item.index.toString()
            binding.txtDeviceName.text = item.name ?: ""
            binding.txtBluetoothStrength.text = item.rssi ?: ""
            binding.txtBluetoothMac.text = item.macAddress
        }
    }
}