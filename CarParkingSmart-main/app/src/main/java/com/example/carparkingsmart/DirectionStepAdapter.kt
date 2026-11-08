package com.example.carparkingsmart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DirectionStepAdapter(private val steps: List<DirectionStep>) :
    RecyclerView.Adapter<DirectionStepAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // ✅ TextView, không phải ImageView
        val tvIcon        : TextView = view.findViewById(R.id.iv_step_icon)
        val tvInstruction : TextView = view.findViewById(R.id.tv_step_instruction)
        val tvDistance    : TextView = view.findViewById(R.id.tv_step_distance)
        val tvDuration    : TextView = view.findViewById(R.id.tv_step_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_direction_step, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = steps[position]
        holder.tvInstruction.text = step.instruction
        holder.tvDistance.text    = step.distance
        holder.tvDuration.text    = step.duration

        // ✅ Dùng text, không dùng setImageResource
        holder.tvIcon.text = when {
            step.maneuver.contains("left")       -> "<"
            step.maneuver.contains("right")      -> ">"
            step.maneuver.contains("arrive")     -> "X"
            step.maneuver.contains("depart")     -> "GO"
            step.maneuver.contains("roundabout") -> "O"
            else                                 -> "^"
        }
    }

    override fun getItemCount() = steps.size
}