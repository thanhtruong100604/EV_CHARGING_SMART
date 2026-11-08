package com.example.carparkingsmart

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChargingSlot(
    val id: Int,
    val slot_code: String,
    val is_available: Boolean // Trạng thái vật lý hiện tại (có xe đang đỗ hay không)
)

class SlotAdapter(
    private val slots: List<ChargingSlot>,
    private val onSlotSelected: (ChargingSlot) -> Unit
) : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    private var selectedPosition = -1

    inner class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSlotCode: TextView = itemView.findViewById(R.id.tv_slot_code)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_charging_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slots[position]
        holder.tvSlotCode.text = slot.slot_code

        // LOGIC ĐỔI MÀU TẬP TRUNG VÀO TRẠNG THÁI CHỌN
        when {
            // 1. Ô ĐANG ĐƯỢC NGƯỜI DÙNG CLICK CHỌN (Màu xanh đậm)
            position == selectedPosition -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#4CAF50")) // Green 500
                holder.tvSlotCode.setTextColor(Color.WHITE)
                holder.itemView.alpha = 1f
            }

            // 2. Ô CHƯA ĐƯỢC CHỌN (Màu xanh nhạt)
            // Lưu ý: Chúng ta để mặc định là xanh nhạt kể cả is_available là false
            // để người dùng vẫn ấn vào xem được các khung giờ khác còn trống.
            else -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#E8F5E9")) // Green 50
                holder.tvSlotCode.setTextColor(Color.parseColor("#1B5E20")) // Green 900
                holder.itemView.alpha = 1f
            }
        }

        // Đảm bảo tất cả các ô đều có thể tương tác để xem lịch trình giờ
        holder.itemView.isClickable = true
        holder.itemView.isEnabled = true

        holder.itemView.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition

            // Cập nhật lại UI cho ô cũ và ô mới
            if (oldPosition != -1) notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)

            onSlotSelected(slot)
        }
    }

    override fun getItemCount() = slots.size
}