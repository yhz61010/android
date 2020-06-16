package com.ho1ho.leoandroidbaseutil

import android.app.Activity
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
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.CLog
import com.ho1ho.leoandroidbaseutil.ui.Camera2LiveActivity
import com.ho1ho.leoandroidbaseutil.ui.DeviceInfoActivity
import com.ho1ho.leoandroidbaseutil.ui.LogActivity
import com.ho1ho.leoandroidbaseutil.ui.NetworkMonitorActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        CLog.w(ITAG, "=====> onCreate <=====")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridView.adapter = ColorBaseAdapter(this)
    }

    override fun onStop() {
        CLog.w(ITAG, "=====> onStop <=====")
        CLog.flushLog(false)
        super.onStop()
    }

    override fun onDestroy() {
        CLog.w(ITAG, "=====> onDestroy <=====")
        // We want to test changing device orientation, the onDestroy() may be called.
        // So we do not close log temporarily.
        // Check LogActivity onDestroy() comments
//        CLog.closeLog()
        super.onDestroy()
        // In some cases, if you use saved some parameters in Application, when app exits,
        // the parameters may not be released. So we need to call AppUtil.exitApp(ctx)
//        AppUtil.exitApp(this)
    }

    class ColorBaseAdapter(private val ctx: Activity) : BaseAdapter() {
        internal class ViewHolder {
            lateinit var textView: TextView
            lateinit var cardView: CardView
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val viewHolder: ViewHolder
            val noneConvertView: View

            if (convertView == null) {
                noneConvertView = LayoutInflater.from(parent?.context).inflate(R.layout.grid_item, parent, false)
                viewHolder = ViewHolder()
                viewHolder.cardView = noneConvertView.findViewById(R.id.cardView)
                viewHolder.textView = noneConvertView.findViewById(R.id.name)
                noneConvertView.tag = viewHolder
            } else {
                noneConvertView = convertView
                viewHolder = noneConvertView.tag as ViewHolder
            }
            viewHolder.textView.text = featureList[position].first
            viewHolder.cardView.setCardBackgroundColor(color[color.indices.random()])
            viewHolder.cardView.setOnClickListener {
                val intent = Intent(ctx, featureList[position].second)
                intent.putExtra("title", featureList[position].first)
                ctx.startActivity(intent)
            }
            return noneConvertView
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