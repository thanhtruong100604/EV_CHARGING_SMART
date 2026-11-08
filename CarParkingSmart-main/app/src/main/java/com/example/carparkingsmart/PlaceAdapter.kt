package com.example.carparkingsmart

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView

class PlaceAdapter(
    context: Context,
    private val allItems: MutableList<MainActivity.PlaceInfo>
) : ArrayAdapter<MainActivity.PlaceInfo>(context, android.R.layout.simple_dropdown_item_1line, allItems) {

    private var filteredItems: MutableList<MainActivity.PlaceInfo> = allItems.toMutableList()

    override fun getCount(): Int = filteredItems.size

    override fun getItem(position: Int): MainActivity.PlaceInfo? =
        if (position < filteredItems.size) filteredItems[position] else null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)

        val item = filteredItems.getOrNull(position)
        (view as? TextView)?.text = item?.name ?: ""
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val query = constraint?.toString()?.trim()?.lowercase() ?: ""

                filteredItems = if (query.isEmpty()) {
                    allItems.toMutableList()
                } else {
                    allItems.filter { place ->
                        place.name.lowercase().contains(query) ||
                                place.address.lowercase().contains(query)
                    }.toMutableList()
                }

                results.values = filteredItems
                results.count = filteredItems.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems = (results?.values as? MutableList<MainActivity.PlaceInfo>)
                    ?: mutableListOf()
                notifyDataSetChanged()
            }
        }
    }

    /** Thêm 1 item, tránh trùng theo tên */
    fun addItemIfNotExists(item: MainActivity.PlaceInfo) {
        if (allItems.none { it.name == item.name }) {
            allItems.add(item)
        }
    }

    /** Thay toàn bộ danh sách */
    fun replaceAll(newItems: List<MainActivity.PlaceInfo>) {
        allItems.clear()
        allItems.addAll(newItems)
        filteredItems = allItems.toMutableList()
        notifyDataSetChanged()
    }
}