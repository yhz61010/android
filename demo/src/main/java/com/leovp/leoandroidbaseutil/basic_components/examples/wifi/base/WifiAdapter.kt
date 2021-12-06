package com.leovp.leoandroidbaseutil.basic_components.examples.wifi.base

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.databinding.RecyclerviewWifiItemBinding

/**
 * Author: Michael Leo
 * Date: 21-3-6 下午3:59
 */
class WifiAdapter(private val currentSsid: String?) : RecyclerView.Adapter<WifiAdapter.ItemViewHolder>() {
    private lateinit var binding: RecyclerviewWifiItemBinding

    private val dataArray: MutableList<WifiModel> = mutableListOf()
    var onItemClickListener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        binding = RecyclerviewWifiItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding.root)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
        val currentItem = dataArray[holder.adapterPosition]
//        LogContext.log.d(ITAG, "Current item[${holder.adapterPosition}]=${currentItem.toJsonString()}")
        currentItem.index = holder.adapterPosition + 1
        holder.bind(currentItem)
        val cardView = holder.view.findViewById<CardView>(R.id.cardView)
        if (currentSsid == currentItem.name) {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.view.context, R.color.purple_200))
            val innerViewGroup = cardView.children.first() as ViewGroup
            innerViewGroup.children.filter { it is TextView }.forEach { (it as TextView).setTextColor(Color.WHITE) }
        } else {
            cardView.setCardBackgroundColor(ContextCompat.getColor(holder.view.context, android.R.color.white))
            val innerViewGroup = cardView.children.first() as ViewGroup
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

    @SuppressLint("NotifyDataSetChanged")
    fun clearAndAddList(list: MutableList<WifiModel>) {
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
        fun onItemClick(item: WifiModel, position: Int)
    }

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val binding = RecyclerviewWifiItemBinding.bind(view)

        fun bind(item: WifiModel) {
            binding.txtIndex.text = item.index.toString()
            binding.txtWifiName.text = item.name
            binding.tvBssid.text = item.bssid
            binding.tvWifiLevel.text = item.signalLevel.toString()
            binding.tvFreq.text = item.freq.toString()
        }
    }
}