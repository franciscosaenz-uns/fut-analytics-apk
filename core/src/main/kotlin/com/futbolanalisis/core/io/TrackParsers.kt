package com.futbolanalisis.core.io

import com.futbolanalisis.core.model.HeartRateSample
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.model.TrackPoint
import com.futbolanalisis.core.model.TrackSource
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory

class TrackParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Detecta el formato por el contenido y delega en el parser correcto. */
object TrackImporter {
    fun parse(input: InputStream, fileName: String? = null): Track {
        val bytes = input.readBytes()
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8)
        return when {
            head.contains("<gpx", ignoreCase = true) -> GpxParser.parse(bytes.inputStream())
            head.contains("TrainingCenterDatabase") -> TcxParser.parse(bytes.inputStream())
            fileName?.endsWith(".fit", ignoreCase = true) == true ->
                throw TrackParseException("Los archivos FIT aún no están soportados (v0.2). Exporta en GPX o TCX.")
            else -> throw TrackParseException("Formato no reconocido. Usa un archivo GPX o TCX.")
        }
    }
}

internal object Xml {
    fun parse(input: InputStream): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Protección contra XXE; algunos parsers de Android no soportan todas las features.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        try {
            return factory.newDocumentBuilder().parse(input).documentElement
        } catch (e: Exception) {
            throw TrackParseException("El archivo no es un XML válido: ${e.message}", e)
        }
    }

    fun Element.children(localName: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && n.localNameOrName() == localName) out += n as Element
        }
        return out
    }

    fun Element.child(localName: String): Element? = children(localName).firstOrNull()

    fun Element.descendants(localName: String): List<Element> {
        val list = getElementsByTagNameNS("*", localName)
        val out = ArrayList<Element>(list.length)
        for (i in 0 until list.length) out += list.item(i) as Element
        if (out.isEmpty()) {
            // Documentos sin namespace.
            val plain = getElementsByTagName(localName)
            for (i in 0 until plain.length) out += plain.item(i) as Element
        }
        return out
    }

    fun Node.localNameOrName(): String = localName ?: nodeName.substringAfter(':')

    fun parseTime(text: String?): Long? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        return runCatching { Instant.parse(t).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
    }
}

object GpxParser {
    private val hrNames = setOf("hr", "heartrate", "heart_rate")

    fun parse(input: InputStream): Track {
        val root = Xml.parse(input)
        with(Xml) {
            val trk = root.descendants("trk").firstOrNull()
            val name = trk?.child("name")?.textContent?.trim()
            val points = root.descendants("trkpt").mapNotNull { pt ->
                val lat = pt.getAttribute("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lon = pt.getAttribute("lon").toDoubleOrNull() ?: return@mapNotNull null
                val time = parseTime(pt.child("time")?.textContent) ?: return@mapNotNull null
                val ele = pt.child("ele")?.textContent?.trim()?.toDoubleOrNull()
                val hr = pt.child("extensions")?.let { ext -> findHr(ext) }
                TrackPoint(time, lat, lon, ele, hr)
            }.sortedBy { it.timeMs }
            if (points.size < 2) throw TrackParseException("El GPX no contiene puntos con hora y posición.")
            return Track(name = name, source = TrackSource.GPX, points = points)
        }
    }

    private fun findHr(ext: Element): Int? {
        val all = ext.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val e = all.item(i) as Element
            val n = with(Xml) { e.localNameOrName() }.lowercase()
            if (n in hrNames) return e.textContent.trim().toDoubleOrNull()?.toInt()?.takeIf { it in 25..250 }
        }
        return null
    }
}

object TcxParser {
    fun parse(input: InputStream): Track {
        val root = Xml.parse(input)
        with(Xml) {
            val name = root.descendants("Activity").firstOrNull()?.getAttribute("Sport")
            val points = ArrayList<TrackPoint>()
            val hrOnly = ArrayList<HeartRateSample>()
            for (tp in root.descendants("Trackpoint")) {
                val time = parseTime(tp.child("Time")?.textContent) ?: continue
                val hr = tp.child("HeartRateBpm")?.child("Value")?.textContent?.trim()?.toDoubleOrNull()
                    ?.toInt()?.takeIf { it in 25..250 }
                val pos = tp.child("Position")
                val lat = pos?.child("LatitudeDegrees")?.textContent?.trim()?.toDoubleOrNull()
                val lon = pos?.child("LongitudeDegrees")?.textContent?.trim()?.toDoubleOrNull()
                if (lat != null && lon != null) {
                    val ele = tp.child("AltitudeMeters")?.textContent?.trim()?.toDoubleOrNull()
                    points += TrackPoint(time, lat, lon, ele, hr)
                } else if (hr != null) {
                    hrOnly += HeartRateSample(time, hr)
                }
            }
            if (points.size < 2) throw TrackParseException("El TCX no contiene puntos GPS.")
            return Track(
                name = name?.takeIf { it.isNotBlank() },
                source = TrackSource.TCX,
                points = points.sortedBy { it.timeMs },
                extraHeartRate = hrOnly,
            )
        }
    }
}

/** Escribe un GPX 1.1 con FC (extensión Garmin TrackPointExtension), útil para exportar y para datos de ejemplo. */
object GpxWriter {
    fun write(track: Track): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="FutbolAnalisis" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">"""
        )
        appendLine("  <trk><name>${escape(track.name ?: "Partido")}</name><trkseg>")
        for (p in track.points) {
            append("""    <trkpt lat="${"%.7f".format(java.util.Locale.US, p.lat)}" lon="${"%.7f".format(java.util.Locale.US, p.lon)}">""")
            p.elevation?.let { append("<ele>${"%.1f".format(java.util.Locale.US, it)}</ele>") }
            append("<time>${Instant.ofEpochMilli(p.timeMs)}</time>")
            p.heartRate?.let {
                append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>$it</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
            }
            appendLine("</trkpt>")
        }
        appendLine("  </trkseg></trk>")
        appendLine("</gpx>")
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
