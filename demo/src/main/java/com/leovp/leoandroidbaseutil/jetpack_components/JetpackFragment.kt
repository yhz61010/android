package com.leovp.leoandroidbaseutil.jetpack_components

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.leoandroidbaseutil.ColorBaseAdapter
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.jetpack_components.examples.camerax.CameraXDemoActivity
import com.leovp.leoandroidbaseutil.jetpack_components.examples.navigation.NavigationMainActivity
import com.leovp.leoandroidbaseutil.jetpack_components.examples.recyclerview.RecyclerviewActivity
import com.leovp.leoandroidbaseutil.jetpack_components.examples.room.RoomActivity
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG

class JetpackFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_jetpack, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val colorBaseAdapter = ColorBaseAdapter(featureList.map { it.first }, colors)
        colorBaseAdapter.onItemClickListener = object : ColorBaseAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                startActivity(featureList[position].second, { intent -> intent.putExtra("title", featureList[position].first) })
            }
        }
        view.findViewById<RecyclerView>(R.id.recyclerView).run {
            setHasFixedSize(true)
            //            layoutManager = LinearLayoutManager(requireActivity())
            adapter = colorBaseAdapter
        }
    }

    override fun onDestroy() {
        //        CustomApplication.instance.closeDebugOutputFile()
        LogContext.log.i(ITAG, "onDestroy()")
        super.onDestroy()
        // In some cases, if you use saved some parameters in Application, when app exits,
        // the parameters may not be released. So we need to call AppUtil.exitApp(ctx)
        //        AppUtil.exitApp(this)
    }

    companion object {
        private val featureList = arrayOf(
            Pair("Room", RoomActivity::class.java),
            Pair("Recyclerview", RecyclerviewActivity::class.java),
            Pair("Navigation", NavigationMainActivity::class.java),
            Pair("CameraX", CameraXDemoActivity::class.java),
        )

        val colors = arrayOf(
            Color.parseColor("#80CBC4"),
            Color.parseColor("#80DEEA"),
            Color.parseColor("#81D4FA"),
            Color.parseColor("#90CAF9"),
            Color.parseColor("#9FA8DA"),
            Color.parseColor("#A5D6A7"),
            Color.parseColor("#B0BEC5"),
            Color.parseColor("#B39DDB"),
            Color.parseColor("#BCAAA4"),
            Color.parseColor("#C5E1A5"),
            Color.parseColor("#CE93D8"),
            Color.parseColor("#E6EE9C"),
            Color.parseColor("#EF9A9A"),
            Color.parseColor("#F48FB1"),
            Color.parseColor("#FFAB91"),
            Color.parseColor("#FFCC80"),
            Color.parseColor("#FFE082"),
            Color.parseColor("#FFF59D")
        )
    }
}