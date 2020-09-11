package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.recyclerview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
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
    }

    class SimpleAdapter(private val dataArray: List<ItemBean>) :
        RecyclerView.Adapter<SimpleAdapter.ItemViewHolder>() {
        var onItemClickListener: OnItemClickListener? = null

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
}

data class ItemBean(val title: String, val imageUrl: String)