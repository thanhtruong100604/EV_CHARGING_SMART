package com.example.carparkingsmart

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class TimeSlot(
    val hour: Int,           // 0..23
    val label: String,       // "06:00 - 08:00"
    val isBooked: Boolean    // true = đã có người đặt khung giờ này
)

class TimeSlotAdapter(
    private val timeSlots: List<TimeSlot>,
    private val onTimeSelected: (TimeSlot) -> Unit
) : RecyclerView.Adapter<TimeSlotAdapter.TimeViewHolder>() {

    private var selectedPosition = -1

    inner class TimeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLabel: TextView = itemView.findViewById(R.id.tv_time_label)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_time_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_time_slot, parent, false)
        return TimeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        val slot = timeSlots[position]
        holder.tvLabel.text = slot.label

        // 1. THIẾT LẬP TRẠNG THÁI UI DỰA TRÊN LOGIC ĐẶT CHỖ
        when {
            // Trường hợp: KHUNG GIỜ ĐÃ CÓ NGƯỜI ĐẶT
            slot.isBooked -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#EEEEEE")) // Xám nhạt
                holder.tvLabel.setTextColor(Color.parseColor("#BDBDBD"))
                holder.tvStatus.text = "Đã đặt"
                holder.tvStatus.setTextColor(Color.parseColor("#BDBDBD"))
                holder.itemView.alpha = 0.5f

                // Vô hiệu hóa tương tác
                holder.itemView.isClickable = false
                holder.itemView.isEnabled = false
            }

            // Trường hợp: KHUNG GIỜ ĐANG ĐƯỢC CHỌN
            position == selectedPosition -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#4CAF50")) // Xanh đậm (giống SlotAdapter)
                holder.tvLabel.setTextColor(Color.WHITE)
                holder.tvStatus.text = "Đang chọn"
                holder.tvStatus.setTextColor(Color.WHITE)
                holder.itemView.alpha = 1f

                holder.itemView.isClickable = true
                holder.itemView.isEnabled = true
            }

            // Trường hợp: KHUNG GIỜ CÒN TRỐNG
            else -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#E8F5E9")) // Xanh nhạt (giống SlotAdapter)
                holder.tvLabel.setTextColor(Color.parseColor("#1B5E20"))
                holder.tvStatus.text = "Còn trống"
                holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                holder.itemView.alpha = 1f

                holder.itemView.isClickable = true
                holder.itemView.isEnabled = true
            }
        }

        // 2. XỬ LÝ SỰ KIỆN CLICK
        holder.itemView.setOnClickListener {
            // Bảo vệ: Nếu khung giờ đã đặt hoặc vị trí không hợp lệ thì không làm gì
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION || timeSlots[currentPos].isBooked) return@setOnClickListener

            val oldPosition = selectedPosition
            selectedPosition = currentPos

            // Chỉ cập nhật lại các item thay đổi để tối ưu hiệu năng
            if (oldPosition != -1) notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)

            onTimeSelected(timeSlots[currentPos])
        }
    }

    override fun getItemCount() = timeSlots.size
}