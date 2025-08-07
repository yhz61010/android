package com.leovp.demo.jetpackcomponents.examples.room

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.leovp.androidbase.utils.ui.BetterActivityResult
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityRoomBinding
import com.leovp.demo.jetpackcomponents.examples.room.entity.Word
import com.leovp.log.base.ITAG

class RoomActivity : BaseDemonstrationActivity<ActivityRoomBinding>(R.layout.activity_room) {

    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityRoomBinding =
        ActivityRoomBinding.inflate(layoutInflater)

    private lateinit var wordViewModel: WordViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recyclerView = binding.recyclerview
        val adapter = WordListAdapter(this)
        adapter.onItemClickListener = object : WordListAdapter.OnItemClickListener {
            @SuppressLint("CheckResult")
            override fun onItemLongClick(view: View, position: Int) {
                MaterialDialog(this@RoomActivity).show {
                    input(
                        hint = "Input your word",
                        prefill = view.findViewById<TextView>(R.id.textView).text.toString()
                    ) { _, text ->
                        val updatedWorld = Word(text.toString())
                        updatedWorld.id = view.getTag(R.id.word_id) as Int
                        wordViewModel.update(updatedWorld)
                    }
                    positiveButton(android.R.string.ok)
                    negativeButton(android.R.string.cancel)
                }
            }
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        val itemTouchHelperCallback =
            object :
                ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    wordViewModel.delete(adapter.removeAt(viewHolder.bindingAdapterPosition))
                }
            }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        wordViewModel = ViewModelProvider(this).get(WordViewModel::class.java)
        wordViewModel.allWords.observe(this) { words ->
            // Update the cached copy of the words in the adapter.
            words?.let { adapter.setWords(it) }
        }

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            newWordActivityLauncher.launch(
                Intent(this, NewWordActivity::class.java).apply {
                    putExtra("title", "New Word")
                }
            )
        }
    }

    private val newWordActivityLauncher = BetterActivityResult.registerForActivityResult(
        this,
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(NewWordActivity.EXTRA_REPLY)?.let {
                val word = Word(it)
                wordViewModel.insert(word)
            }
        } else {
            Toast.makeText(
                applicationContext,
                R.string.empty_not_saved,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
