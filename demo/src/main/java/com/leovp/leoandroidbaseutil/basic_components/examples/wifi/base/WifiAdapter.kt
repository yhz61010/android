package com.leovp.leoandroidbaseutil.basic_components.examples.wifi.base

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leovp.leoandroidbaseutil.R

/**
 * Author: Michael Leo
 * Date: 21-3-6 下午3:59
 */
class WifiAdapter : RecyclerView.Adapter<WifiAdapter.ItemViewHolder>() {
    private val dataArray: MutableList<WifiModel> = mutableListOf()
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
        currentItem.index = holder.adapterPosition + 1
        holder.bind(currentItem)
        holder.itemView.setOnClickListener {
            // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            onItemClickListener?.onItemClick(currentItem, holder.layoutPosition)
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = position.toLong()

    fun insertAdd(item: WifiModel) {
        dataArray.add(item)
        notifyItemRangeInserted(0, 1)
    }

    fun clearAndAddList(list: MutableList<WifiModel>) {
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
        fun onItemClick(item: WifiModel, position: Int)
    }

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val tvIndex: TextView = itemView.findViewById(R.id.txtIndex)
        private val tvWifiName: TextView = itemView.findViewById(R.id.txtWifiName)

        fun bind(item: WifiModel) {
            tvIndex.text = item.index.toString()
            tvWifiName.text = item.name
        }
    }
}