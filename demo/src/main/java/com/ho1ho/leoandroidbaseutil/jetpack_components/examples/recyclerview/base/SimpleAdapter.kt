package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview.base

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview.ItemBean


/**
 * Author: Michael Leo
 * Date: 2020/9/11 上午11:24
 */
class SimpleAdapter(private val dataArray: MutableList<ItemBean>) : RecyclerView.Adapter<SimpleAdapter.ItemViewHolder>() {
    var onItemClickListener: OnItemClickListener? = null
    private var lastDeletedItem: Pair<Int, ItemBean>? = null
    var startDragListener: OnStartDragListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.recyclerview_demo_item, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(dataArray[position])
        holder.itemView.setOnClickListener {
            onItemClickListener?.onItemClick(holder.itemView, holder.layoutPosition)
        }

        holder.itemView.setOnLongClickListener {
            onItemClickListener?.onItemLongClick(holder.itemView, holder.layoutPosition)
            true
        }
        holder.ivDrag.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                startDragListener?.onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = position.toLong()

    fun insertAdd(position: Int, item: ItemBean) {
        dataArray.add(position, item)
        notifyItemRangeInserted(position, 1)
    }

    fun itemMove(srcPos: Int, targetPos: Int) {
        val sourceItem = dataArray.removeAt(srcPos)
        dataArray.add(targetPos, sourceItem)
        notifyItemMoved(srcPos, targetPos)
    }

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

    interface OnStartDragListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }
    // =============================================

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val txtView: TextView = itemView.findViewById(R.id.name)
        private val ivAlbum: ShapeableImageView = itemView.findViewById(R.id.ivAlbum)
        val ivDrag: ImageView = itemView.findViewById(R.id.ivDrag)

        fun bind(item: ItemBean) {
            txtView.text = item.title
            ivAlbum.shapeAppearanceModel = ivAlbum.shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, 30F)
                .build()
            Glide.with(view).load(item.imageUrl).into(ivAlbum)
        }
    }
}