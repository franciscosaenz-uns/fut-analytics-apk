package com.futbolanalisis.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val es = Locale("es")
private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm", es)

fun Double.fmt(decimals: Int = 1): String = String.format(es, "%.${decimals}f", this)
fun Double.km(): String = (this / 1000).fmt(2) + " km"
fun Double.minutes(): String = (this / 60).fmt(0) + " min"
fun Long.dateTime(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFmt)
