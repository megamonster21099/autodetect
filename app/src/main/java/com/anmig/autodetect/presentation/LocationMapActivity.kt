package com.anmig.autodetect.presentation

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar
import com.anmig.autodetect.R
import com.anmig.autodetect.models.LocationData
import com.anmig.autodetect.utils.FirebaseHelper
import com.anmig.autodetect.utils.Logger
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationMapActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private const val TAG = "LocationMapActivity"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var firebaseHelper: FirebaseHelper
    private var googleMap: GoogleMap? = null
    private var locations: List<LocationData> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_maps)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.locations)

        progressBar = findViewById(R.id.progress_bar)
        firebaseHelper = FirebaseHelper()

        // Initialize map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        loadLocations()
    }

    override fun onMapReady(map: GoogleMap) {
        Logger.log("$TAG: onMapReady() called")
        googleMap = map
        
        // Configure map settings
        googleMap?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMapToolbarEnabled = true
        }

        // If locations are already loaded, display them
        if (locations.isNotEmpty()) {
            displayLocationsOnMap()
        }
    }

    private fun loadLocations() {
        Logger.log("$TAG: loadLocations() called")
        progressBar.visibility = View.VISIBLE

        firebaseHelper.getAllLocations { loadedLocations ->
            Logger.log("$TAG: Loaded ${loadedLocations.size} locations from Firebase")
            progressBar.visibility = View.GONE
            
            if (loadedLocations.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_locations_found), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // Sort locations by timestamp to create proper route order
                locations = loadedLocations.sortedBy { it.timestamp }
                Logger.log("$TAG: Locations sorted by timestamp")
                
                // Display on map if ready
                if (googleMap != null) {
                    displayLocationsOnMap()
                }
            }
        }
    }

    private fun displayLocationsOnMap() {
        val map = googleMap ?: return
        
        Logger.log("$TAG: displayLocationsOnMap() called with ${locations.size} locations")
        
        if (locations.isEmpty()) return

        // Create polyline points for the route
        val routePoints = locations.map { LatLng(it.latitude, it.longitude) }
        
        // Add polyline to show the route
        val polylineOptions = PolylineOptions()
            .addAll(routePoints)
            .color(Color.BLUE)
            .width(8f)
            .geodesic(true)
        
        map.addPolyline(polylineOptions)
        Logger.log("$TAG: Added polyline with ${routePoints.size} points")

        // Add markers for start and end points
        if (locations.isNotEmpty()) {
            val startLocation = locations.first()
            val endLocation = locations.last()
            
            // Start marker (Green)
            val startLatLng = LatLng(startLocation.latitude, startLocation.longitude)
            val startTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                .format(Date(startLocation.timestamp))
            map.addMarker(
                MarkerOptions()
                    .position(startLatLng)
                    .title("Start")
                    .snippet("Time: $startTime")
            )
            
            // End marker (Red) - only if different from start
            if (locations.size > 1) {
                val endLatLng = LatLng(endLocation.latitude, endLocation.longitude)
                val endTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    .format(Date(endLocation.timestamp))
                map.addMarker(
                    MarkerOptions()
                        .position(endLatLng)
                        .title("End")
                        .snippet("Time: $endTime")
                )
            }
        }

        // Adjust camera to show all points
        if (routePoints.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            routePoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            
            val padding = 100 // padding in pixels
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
            Logger.log("$TAG: Camera adjusted to show all locations")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}