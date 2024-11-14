package com.leovp.demo.jetpackcomponents.examples.recyclerview.base

/**
 * Author: Michael Leo
 * Date: 2020/9/11 上午10:35
 */
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.leovp.demo.R
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

abstract class SimpleItemTouchCallback(context: Context) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    // or ItemTouchHelper.UP or ItemTouchHelper.DOWN
    ItemTouchHelper.LEFT
) {
    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_outline_delete_forever)!!
    private val intrinsicWidth = deleteIcon.intrinsicWidth
    private val intrinsicHeight = deleteIcon.intrinsicHeight
    private val background = ColorDrawable()
    private val backgroundColor = ContextCompat.getColor(context, R.color.purple_500)
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val adapter = recyclerView.adapter as SimpleAdapter
        return if (adapter.editMode) {
            /**
             * To disable "swipe" for specific item return 0 here.
             * For example:
             * if (viewHolder?.itemViewType == YourAdapter.SOME_TYPE) return 0
             * if (viewHolder?.adapterPosition == 0) return 0
             */
            // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            LogContext.log.d(
                ITAG,
                "viewHolder.adapterPosition=${viewHolder.bindingAdapterPosition}"
            )
            super.getMovementFlags(recyclerView, viewHolder)
        } else {
            0
        }
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val adapter = recyclerView.adapter as SimpleAdapter
        return if (adapter.editMode) {
            // https://medium.com/@noureldeen.abouelkassem/difference-between-position-getadapterposition-and-getlayoutposition-in-recyclerview-80279a2711d1
            adapter.itemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            true
        } else {
            false
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        // Draw the red delete background
        background.color = backgroundColor
        background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
        background.draw(c)

        // Calculate position of delete icon
        val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
        val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
        val deleteIconLeft = itemView.right - deleteIconMargin - intrinsicWidth
        val deleteIconRight = itemView.right - deleteIconMargin
        val deleteIconBottom = deleteIconTop + intrinsicHeight

        // Draw the delete icon
        deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
        deleteIcon.setTint(Color.WHITE)
        deleteIcon.draw(c)

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun clearCanvas(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        c.drawRect(left, top, right, bottom, clearPaint)
    }
}
