package com.leovp.demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

/**
 * Author: Michael Leo
 * Date: 2020/9/4 上午10:56
 */
class ColorBaseAdapter(private val dataArray: List<String>, private val colorArray: Array<Int>) :
    RecyclerView.Adapter<ColorBaseAdapter.ItemViewHolder>() {
    var onItemClickListener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_main_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(dataArray[position])
        holder.itemView.setOnClickListener {
            onItemClickListener?.onItemClick(holder.itemView, holder.layoutPosition)
        }

        holder.itemView.setOnLongClickListener {
            onItemClickListener?.onItemLongClick(holder.itemView, holder.layoutPosition)
            true
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = position.toLong()

    // =============================================
    interface OnItemClickListener {
        fun onItemClick(view: View, position: Int) {}
        fun onItemLongClick(view: View, position: Int) {}
    }
    // =============================================

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtView: TextView = itemView.findViewById(R.id.name)
        private val cardView: CardView = itemView.findViewById(R.id.cardView)

        fun bind(title: String) {
            txtView.text = title
            cardView.setCardBackgroundColor(colorArray[colorArray.indices.random()])
        }
    }
}