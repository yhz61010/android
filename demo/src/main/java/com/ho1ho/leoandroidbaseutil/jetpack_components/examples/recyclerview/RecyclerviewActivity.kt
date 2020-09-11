package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ho1ho.androidbase.exts.action
import com.ho1ho.androidbase.exts.snack
import com.ho1ho.androidbase.utils.ui.ToastUtil
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import kotlinx.android.synthetic.main.activity_recyclerview.*


class RecyclerviewActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recyclerview)

        val featureList = mutableListOf<ItemBean>()
        for (i in 0..100) {
            featureList.add(ItemBean("Demo String $i", "https://picsum.photos/80?random=$i"))
        }

        val simpleAdapter = SimpleAdapter(featureList)
        simpleAdapter.onItemClickListener = object : SimpleAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                ToastUtil.showToast("You click position: $position")
            }

            override fun onItemLongClick(view: View, position: Int) {
                ToastUtil.showToast("Long click on position: $position")
            }
        }
        recyclerView.run {
            setHasFixedSize(true)
//            layoutManager = LinearLayoutManager(requireActivity())
            adapter = simpleAdapter
        }

        val swipeHandler = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapter = recyclerView.adapter as SimpleAdapter
                adapter.removeAt(viewHolder.adapterPosition)
                rootLL.snack("Undo delete?") {
                    action("Undo") { adapter.undo() }
                }
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}

data class ItemBean(val title: String, val imageUrl: String)