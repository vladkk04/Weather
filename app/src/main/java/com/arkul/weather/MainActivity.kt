package com.arkul.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arkul.weather.databinding.ActivityMainBinding
import com.arkul.weather.models.WeatherResponse
import com.arkul.weather.network.IWeatherService
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity()
{
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mSharedPreferences : SharedPreferences

    private lateinit var binding : ActivityMainBinding

    private var isLocationFinePermissionGranted = false
    private var isLocationCoarsePermissionGranted = false

    private var mProgressDialog : Dialog ?= null

    private val registerRequestLocationPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
    { permissions ->
        isLocationFinePermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: isLocationFinePermissionGranted
        isLocationCoarsePermissionGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: isLocationCoarsePermissionGranted

        if(!isLocationFinePermissionGranted)
        {
            requestLocationPermissions()
        }

        if (isLocationFinePermissionGranted && isLocationCoarsePermissionGranted)
        {
            if (!isLocationEnable())
            {
                enableLocation()
            }

            requestLocationData()
        }
    }

    override fun onCreate(savedInstanceState : Bundle?)
    {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUi()

        requestLocationPermissions()
   }

    private fun getLocationWeatherDetails(latitude : Double?, longitude : Double?)
    {
        if(Constants.isNetworkAvailable(this))
        {
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : IWeatherService = retrofit.create(IWeatherService::class.java)

            val listCall : Call<WeatherResponse> = service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>
            {
                override fun onResponse(call : Call<WeatherResponse>, response : Response<WeatherResponse>)
                {
                    if(response.isSuccessful)
                    {
                        hideProgressDialog()
                        val weatherList : WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUi()

                        Log.d("MainActivityy", "$weatherList")
                    }
                    else
                    {
                        when(response.code())
                        {
                            400 -> Log.e("Error 400", "Bad Connection")
                        }
                    }
                }

                override fun onFailure(call : Call<WeatherResponse>,t : Throwable)
                {
                    Log.d("MainActivityy", "ERror")
                    hideProgressDialog()
                }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData()
    {
        val locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(locationRequest, getLocationCallback, Looper.myLooper())
    }

    private var getLocationCallback = object : LocationCallback()
    {
        override fun onLocationResult(locationResult : LocationResult)
        {
            val mLastLocation = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            val longitude = mLastLocation?.longitude

            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun requestLocationPermissions()
    {
        isLocationFinePermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        isLocationCoarsePermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        val permissionRequestList = arrayListOf<String>()

        if (!isLocationFinePermissionGranted)
        {
            permissionRequestList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (!isLocationCoarsePermissionGranted)
        {
            permissionRequestList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionRequestList.isNotEmpty())
        {
            registerRequestLocationPermissions.launch(permissionRequestList.toTypedArray())
        }
    }

    private fun isLocationEnable() : Boolean
    {
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun enableLocation() : LocationRequest
    {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setWaitForAccurateLocation(false)
            .build()

        val locationSettingsBuilder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(this).checkLocationSettings(locationSettingsBuilder)
            .addOnFailureListener { exception ->
            if (exception is ResolvableApiException)
                {
                    try
                    {
                        exception.startResolutionForResult(this, 0x1)
                    }
                    catch (_ : IntentSender.SendIntentException)
                    {

                    }
                }
            }

        return locationRequest
    }

    private fun showCustomProgressDialog()
    {
        mProgressDialog = Dialog(this).also { dialog ->
            dialog.setContentView(R.layout.dialog_custom_progress)
            dialog.show()
        }
    }

    private fun hideProgressDialog()
    {
        if(mProgressDialog != null)
        {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUi()
    {
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty())
        {
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for(i in weatherList.weather.indices)
            {
                binding.tvMain.text = weatherList.weather[i].main
                binding.tvMainDescription.text = weatherList.weather[i].description
                binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

                binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
                binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
                binding.tvSpeed.text = weatherList.wind.speed.toString()
                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country

                when(weatherList.weather[i].icon)
                {
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                }
            }
        }

    }

    private fun getUnit(value : String) : String
    {
        var value = "C"

        if("US" == value || "LR" == value || "MM" == value)
        {
            value = "F"
        }
        return value
    }

    private fun unixTime(timex : Long) : String?
    {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu : Menu?) : Boolean
    {
        menuInflater.inflate(R.menu.menu_main, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item : MenuItem) : Boolean
    {
        return when(item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}



