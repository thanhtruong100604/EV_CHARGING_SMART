package com.example.carparkingsmart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ParkingListAdapter(
    private val parkingList: List<MainActivity.ParkingLot>,
    private val onItemClick: (MainActivity.ParkingLot) -> Unit,
    private val getDistanceText: (MainActivity.ParkingLot) -> String  // Truyền hàm tính khoảng cách từ MainActivity
) : RecyclerView.Adapter<ParkingListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val parking = parkingList[position]

        val status = when {
            parking.availableSpots == 0 -> "🔴 Hết chỗ"
            parking.availableSpots < 10 -> "🟡 Còn ít"
            else -> "🟢 Còn nhiều"
        }

        val distanceText = getDistanceText(parking)  // Gọi hàm được truyền vào

        holder.tvName.text = "${parking.name}\n$status - ${parking.availableSpots}/${parking.totalSpots} chỗ$distanceText"
        holder.itemView.setOnClickListener { onItemClick(parking) }
    }

    override fun getItemCount() = parkingList.size
}