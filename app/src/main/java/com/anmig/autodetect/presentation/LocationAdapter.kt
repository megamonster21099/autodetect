package com.anmig.autodetect.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anmig.autodetect.R
import com.anmig.autodetect.models.LocationData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationAdapter(
    private val onItemClick: (LocationData) -> Unit
) : ListAdapter<LocationData, LocationAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)

        fun bind(locationData: LocationData, onItemClick: (LocationData) -> Unit) {
            val locationText = "Lat: ${"%.6f".format(locationData.latitude)}, " +
                    "Lon: ${"%.6f".format(locationData.longitude)}"
            tvLocation.text = locationText

            val date = Date(locationData.timestamp)
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            tvDate.text = dateFormat.format(date)

            itemView.setOnClickListener {
                onItemClick(locationData)
            }
        }
    }
}

class LocationDiffCallback : DiffUtil.ItemCallback<LocationData>() {
    override fun areItemsTheSame(oldItem: LocationData, newItem: LocationData): Boolean {
        return oldItem.timestamp == newItem.timestamp
    }

    override fun areContentsTheSame(oldItem: LocationData, newItem: LocationData): Boolean {
        return oldItem == newItem
    }
}