package com.arkul.weather

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.Manifest
import android.content.Intent
import android.provider.Settings

class WeatherDialogs (private val context : Context)
{
    fun showLocationDialog()
    {
        MaterialAlertDialogBuilder(context)
            .setTitle("Location")
            .setMessage("You have not enabled geolocation. You need to enable it")
            .setNeutralButton("Later") { dialog, _ ->
                dialog.dismiss()

            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Yes") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }
}
