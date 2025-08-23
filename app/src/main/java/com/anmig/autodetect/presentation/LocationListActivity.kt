package com.anmig.autodetect.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ProgressBar
import androidx.core.net.toUri
import com.anmig.autodetect.R
import com.anmig.autodetect.utils.FirebaseHelper

class LocationListActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var adapter: LocationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_list)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.locations)

        recyclerView = findViewById(R.id.rv_locations)
        progressBar = findViewById(R.id.progress_bar)

        firebaseHelper = FirebaseHelper()

        setupRecyclerView()
        loadLocations()
    }

    private fun setupRecyclerView() {
        adapter = LocationAdapter { locationData ->
            openLocationInMaps(locationData.latitude, locationData.longitude)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadLocations() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        firebaseHelper.getAllLocations { locations ->
            progressBar.visibility = View.GONE
            if (locations.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_locations_found), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                recyclerView.visibility = View.VISIBLE
                adapter.submitList(locations)
            }
        }
    }

    private fun openLocationInMaps(latitude: Double, longitude: Double) {
        val uri = "geo:$latitude,$longitude?q=$latitude,$longitude".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to any app that can handle geo URIs
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
            if (fallbackIntent.resolveActivity(packageManager) != null) {
                startActivity(fallbackIntent)
            } else {
                Toast.makeText(this, getString(R.string.no_maps_app_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}