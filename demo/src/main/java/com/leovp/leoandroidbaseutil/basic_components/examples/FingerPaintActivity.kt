package com.leovp.leoandroidbaseutil.basic_components.examples

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityFingerPaintBinding
import com.leovp.log_sdk.base.ITAG

class FingerPaintActivity : BaseDemonstrationActivity(), SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    override fun getTagName(): String = ITAG

    private lateinit var binding: ActivityFingerPaintBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFingerPaintBinding.inflate(layoutInflater).apply { setContentView(root) }

        binding.close.setOnClickListener(this)
        binding.save.setOnClickListener(this)
        binding.undo.setOnClickListener(this)
        binding.clear.setOnClickListener(this)
        binding.red.setOnSeekBarChangeListener(this)
        binding.green.setOnSeekBarChangeListener(this)
        binding.blue.setOnSeekBarChangeListener(this)
        binding.tolerance.setOnSeekBarChangeListener(this)
        binding.width.setOnSeekBarChangeListener(this)
        binding.normal.setOnClickListener(this)
//        binding.emboss.setOnClickListener(this)
        binding.blur.setOnClickListener(this)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (seekBar.id == binding.red.id || seekBar.id == binding.green.id || seekBar.id == binding.blue.id) {
            val r = binding.red.progress
            val g = binding.green.progress
            val b = binding.blue.progress
            val color = Color.argb(255, r, g, b)
            binding.finger.strokeColor = color
            binding.colorPreview.setBackgroundColor(color)
        } else if (seekBar.id == binding.tolerance.id) {
            binding.finger.touchTolerance = progress.toFloat()
        } else if (seekBar.id == binding.width.id) {
            binding.finger.strokeWidth = progress.toFloat()
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.undo -> binding.finger.undo()
            binding.clear -> binding.finger.clear()
            binding.close -> hidePreview()
            binding.save -> showPreview()
//            binding.emboss -> binding.finger.emboss()
            binding.blur -> binding.finger.blur()
            binding.normal -> binding.finger.normal()
        }
    }

    private fun showPreview() {
        binding.previewContainer.visibility = View.VISIBLE
        binding.preview.setImageDrawable(binding.finger.drawable)
    }

    private fun hidePreview() {
        binding.previewContainer.visibility = View.INVISIBLE
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
    }

    override fun onBackPressed() {
        if (binding.previewContainer.visibility == View.VISIBLE) {
            hidePreview()
        } else {
            super.onBackPressed()
        }
    }
}