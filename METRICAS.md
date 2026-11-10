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
