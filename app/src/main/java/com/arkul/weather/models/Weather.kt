package com.arkul.weather.models

import java.io.Serializable

data class Weather(
    val id : Int,
    val main : String,
    val description : String,
    val icon : String
) : Serializable
