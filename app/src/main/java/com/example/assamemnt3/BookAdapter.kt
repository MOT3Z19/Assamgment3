package com.example.assamemnt3

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.assamemnt3.databinding.BookItemBinding

class BookAdapter (private val context: Context, private val click: OnClick) : RecyclerView.Adapter<BooksAdapter.ViewHolder>() {
    private val TAG = "BooksAdapter"
    private var list = ArrayList<Book>()

    // For View Binding
    class ViewHolder(val binding: BookItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(BookItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = list[position]
        holder.binding.name.text = book.name
        holder.binding.author.text = book.authorName
        holder.binding.date.text = book.realizeDate
        holder.binding.price.text = "$ ${book.price}"
        holder.binding.rate.rating = book.rate!!.toFloat()

        Glide.with(context)
            .load(book.imageUrl)
            .into(holder.binding.ivBook)

        holder.binding.root.setOnClickListener {
            click.onClickBook(position, book)
        }
        holder.binding.btnEdit.setOnClickListener {
            click.onClickEditBook(position, book)
        }
    }

    fun setData(data: ArrayList<Book>) {
        list = data
        notifyDataSetChanged()
    }

    interface OnClick {
        fun onClickBook(itemId: Int, book: Book)
        fun onClickEditBook(itemId: Int, book: Book)
    }
}