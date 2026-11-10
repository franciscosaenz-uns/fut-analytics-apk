# Métricas de análisis individual en fútbol (investigación para v0.2)

Resumen de lo que se usa hoy en clubes y en la literatura de ciencias del deporte con GPS/GNSS y FC,
y qué hace la app con cada una. ✓ = incorporada, ◐ = parcial, ○ = pendiente (y por qué).

## Carga externa (GPS)
| Métrica | Qué mide | Estado |
|---|---|---|
| Distancia total y relativa (m/min) | Volumen e intensidad media | ✓ |
| Distancia por zonas de velocidad (HSR > 19,8, sprint > 25,2 km/h) | Intensidad (Bradley et al. 2009) | ✓ |
| **Umbrales individualizados** (% de la velocidad máxima) | Compara jugadores de distinto nivel (Abt & Lovell 2009; Gabbett 2015) | ✓ modo por defecto |
| Número de sprints y velocidad máxima | Capacidad de sprint | ✓ |
| Aceleraciones y desaceleraciones | Carga neuromuscular (Harper et al. 2019) | ✓ (umbral 2 m/s² por GPS de muñeca a 1 Hz) |
| **Picos de demanda / worst-case scenarios** (1', 3', 5', 10') | Pasajes más intensos, referencia para entrenar (Delaney et al. 2018) | ✓ |
| **Esfuerzos repetidos de alta intensidad (RHIE)** | ≥3 esfuerzos con < 21 s de pausa (Spencer et al. 2004) | ✓ |
| **Potencia metabólica y HMLD** | Coste energético de acelerar ≈ correr cuesta arriba (di Prampero 2005; Osgnach 2010) | ✓ |
| **Gasto energético** (kJ/kg, kcal) | Estimado desde la potencia metabólica | ✓ |
| Relación trabajo : pausa | Densidad del esfuerzo | ✓ |
| Índice de fatiga / caída entre bloques | Comparación de bloques de 15' (Mohr et al. 2003) | ✓ + alertas |
| PlayerLoad / impactos | Requiere acelerómetro crudo a 100 Hz | ○ el reloj no lo exporta |
| Cambios de dirección | Requiere GPS ≥ 10 Hz para ser fiable | ○ |

## Carga interna (FC y percepción)
| Métrica | Estado |
|---|---|
| FC media/máx, % FCmáx, tiempo en zonas Z1–Z5 | ✓ |
| TRIMP de Edwards | ✓ |
| Tiempo > 85% y > 92% FCmáx | ✓ (+ alerta) |
| **sRPE** (RPE × minutos, Foster 2001) | ✓ (se indica en ⚙) |
| **Eficiencia / desacople cardiaco** (metros por latido entre tiempos) | ✓ |
| HRV matinal vs. media de 7 días (recuperación) | ✓ vía Health Connect |
| ACWR (carga aguda : crónica, Gabbett 2016) | ○ requiere varias semanas de partidos y entrenos; próximo paso |

## Táctica individual
| Métrica | Estado |
|---|---|
| Mapa de calor, zona más frecuente (moda) y zonas calientes | ✓ |
| Ocupación 3×3 (tercios × carriles), presencia en áreas | ✓ |
| Área de influencia (elipse 95%), profundidad y ancho cubiertos (p10–p90) | ✓ |
| Distancia por tercio, sprints que terminan en el último tercio | ✓ |
| Dirección de los sprints (ruptura vs. repliegue) | ✓ |
| Rol táctico descrito a partir del mapa de calor | ✓ (reglas explicables) |
| Posición relativa al equipo y al balón | ○ necesita datos de compañeros o del balón |

## Indicador de energía de la app
Combina tres reservas (0-100):
1. **Anaeróbica (W' balance, Skiba 2012/2015)** con la potencia metabólica: baja con esfuerzos sobre la potencia crítica (≈ coste de correr al 50% de tu velocidad máxima) y se recupera exponencialmente al bajar el ritmo.
2. **Resistencia**: trabajo metabólico acumulado frente a un partido profesional completo (~60 kJ/kg).
3. **Cardiaca** (si hay FC): se vacía sosteniendo > 85% de FCmáx.

Pesos: 45/35/20 con FC, 55/45 sin FC. Es un modelo orientativo, no un diagnóstico.

## Índice de rendimiento (v0.4)
Cinco atributos de 0 a 99. Cada dato se sitúa linealmente entre una referencia baja (≈ 30 puntos, fútbol recreativo) y
una alta (≈ 95, profesional). Los datos de volumen se normalizan a 90 minutos.

| Atributo | Peso | Datos (referencia baja → alta) |
|---|---|---|
| Velocidad | 20 % | Velocidad máxima 18 → 32 km/h (70 %); distancia ≥ 19,8 km/h 0 → 700 m (30 %) |
| Resistencia | 25 % | Ritmo 55 → 120 m/min (70 %); distancia ≥ 14,4 km/h 0 → 2200 m (30 %) |
| Intensidad | 25 % | HMLD 200 → 2200 m (50 %); pico de 1 minuto 90 → 190 m/min (25 %); potencia metabólica 5 → 11 W/kg (25 %) |
| Explosividad | 15 % | Aceleraciones + frenadas 8 → 70 por 90' (umbral 2 m/s² con GPS de muñeca) |
| Aguante | 15 % | Índice de fatiga −40 → +5 % (40 %); 2º tiempo vs 1º −25 → +5 % (30 %); energía final 15 → 80 % (30 %) |

Niveles: < 52 en construcción, 52-65 amateur, 66-77 competitivo, 78-87 avanzado, 88+ élite.
Las referencias altas salen de valores típicos de fútbol profesional (≈ 110-120 m/min, 30-33 km/h); las de explosividad
están ajustadas a lo que mide un reloj a 1 Hz, no a un GPS de chaleco. Habrá que afinarlas con partidos reales.

## Comparación entre partidos (v0.4)
Una diferencia cuenta como real solo si supera el margen de ruido de la métrica: 3 % en velocidad máxima, 4 % en ritmo y
picos, 5-8 % en distancias, 10-15 % en conteos (sprints, aceleraciones). FC media y TRIMP se muestran sin juicio
(más alto no es mejor ni peor).
