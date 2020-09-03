package com.ho1ho.leoandroidbaseutil.jetpack_components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ho1ho.leoandroidbaseutil.R

class JetpackFragment : Fragment() {

    companion object {
        val mNicolasCageMovies = listOf(
            Movie("Raising Arizona", 1987),
            Movie("Vampire's Kiss", 1988),
            Movie("Con Air", 1997),
            Movie("Gone in 60 Seconds", 1997),
            Movie("National Treasure", 2004),
            Movie("The Wicker Man", 2006),
            Movie("Ghost Rider", 2007),
            Movie("Knowing", 2009)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_jetpack, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<RecyclerView>(R.id.recyclerView).run {
            layoutManager = LinearLayoutManager(requireActivity())
            adapter = ListAdapter(JetpackFragment.mNicolasCageMovies)
        }
    }
}

class MovieViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
    RecyclerView.ViewHolder(inflater.inflate(R.layout.grid_item, parent, false)) {
    private var mTitleView: TextView? = null

    init {
        mTitleView = itemView.findViewById(R.id.name)
    }

    fun bind(movie: Movie) {
        mTitleView?.text = movie.title
    }
}

class ListAdapter(private val list: List<Movie>) : RecyclerView.Adapter<MovieViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return MovieViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie: Movie = list[position]
        holder.bind(movie)
    }

    override fun getItemCount(): Int = list.size
}

data class Movie(val title: String, val year: Int)