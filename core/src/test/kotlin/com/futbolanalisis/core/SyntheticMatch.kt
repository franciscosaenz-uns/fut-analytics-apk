package com.futbolanalisis.core

import com.futbolanalisis.core.geo.LocalProjection
import com.futbolanalisis.core.geo.Vec2
import com.futbolanalisis.core.model.Track
import com.futbolanalisis.core.model.TrackPoint
import com.futbolanalisis.core.model.TrackSource
import kotlin.math.cos
import kotlin.math.sin
import java.util.Random

/**
 * Genera un partido sintético realista de un mediocampista (1 Hz): 45' + 15' de descanso
 * en el banco + 47', con cambio de lado, ruido de GPS correlacionado, algunos saltos de
 * GPS y frecuencia cardiaca que sigue la intensidad con retardo.
 */
object SyntheticMatch {
    const val CENTER_LAT = 40.4530
    const val CENTER_LON = -3.6883
    const val PITCH_ANGLE_DEG = 28.0

    fun generate(
        seed: Int = 7,
        startMs: Long = 1_762_000_000_000L,
        homeX: Double = 50.0,
        homeY: Double = 30.0,
    ): Track {
        val rnd = Random(seed.toLong())
        val proj = LocalProjection(CENTER_LAT, CENTER_LON)
        val a = Math.toRadians(PITCH_ANGLE_DEG)
        fun toLatLon(px: Double, py: Double): Pair<Double, Double> {
            // Cancha 105x68 centrada en el origen y rotada.
            val x = px - 52.5; val y = py - 34.0
            return proj.toLatLon(Vec2(x * cos(a) - y * sin(a), x * sin(a) + y * cos(a)))
        }

        val points = ArrayList<TrackPoint>()
        var t = startMs
        var hr = 95.0
        var nx = 0.0; var ny = 0.0

        fun emit(px: Double, py: Double, speed: Double, glitch: Boolean = false) {
            nx = 0.85 * nx + rnd.nextGaussian() * 0.35
            ny = 0.85 * ny + rnd.nextGaussian() * 0.35
            val targetHr = if (speed < 0.5) 100.0 else 122 + speed * 10.5 + (if (speed > 5) 12 else 0)
            hr += (targetHr.coerceAtMost(191.0) - hr) * 0.05
            var (lat, lon) = toLatLon(px + nx, py + ny)
            if (glitch) { lat += 0.0009; lon += 0.0009 } // ~120 m de salto
            points += TrackPoint(t, lat, lon, 650.0, (hr + rnd.nextGaussian() * 1.5).toInt())
            t += 1000
        }

        fun half(seconds: Int, attackSign: Int, fatigue: Double) {
            // attackSign = +1 ataca hacia x=105 (coordenadas absolutas de la cancha).
            var x = 52.5; var y = 34.0
            var tx = x; var ty = y
            var speed = 1.0
            var boutLeft = 0
            var phase = 0.0
            for (s in 0 until seconds) {
                phase += 0.012 + rnd.nextDouble() * 0.01
                if (boutLeft <= 0) {
                    val r = rnd.nextDouble()
                    speed = when {
                        r < 0.52 -> 1.2 + rnd.nextDouble() * 0.6       // caminar
                        r < 0.80 -> 2.6 + rnd.nextDouble() * 1.2       // trote
                        r < 0.92 -> 4.3 + rnd.nextDouble() * 1.0       // carrera
                        r < 0.97 -> 5.7 + rnd.nextDouble() * 0.9       // alta intensidad
                        else -> 7.2 + rnd.nextDouble() * 0.9           // sprint
                    } * fatigue
                    boutLeft = if (speed > 5.5) 3 + rnd.nextInt(4) else 6 + rnd.nextInt(14)
                    // El bloque sube y baja con el juego; el jugador se mueve alrededor de su zona.
                    val relX = homeX + 12 * sin(phase) + rnd.nextGaussian() * 12
                    val relY = homeY + rnd.nextGaussian() * 11
                    tx = if (attackSign > 0) relX else 105 - relX
                    ty = if (attackSign > 0) relY else 68 - relY
                    if (speed > 5.5) tx += attackSign * 20.0 // los sprints suelen ir hacia adelante
                    tx = tx.coerceIn(2.0, 103.0); ty = ty.coerceIn(2.0, 66.0)
                }
                boutLeft--
                val dx = tx - x; val dy = ty - y
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                val step = if (d < 0.5) 0.0 else minOf(speed, d)
                if (d > 0) { x += dx / d * step; y += dy / d * step }
                if (d < 1.0) boutLeft = 0
                emit(x, y, step, glitch = rnd.nextDouble() < 0.0008)
            }
        }

        half(45 * 60, +1, 1.0)
        // Descanso: camina al banco y se queda quieto fuera de la cancha.
        repeat(15 * 60) { emit(52.5 + rnd.nextGaussian() * 0.3, -6.0 + rnd.nextGaussian() * 0.3, 0.1) }
        half(47 * 60, -1, 0.93)
        return Track(name = "Partido de ejemplo", source = TrackSource.SYNTHETIC, points = points)
    }
}
