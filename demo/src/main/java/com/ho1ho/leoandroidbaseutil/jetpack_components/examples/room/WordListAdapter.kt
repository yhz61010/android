package com.ho1ho.leoandroidbaseutil.jetpack_components.examples.room

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.jetpack_components.examples.room.entity.Word

/**
 * Author: Michael Leo
 * Date: 2020/9/4 下午1:47
 */
class WordListAdapter internal constructor(context: Context) : RecyclerView.Adapter<WordListAdapter.WordViewHolder>() {
    var onItemClickListener: OnItemClickListener? = null

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var words = mutableListOf<Word>() // Cached copy of words

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val wordItemView: TextView = itemView.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val itemView = inflater.inflate(R.layout.recyclerview_item, parent, false)
        return WordViewHolder(itemView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val current = words[position]
        holder.wordItemView.text = current.word
        holder.itemView.setTag(R.id.word_id, current.id)

        holder.itemView.setOnClickListener {
            onItemClickListener?.onItemClick(holder.itemView, holder.layoutPosition)
        }

        holder.itemView.setOnLongClickListener {
            onItemClickListener?.onItemLongClick(holder.itemView, holder.layoutPosition)
            true
        }
    }

    internal fun setWords(words: List<Word>) {
        this.words = words.toMutableList()
        notifyDataSetChanged()
    }

    override fun getItemCount() = words.size

    fun removeAt(pos: Int): Word {
        val deletedWorld = words.removeAt(pos)
        notifyDataSetChanged()
        return deletedWorld
    }

    // =============================================
    interface OnItemClickListener {
        fun onItemClick(view: View, position: Int) {}
        fun onItemLongClick(view: View, position: Int) {}
    }
    // =============================================
}