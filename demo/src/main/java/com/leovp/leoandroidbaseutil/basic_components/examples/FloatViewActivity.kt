package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.leovp.androidbase.exts.android.setOnSingleClickListener
import com.leovp.androidbase.exts.android.toast
import com.leovp.floatview_sdk.FloatView
import com.leovp.floatview_sdk.base.AutoDock
import com.leovp.floatview_sdk.base.StickyEdge
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityFloatViewBinding
import java.util.*

class FloatViewActivity : BaseDemonstrationActivity() {
    private lateinit var binding: ActivityFloatViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFloatViewBinding.inflate(layoutInflater).apply { setContentView(root) }

        binding.btnChange.setOnSingleClickListener {
            FloatView.getCustomLayout("f1")?.findViewById<TextView>(R.id.tvText)?.text = "I'm f1 in ${Random().nextInt(100)}"
            FloatView.getCustomLayout("f2")?.findViewById<TextView>(R.id.tvText)?.text = "I'm f2 in ${Random().nextInt(100)}"
        }

        FloatView.with(this)
            .setTag("f1")
            .setDragOverStatusBar(false)
            .setX(50)
            .setY(220)
            .setLayout(R.layout.floatview) { v ->
                v.findViewById<TextView>(R.id.tvText).text = "FloatWindow 1"
                v.findViewById<View>(R.id.floatViewBtn).setOnSingleClickListener { toast("Win1 Button") }
                v.findViewById<View>(R.id.linearLayout).setOnSingleClickListener { toast("Win1 Image") }
            }
            .show()

        FloatView.with(this)
            .setTag("f2")
            .setDragOverStatusBar(true)
            .setEdgeMargin(20)
            .setX(100)
            .setY(600)
            .setLayout(R.layout.floatview) { v ->
                v.findViewById<TextView>(R.id.tvText).text = "FloatWindow 2"
                v.findViewById<View>(R.id.floatViewBtn).setOnSingleClickListener { toast("Win2 Button") }
                v.findViewById<View>(R.id.linearLayout).setOnSingleClickListener { toast("Win2 Image") }
            }
            .show()

        binding.rgSticky.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.rbStickyNone -> FloatView.setStickyEdge(StickyEdge.NONE, "f1")
                R.id.rbStickyLeft -> FloatView.setStickyEdge(StickyEdge.LEFT, "f1")
                R.id.rbStickyRight -> FloatView.setStickyEdge(StickyEdge.RIGHT, "f1")
                R.id.rbStickyTop -> FloatView.setStickyEdge(StickyEdge.TOP, "f1")
                R.id.rbStickyBottom -> FloatView.setStickyEdge(StickyEdge.BOTTOM, "f1")
            }
        }

        binding.rgAutoDock.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.rbAutoDockNone -> FloatView.setAutoDock(AutoDock.NONE, "f2")
                R.id.rbAutoDockLeft -> FloatView.setAutoDock(AutoDock.LEFT, "f2")
                R.id.rbAutoDockRight -> FloatView.setAutoDock(AutoDock.RIGHT, "f2")
                R.id.rbAutoDockTop -> FloatView.setAutoDock(AutoDock.TOP, "f2")
                R.id.rbAutoDockBottom -> FloatView.setAutoDock(AutoDock.BOTTOM, "f2")
                R.id.rbAutoDockLeftRight -> FloatView.setAutoDock(AutoDock.LEFT_RIGHT, "f2")
                R.id.rbAutoDockTopBottom -> FloatView.setAutoDock(AutoDock.TOP_BOTTOM, "f2")
                R.id.rbAutoDockFull -> FloatView.setAutoDock(AutoDock.FULL, "f2")
            }
        }
    }

    override fun onDestroy() {
        FloatView.clear()
        super.onDestroy()
    }
}