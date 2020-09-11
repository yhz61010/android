package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.ho1ho.leoandroidbaseutil.R

/**
 * Author: Michael Leo
 * Date: 2020/9/11 上午11:24
 */
class SimpleAdapter(private val dataArray: MutableList<ItemBean>) :
    RecyclerView.Adapter<SimpleAdapter.ItemViewHolder>() {
    var onItemClickListener: OnItemClickListener? = null
    private var lastDeletedItem: Pair<Int, ItemBean>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_demo_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(dataArray.get(position))
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

    fun removeAt(position: Int) {
        lastDeletedItem = Pair(position, dataArray.removeAt(position))
        notifyItemRemoved(position)
    }

    fun undo() {
        lastDeletedItem?.let {
            dataArray.add(it.first, it.second)
            notifyItemRangeInserted(it.first, 1)
            lastDeletedItem = null
        }
    }

    // =============================================
    interface OnItemClickListener {
        fun onItemClick(view: View, position: Int) {}
        fun onItemLongClick(view: View, position: Int) {}
    }
    // =============================================

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val txtView: TextView = itemView.findViewById(R.id.name)
        private val iv: ShapeableImageView = itemView.findViewById(R.id.ivAlbum)

        fun bind(item: ItemBean) {
            txtView.text = item.title
            iv.shapeAppearanceModel = iv.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, 40F)
                .build()
            Glide.with(view).load(item.imageUrl).into(iv)
        }
    }
}