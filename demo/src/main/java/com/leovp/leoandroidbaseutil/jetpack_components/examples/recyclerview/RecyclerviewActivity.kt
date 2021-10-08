package com.leovp.leoandroidbaseutil.jetpack_components.examples.recyclerview

import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.Keep
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo
import com.leovp.androidbase.exts.android.action
import com.leovp.androidbase.exts.android.snack
import com.leovp.androidbase.exts.android.toast
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityRecyclerviewBinding
import com.leovp.leoandroidbaseutil.jetpack_components.examples.recyclerview.base.SimpleAdapter
import com.leovp.leoandroidbaseutil.jetpack_components.examples.recyclerview.base.SimpleItemTouchCallback


class RecyclerviewActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityRecyclerviewBinding

    private lateinit var simpleAdapter: SimpleAdapter
    private lateinit var itemTouchHandler: SimpleItemTouchCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecyclerviewBinding.inflate(layoutInflater).apply { setContentView(root) }

        val featureList = mutableListOf<ItemBean>()
        for (i in 0 until 300) {
            featureList.add(
                ItemBean(
                    SystemClock.elapsedRealtimeNanos(),
                    "Demo String ${i + 1}",
                    "http://temp.leovp.com/temp/number_counter/${i % 100 + 1}.png"
                )
            )
        }

        simpleAdapter = SimpleAdapter(featureList)
        simpleAdapter.onItemClickListener = object : SimpleAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                toast("You click position: $position")
                findViewById<TextView>(R.id.tv_select_num).text = "${simpleAdapter.selectedItems.size}"
            }

            override fun onItemLongClick(view: View, position: Int) {
                toast("Long click on position: $position")
            }
        }
        binding.recyclerView.run {
//            setHasFixedSize(true)
//            layoutManager = LinearLayoutManager(requireActivity())
            adapter = simpleAdapter
        }

        itemTouchHandler = object : SimpleItemTouchCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapter = binding.recyclerView.adapter as SimpleAdapter
                adapter.removeAt(viewHolder.adapterPosition)
                findViewById<TextView>(R.id.tv_select_num).text = "${simpleAdapter.selectedItems.size}"
                binding.rootLL.snack("Undo last delete?") {
                    action("Undo") { adapter.undo() }
                }
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHandler)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        simpleAdapter.startDragListener = object : SimpleAdapter.OnStartDragListener {
            override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
                itemTouchHelper.startDrag(viewHolder)
            }
        }

        findViewById<Button>(R.id.select_all).setOnClickListener {
            it.tag = (!((it.tag as? Boolean) ?: false))
            findViewById<TextView>(R.id.tv_select_num).text = "${simpleAdapter.toggleSelectAll(!((it.tag) as Boolean))}"
        }
        findViewById<Button>(R.id.btn_delete).setOnClickListener {
            simpleAdapter.multipleDelete()
            findViewById<TextView>(R.id.tv_select_num).text = "0"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.recyclerview_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val editItem = menu.findItem(R.id.edit)
        val cancelItem = menu.findItem(R.id.cancel)
        if (simpleAdapter.editMode) {
            editItem.isVisible = false
            cancelItem.isVisible = true
        } else {
            editItem.isVisible = true
            cancelItem.isVisible = false
        }

        val listItem = menu.findItem(R.id.change_to_list)
        val gridItem = menu.findItem(R.id.change_to_grid)
        if (simpleAdapter.displayStyle == SimpleAdapter.STYLE_LIST) {
            listItem.isVisible = false
            gridItem.isVisible = true
            itemTouchHandler.setDefaultDragDirs(ItemTouchHelper.UP or ItemTouchHelper.DOWN)
        } else {
            listItem.isVisible = true
            gridItem.isVisible = false
            itemTouchHandler.setDefaultDragDirs(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.add_item -> {
                simpleAdapter.insertAdd(
                    0,
                    ItemBean(
                        SystemClock.elapsedRealtimeNanos(),
                        "Add-${SystemClock.elapsedRealtime()}",
                        "https://picsum.photos/80?random=${SystemClock.elapsedRealtime()}"
                    )
                )
                binding.recyclerView.scrollToPosition(0)
            }
            R.id.edit, R.id.cancel -> {
                simpleAdapter.toggleEditMode()
                invalidateOptionsMenu()
                if (simpleAdapter.editMode) {
                    YoYo.with(Techniques.SlideInUp)
                        .duration(400)
                        .onStart { binding.llMycollectionBottomDialog.llRvBottomSheetSelect.visibility = View.VISIBLE }
                        .playOn(binding.llMycollectionBottomDialog.llRvBottomSheetSelect)
                } else {
                    YoYo.with(Techniques.SlideOutDown)
                        .duration(400)
                        .onEnd { binding.llMycollectionBottomDialog.llRvBottomSheetSelect.visibility = View.GONE }
                        .playOn(binding.llMycollectionBottomDialog.llRvBottomSheetSelect)
                }
            }
            R.id.change_to_grid, R.id.change_to_list -> {
                simpleAdapter.toggleDisplayMode()
                invalidateOptionsMenu()
                changeDisplayStyle()
            }
        }
        return true
    }

    private fun changeDisplayStyle() {
        when (simpleAdapter.displayStyle) {
            SimpleAdapter.STYLE_LIST -> {
                binding.recyclerView.layoutManager = LinearLayoutManager(this)
            }
            SimpleAdapter.STYLE_GRID -> {
                binding.recyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.grid_columns))
            }
        }
    }
}

@Keep
data class ItemBean(val id: Long, val title: String, val imageUrl: String) : BaseMultipleCheckedItem()

open class BaseMultipleCheckedItem {
    var checked = false
}