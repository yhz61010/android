package com.leovp.demo.basic_components.examples

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.leovp.androidbase.exts.android.toast
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityFloatViewBinding
import com.leovp.drawonscreen.FingerPaintView
import com.leovp.floatview_sdk.FloatView
import com.leovp.floatview_sdk.entities.DockEdge
import com.leovp.floatview_sdk.entities.StickyEdge
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import java.util.*

class FloatViewActivity : BaseDemonstrationActivity<ActivityFloatViewBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityFloatViewBinding {
        return ActivityFloatViewBinding.inflate(layoutInflater)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.btnChange.setOnSingleClickListener {
            FloatView.with("f1").customView?.findViewById<TextView>(R.id.tvText)?.text =
                    "I'm f1 and change my text to: ${Random().nextInt(100)}"
            FloatView.with("f2").customView?.findViewById<TextView>(R.id.tvText)?.text =
                    "I'm f2 and change my text to: ${Random().nextInt(100)}"
        }

        FloatView.with(this)
            .meta {
                tag = "f1"
                immersiveMode = false
                x = 50
                y = 220
            }
            .layout(R.layout.floatview) { v ->
                v.findViewById<TextView>(R.id.tvText).text =
                        "f1\nImmersive:OFF\nStick:${FloatView.with("f1").stickyEdge}"
                v.findViewById<View>(R.id.floatViewBtn)
                    .setOnSingleClickListener { toast("Win1 Button") }
                v.findViewById<View>(R.id.linearLayout)
                    .setOnSingleClickListener { toast("Win1 Image") }
            }
            .listener(object : FloatView.TouchEventListener {
                override fun touchDown(view: View, x: Int, y: Int): Boolean {
                    LogContext.log.w("F1", "touchDown ($x, $y)")
                    return false
                }

                override fun touchMove(view: View, x: Int, y: Int,
                    isClickGesture: Boolean): Boolean {
                    LogContext.log.w("F1", "touchMove ($x, $y) isClickGesture=$isClickGesture")
                    return false
                }

                override fun touchUp(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean {
                    LogContext.log.w("F1", "touchUp ($x, $y) isClickGesture=$isClickGesture")
                    return false
                }
            })
            .show()

        FloatView.with(this)
            .meta {
                tag = "f2"
                immersiveMode = true
                edgeMargin = 20
                x = 100
                y = 600
            }
            .layout(R.layout.floatview) { v ->
                v.findViewById<TextView>(R.id.tvText).text =
                        "f2\nImmersive:ON\nEdge 20px\nDock:${FloatView.with("f1").dockEdge}"
                v.findViewById<View>(R.id.floatViewBtn)
                    .setOnSingleClickListener { toast("Win2 Button") }
                v.findViewById<View>(R.id.linearLayout)
                    .setOnSingleClickListener { toast("Win2 Image") }
            }
            .show()

        FloatView.with(this)
            .meta {
                tag = "f3"
                immersiveMode = true
                y = 900
                stickyEdge = StickyEdge.RIGHT
            }
            .layout(R.layout.floatview) { v ->
                v.findViewById<TextView>(R.id.tvText).text = "f3\nImmersive:ON\nStick:RIGHT"
                v.findViewById<View>(R.id.floatViewBtn)
                    .setOnSingleClickListener { toast("Win3 Button") }
                v.findViewById<View>(R.id.linearLayout)
                    .setOnSingleClickListener { toast("Win3 Image") }
            }
            .show()

        FloatView.with(this)
            .meta {
                tag = "f4"
                immersiveMode = true
                y = 900
                dockEdge = DockEdge.LEFT
            }
            .layout(R.layout.floatview) { v ->
                v.findViewById<TextView>(R.id.tvText).text = "f4\nImmersive:ON\nDock:LEFT"
                v.findViewById<View>(R.id.floatViewBtn)
                    .setOnSingleClickListener { toast("Win4 Button") }
                v.findViewById<View>(R.id.linearLayout)
                    .setOnSingleClickListener { toast("Win4 Image") }
            }
            .show()

        FloatView.with(this)
            .meta {
                tag = "floatView_touchable"
                immersiveMode = true
                touchable = false
                enableDrag = false
            }
            .layout(R.layout.float_view_fingerpaint_fullscreen) { v ->
                val finger = v.findViewById(R.id.finger) as FingerPaintView
                finger.strokeColor = Color.RED
                finger.inEditMode = false
                finger.strokeWidth = 8f
            }
            .listener(object : FloatView.TouchEventListener {
                override fun touchDown(view: View, x: Int, y: Int): Boolean {
                    LogContext.log.w("floatView_touchable", "touchDown ($x, $y)")
                    return false
                }

                override fun touchMove(view: View, x: Int, y: Int,
                    isClickGesture: Boolean): Boolean {
                    LogContext.log.w("floatView_touchable",
                        "touchMove ($x, $y) isClickGesture=$isClickGesture")
                    return false
                }

                override fun touchUp(view: View, x: Int, y: Int, isClickGesture: Boolean): Boolean {
                    LogContext.log.w("floatView_touchable",
                        "touchUp ($x, $y) isClickGesture=$isClickGesture")
                    return false
                }
            })
            .show()

        binding.rgSticky.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbStickyNone   -> FloatView.with("f1").stickyEdge = StickyEdge.NONE
                R.id.rbStickyLeft   -> FloatView.with("f1").stickyEdge = StickyEdge.LEFT
                R.id.rbStickyRight  -> FloatView.with("f1").stickyEdge = StickyEdge.RIGHT
                R.id.rbStickyTop    -> FloatView.with("f1").stickyEdge = StickyEdge.TOP
                R.id.rbStickyBottom -> FloatView.with("f1").stickyEdge = StickyEdge.BOTTOM
            }
        }

        binding.rgAutoDock.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbAutoDockNone      -> FloatView.with("f2").dockEdge = DockEdge.NONE
                R.id.rbAutoDockLeft      -> FloatView.with("f2").dockEdge = DockEdge.LEFT
                R.id.rbAutoDockRight     -> {
                    FloatView.with("f2").dockEdge = DockEdge.RIGHT
                    FloatView.with("f2").customView?.let { v ->
                        v.findViewById<TextView>(R.id.tvText).text =
                                "f2\nImmersive:OFF\nDock:${FloatView.with("f2").dockEdge}"
                    }
                }
                R.id.rbAutoDockTop       -> FloatView.with("f2").dockEdge = DockEdge.TOP
                R.id.rbAutoDockBottom    -> FloatView.with("f2").dockEdge = DockEdge.BOTTOM
                R.id.rbAutoDockLeftRight -> FloatView.with("f2").dockEdge = DockEdge.LEFT_RIGHT
                R.id.rbAutoDockTopBottom -> FloatView.with("f2").dockEdge = DockEdge.TOP_BOTTOM
                R.id.rbAutoDockFull      -> FloatView.with("f2").dockEdge = DockEdge.FULL
            }
        }

        binding.rgEnableDrag.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbEnableDrag  -> FloatView.with("f1").enableDrag = true
                R.id.rbDisableDrag -> FloatView.with("f1").enableDrag = false
            }
        }

        binding.rgTouchable.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTouchable     -> {
                    FloatView.with("floatView_touchable").touchable = true
                }
                R.id.rbNoneTouchable -> {
                    FloatView.with("floatView_touchable").touchable = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FloatView.visibleAll()
    }

    override fun onStop() {
        FloatView.invisibleAll()
        super.onStop()
    }

    override fun onDestroy() {
        FloatView.clearAll()
        super.onDestroy()
    }

    fun onChangePosClick(@Suppress("UNUSED_PARAMETER") view: View) {
        FloatView.with("f2").x = 10240
        FloatView.with("f2").y = 10240

        FloatView.with("f1") {
            x = 10
            y = 10
        }
    }
}