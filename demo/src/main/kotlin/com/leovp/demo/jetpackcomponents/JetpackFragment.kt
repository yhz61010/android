package com.leovp.demo.jetpackcomponents

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.androidbase.framework.BaseFragment
import com.leovp.demo.ColorBaseAdapter
import com.leovp.demo.databinding.FragmentJetpackBinding
import com.leovp.demo.jetpackcomponents.examples.camerax.CameraXDemoActivity
import com.leovp.demo.jetpackcomponents.examples.navigation.NavigationMainActivity
import com.leovp.demo.jetpackcomponents.examples.recyclerview.RecyclerviewActivity
import com.leovp.demo.jetpackcomponents.examples.room.RoomActivity
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG

class JetpackFragment : BaseFragment<FragmentJetpackBinding>() {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentJetpackBinding {
        return FragmentJetpackBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val colorBaseAdapter = ColorBaseAdapter(featureArray.map { it.first }.toTypedArray(), colors)
        colorBaseAdapter.onItemClickListener = object : ColorBaseAdapter.OnItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                startActivity(
                    featureArray[position].second,
                    { intent -> intent.putExtra("title", featureArray[position].first) }
                )
            }
        }
        binding.recyclerView.run {
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
        private val featureArray = arrayOf(
            Pair("Room", RoomActivity::class.java),
            Pair("Recyclerview", RecyclerviewActivity::class.java),
            Pair("Navigation", NavigationMainActivity::class.java),
            Pair("CameraX", CameraXDemoActivity::class.java)
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
        ).toIntArray()
    }
}
