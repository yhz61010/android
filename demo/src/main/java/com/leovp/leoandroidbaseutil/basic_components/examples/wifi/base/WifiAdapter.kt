package com.leovp.leoandroidbaseutil.basic_components.examples.wifi.base

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.leovp.androidbase.exts.android.app
import com.leovp.leoandroidbaseutil.R
import kotlinx.android.synthetic.main.recyclerview_wifi_item.view.*

/**
 * Author: Michael Leo
 * Date: 21-3-6 下午3:59
 */
class WifiAdapter(private val currentSsid: String?) : RecyclerView.Adapter<WifiAdapter.ItemViewHolder>() {
    private val dataArray: MutableList<WifiModel> = mutableListOf()
    var onItemClickListener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_wifi_item, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
        val currentItem = dataArray[holder.adapterPosition]
//        LogContext.log.d(ITAG, "Current item[${holder.adapterPosition}]=${currentItem.toJsonString()}")
        currentItem.index = holder.adapterPosition + 1
        holder.bind(currentItem)
        if (currentSsid == currentItem.name) {
            holder.itemView.cardView.setCardBackgroundColor(ContextCompat.getColor(app, R.color.purple_200))
            val innerViewGroup = holder.itemView.cardView.children.first() as ViewGroup
            innerViewGroup.children.filter { it is TextView }.forEach { (it as TextView).setTextColor(Color.WHITE) }
        } else {
            holder.itemView.cardView.setCardBackgroundColor(ContextCompat.getColor(app, android.R.color.white))
            val innerViewGroup = holder.itemView.cardView.children.first() as ViewGroup
            innerViewGroup.children.filter { it is TextView }.forEach { (it as TextView).setTextColor(Color.BLACK) }
        }
        holder.itemView.setOnClickListener {
            // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            onItemClickListener?.onItemClick(currentItem, holder.layoutPosition)
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = position.toLong()

//    override fun getItemViewType(position: Int) = position

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
        private val tvWifiSsid: TextView = itemView.findViewById(R.id.txtWifiName)
        private val tvWifiBssid: TextView = itemView.findViewById(R.id.tvBssid)
        private val tvWifiLevel: TextView = itemView.findViewById(R.id.tvWifiLevel)
        private val tvWifiFreq: TextView = itemView.findViewById(R.id.tvFreq)

        fun bind(item: WifiModel) {
            tvIndex.text = item.index.toString()
            tvWifiSsid.text = item.name
            tvWifiBssid.text = item.bssid
            tvWifiLevel.text = item.signalLevel.toString()
            tvWifiFreq.text = item.freq.toString()
        }
    }
}