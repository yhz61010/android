package com.ho1ho.leoandroidbaseutil

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.leoandroidbaseutil.ui.Camera2LiveActivity
import com.ho1ho.leoandroidbaseutil.ui.DeviceInfoActivity
import com.ho1ho.leoandroidbaseutil.ui.LogActivity
import com.ho1ho.leoandroidbaseutil.ui.NetworkMonitorActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridView.adapter = ColorBaseAdapter(this)
    }

    override fun onDestroy() {
        // Check LogActivity onDestroy() comments
        CLog.closeLog()
        super.onDestroy()
//        AppUtil.exitApp(this)
    }

    class ColorBaseAdapter(private val ctx: Activity) : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val inflater = parent?.context?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.grid_item, null)

            val cardView = view.findViewById<CardView>(R.id.cardView)
            val tv = view.findViewById<TextView>(R.id.name)
            tv.text = featureList[position].first
            cardView.setCardBackgroundColor(color[color.indices.random()])
            cardView.setOnClickListener {
                val intent = Intent(ctx, featureList[position].second)
                intent.putExtra("title", featureList[position].first)
                ctx.startActivity(intent)
            }
            return view
        }

        override fun getItem(position: Int): Any {
            return featureList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return featureList.size
        }
    }

    companion object {
        private val featureList = arrayOf(
            Pair("Device Info", DeviceInfoActivity::class.java),
            Pair("Log", LogActivity::class.java),
            Pair("Network Monitor", NetworkMonitorActivity::class.java),
            Pair("Camera2", Camera2LiveActivity::class.java)
        )

        private val color = arrayOf(
            Color.parseColor("#EF9A9A"),
            Color.parseColor("#CE93D8"),
            Color.parseColor("#9FA8DA"),
            Color.parseColor("#B39DDB"),
            Color.parseColor("#81D4FA"),
            Color.parseColor("#80CBC4"),
            Color.parseColor("#F48FB1"),
            Color.parseColor("#90CAF9"),
            Color.parseColor("#80DEEA"),
            Color.parseColor("#C5E1A5"),
            Color.parseColor("#E6EE9C"),
            Color.parseColor("#FFE082"),
            Color.parseColor("#FFCC80"),
            Color.parseColor("#A5D6A7"),
            Color.parseColor("#BCAAA4"),
            Color.parseColor("#FFF59D"),
            Color.parseColor("#FFAB91"),
            Color.parseColor("#B0BEC5")
        )
    }
}
