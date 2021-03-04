package com.leovp.leoandroidbaseutil.basic_components.examples.bluetooth.base

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leovp.leoandroidbaseutil.R

/**
 * Author: Michael Leo
 * Date: 21-3-3 下午6:13
 */
class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.ItemViewHolder>() {
    private val dataArray: MutableList<DeviceModel> = mutableListOf()
    var onItemClickListener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_bluetooth_device_item, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
        val currentItem = dataArray[holder.adapterPosition]
//        LogContext.log.d(ITAG, "Current item[${holder.adapterPosition}]=${currentItem.toJsonString()}")
        holder.bind(currentItem)
        holder.itemView.setOnClickListener {
            // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            onItemClickListener?.onItemClick(holder.itemView, holder.layoutPosition)
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = position.toLong()

    fun insertAdd(item: DeviceModel) {
        dataArray.add(item)
        notifyItemRangeInserted(0, 1)
    }

    fun clearAndAddList(list: MutableList<DeviceModel>) {
        dataArray.clear()
        dataArray.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        dataArray.clear()
        notifyDataSetChanged()
    }

    // =============================================
    interface OnItemClickListener {
        fun onItemClick(view: View, position: Int) {}
    }


    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val tvIndex: TextView = itemView.findViewById(R.id.txtIndex)
        private val tvDeviceName: TextView = itemView.findViewById(R.id.txtDeviceName)
        private val tvBluetoothStrength: TextView = itemView.findViewById(R.id.txtBluetoothStrength)
        private val tvBluetoothMacAddress: TextView = itemView.findViewById(R.id.txtBluetoothMac)

        fun bind(item: DeviceModel) {
            tvIndex.text = item.index.toString()
            tvDeviceName.text = item.name ?: ""
            tvBluetoothStrength.text = item.rssi ?: ""
            tvBluetoothMacAddress.text = item.macAddress ?: ""
        }
    }
}