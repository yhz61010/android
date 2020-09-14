package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview.base

import android.annotation.SuppressLint
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.florent37.viewanimator.ViewAnimator
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
    private var selectedItem = SparseArray<ItemBean>()
    var startDragListener: OnStartDragListener? = null
    var editMode: Boolean = false
        private set

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

        holder.cb.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedItem.put(holder.layoutPosition, dataArray[holder.layoutPosition])
            } else {
                selectedItem.delete(holder.layoutPosition)
            }
        }
        if (editMode) {
            ViewAnimator
                .animate(holder.cb)
                .fadeIn().onStart { holder.cb.visibility = View.VISIBLE }
                .duration(400)
                .start()

            ViewAnimator
                .animate(holder.primaryLL)
                .dp().translationX(0F, 5F)
                .decelerate()
                .duration(200)
                .start()

            ViewAnimator
                .animate(holder.ivDrag)
                .fadeIn()
                .duration(400).onStart { holder.ivDrag.visibility = View.VISIBLE }
                .start()
        } else {
            holder.cb.visibility = View.GONE
//            ViewAnimator
//                .animate(holder.cb)
//                .fadeOut().onStop { holder.cb.visibility = View.GONE  }
//                .duration(400)
//                .start()

            ViewAnimator
                .animate(holder.primaryLL)
                .dp().translationX(5F, 0F)
                .decelerate()
                .duration(200)
                .start()

            ViewAnimator
                .animate(holder.ivDrag)
                .fadeOut().onStop { holder.ivDrag.visibility = View.GONE }
                .duration(400)
                .start()
        }
    }

    override fun getItemCount(): Int = dataArray.size

    override fun getItemId(position: Int) = position.toLong()

    fun toggleEditMode() {
        notifyDataSetChanged()
        editMode = !editMode
    }

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

    /**
     * Implement this interface will let you drag item directly on an icon.
     */
    interface OnStartDragListener {
        /**
         * Call the following code in [onStartDrag]
         * ```kotlin
         * itemTouchHelper.startDrag(viewHolder)
         * ```
         */
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }
    // =============================================

    class ItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val txtView: TextView = itemView.findViewById(R.id.name)
        private val ivAlbum: ShapeableImageView = itemView.findViewById(R.id.ivAlbum)
        val ivDrag: ImageView = itemView.findViewById(R.id.ivDrag)
        val cb: CheckBox = itemView.findViewById(R.id.cb)
        val primaryLL: LinearLayout = itemView.findViewById(R.id.primaryLL)

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