package com.antarikshc.stockplay.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.antarikshc.stockplay.R
import kotlinx.android.synthetic.main.layout_stock_item.view.*
import java.util.*

class MainListAdapter : ListAdapter<Stock, MainListAdapter.ViewHolder>(StockDC()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.layout_stock_item, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    fun swapData(data: List<Stock>) = submitList(data.toMutableList())

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: Stock) = with(itemView) {

            text_stock_name.text = item.name

            val currentPrice = "$${item.price}"
            text_current_price.text = currentPrice

            val prevPrice = "$${item.previousPrice}"
            text_prev_price.text = prevPrice

            when {
                item.price > item.previousPrice -> {
                    text_prev_price.setTextColor(resources.getColor(R.color.primaryGreen))
                    image_indicator.visibility = View.VISIBLE
                    image_indicator.setImageResource(R.drawable.ic_arrow_up)
                }
                item.price < item.previousPrice -> {
                    text_prev_price.setTextColor(resources.getColor(R.color.primaryRed))
                    image_indicator.visibility = View.VISIBLE
                    image_indicator.setImageResource(R.drawable.ic_arrow_down)
                }
                else -> {
                    text_prev_price.setTextColor(resources.getColor(R.color.grayDark))
                    image_indicator.visibility = View.GONE
                }
            }

            text_timestamp.text = Date(item.updatedAt).getRelativeTime()

        }

    }


    private class StockDC : DiffUtil.ItemCallback<Stock>() {
        override fun areItemsTheSame(
            oldItem: Stock,
            newItem: Stock
        ): Boolean = oldItem.name == newItem.name

        override fun areContentsTheSame(
            oldItem: Stock,
            newItem: Stock
        ): Boolean = oldItem == newItem
    }
}


fun Date.getRelativeTime(): String {
    val now = Date().time
    val difference = now - time

    val relativeTime = when {
        difference < 2000L -> "Just now"

        difference < DateUtils.MINUTE_IN_MILLIS -> DateUtils.getRelativeTimeSpanString(
            time,
            now,
            DateUtils.SECOND_IN_MILLIS
        )
        difference < DateUtils.HOUR_IN_MILLIS -> DateUtils.getRelativeTimeSpanString(
            time,
            now,
            DateUtils.MINUTE_IN_MILLIS
        )
        difference < DateUtils.DAY_IN_MILLIS -> DateUtils.getRelativeTimeSpanString(
            time,
            now,
            DateUtils.HOUR_IN_MILLIS
        )
        difference < DateUtils.WEEK_IN_MILLIS -> DateUtils.getRelativeTimeSpanString(
            time,
            now,
            DateUtils.DAY_IN_MILLIS
        )
        else -> DateUtils.getRelativeTimeSpanString(time, now, DateUtils.WEEK_IN_MILLIS)
    }

    return relativeTime.toString()
}