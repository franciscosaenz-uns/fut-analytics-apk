# Decisiones de la v0.1

## Fuente de datos
1. **GPS por archivo GPX/TCX** (exportado de Zepp o Strava) como entrada principal. Zepp no tiene API pública
   de datos crudos y no siempre publica la ruta en Health Connect.
2. **Health Connect** para fisiología: FC, FC en reposo, HRV (RMSSD), SpO2, VO2 máx, pasos y sesiones de ejercicio
   (con su ruta si existe). Solo lectura. Todo es opcional: si Zepp no comparte un dato se muestra "—".
3. **FIT no soportado aún** (formato binario; v0.2). El importador lo detecta y da un mensaje claro.
4. La FC del archivo tiene prioridad; si no trae, se toma de Health Connect en la ventana del partido.
5. **Gadgetbridge** queda como opción futura: no pude confirmar desde aquí que soporte el Bip Max (modelo nuevo).

## Arquitectura
6. **Kotlin + Jetpack Compose + Material 3**, minSdk 26 (requisito de Health Connect), target 35.
7. **Motor de análisis separado (`core`) en Kotlin puro**: se prueba sin emulador y se podrá reutilizar
   (backend, otras plataformas o relojes de otras marcas).
8. **Almacenamiento local en JSON** (un archivo por partido con datos crudos + opciones + reporte), sin base de
   datos ni servidor. Los datos no salen del teléfono. Re-analizar es instantáneo.
9. Sin mapas de Google (evita API key). La cancha se dibuja en Canvas.
10. APK de release firmado con la clave de debug (solo para instalar a mano en v0.1).

## Análisis físico
11. Zonas de velocidad estándar (km/h): caminar <7, trote 7–14.4, carrera 14.4–19.8, alta intensidad 19.8–25.2,
    sprint >25.2. Interruptor en el perfil para **umbrales amateur** (6 / 12 / 16 / 21).
12. Limpieza: se descartan saltos de GPS >41 km/h; velocidad suavizada con media móvil de 3 muestras.
13. Sprint / carrera de alta intensidad = al menos 2 s por encima del umbral.
14. Aceleraciones/frenadas con umbral **2 m/s²** (no 3) porque el GPS de muñeca a 1 Hz con suavizado atenúa los picos.
15. **Detección automática del descanso**: hueco de registro ≥3 min o racha ≥5 min de quietud en el 30–70% central
    del registro. Solo cuenta el tiempo de juego. Opción manual "sin descanso".
16. Bloques de 15 minutos e índice de fatiga (m/min del último bloque vs. el primero).

## Fisiología
17. FC máxima: la del perfil o, si no hay, **Tanaka (208 − 0.7·edad)**.
18. Zonas Z1–Z5 por % de FCmáx (50/60/70/80/90) y **TRIMP de Edwards** como carga interna.
19. Recuperación por **HRV vs. media de los 7 días previos** (±5% normal, < −10% fatiga).

## Táctico y mapa de calor
20. **Calibración automática de la cancha**: el eje largo sale del análisis de componentes principales del recorrido
    y el rango entre percentiles 2–98 se ajusta a 105×68 m. Es aproximada: funciona bien si el jugador recorre casi
    todo el campo; para porteros/centrales hará falta la calibración por esquinas (ya implementada en el motor, falta UI).
21. El **segundo tiempo se rota 180°** para que todo el partido ataque hacia la derecha.
22. Dirección de ataque automática: en el 1er tiempo el jugador pasa más tiempo en su mitad. Si sale al revés
    (típico de delanteros), se invierte con un interruptor.
23. Mapa de calor ponderado por tiempo, cuadrícula de 5 m (21×14) con desenfoque gaussiano, interpolado al dibujar.
24. Táctico: ocupación 3×3, presencia en áreas, elipse del 95% (área de influencia), posición media por tiempo,
    vectores de sprint (adelante / atrás / lateral) y **posición estimada** por reglas simples (orientativa).

## Hoja de ruta propuesta
- v0.2: calibración tocando las esquinas sobre un mapa (OSM) y canchas guardadas; importación FIT; editar inicio/fin
  de cada tiempo; exportar reporte PDF/imagen.
- v0.3: tendencias entre partidos (carga aguda/crónica, ACWR), comparación con la HRV de la mañana; Gadgetbridge.
- v0.4: otras marcas (Garmin, Coros, Polar vía Health Connect/FIT); análisis de equipo con varios jugadores.

# Decisiones de la v0.2

1. **Posición por la moda**: la posición estimada y el rol usan la celda más caliente del mapa de calor
   (refinada con su vecindad), no la media. La media se sigue calculando pero solo para avisar cuando engaña.
2. **Rol táctico descrito** con reglas explicables a partir de la zona más frecuente, profundidad/ancho cubiertos
   (percentiles 10–90), ocupación por tercios y carriles, dirección de sprints, presencia en área y cambio entre tiempos.
   13 arquetipos (box-to-box, pivote, lateral ofensivo, extremo a pierna cambiada, delantero móvil, etc.).
3. **Recorte** del registro con un deslizador sobre la curva de metros por minuto. Se guarda como opción del partido
   (el registro original no se borra, se puede deshacer).
4. **Umbrales adaptativos por defecto**: velocidad máxima de referencia = percentil 99,8 de la velocidad suavizada
   (≈10 s más rápidos, ignora picos sueltos de GPS), acotada a 18–36 km/h. Trote 30%, carrera 55%, alta intensidad 70%,
   sprint 85%. Se puede volver a los umbrales fijos (profesional o amateur) por partido o en el perfil.
5. **Energía**: modelo de tres reservas (ver METRICAS.md) con potencia metabólica de di Prampero/Osgnach (KT = 1,29 en césped).
6. **Alertas** con minuto de juego (acumulado, el 2º tiempo continúa donde terminó el 1º): energía < 30%,
   esfuerzos repetidos, pasaje más intenso, velocidad máxima, caídas de rendimiento por bloque, FC > 92% sostenida,
   baja participación (≥4' bajo 50 m/min) y desacople cardiaco.
7. **Calibración satelital** con osmdroid + imágenes de Esri World Imagery (sin API key, con atribución). Se tocan
   3 esquinas en cualquier orden; la app corrige la orientación y mide el tamaño real de la cancha. Las canchas se
   pueden guardar y se aplican solas a partidos nuevos jugados a menos de 150 m.
8. **Inicio rediseñado**: cabecera con degradado, resumen acumulado, miniaturas de mapa de calor por partido y
   guía de 3 pasos para compartir el GPX desde Zepp. La app acepta archivos compartidos de tipo application/* y text/*.
9. **Métricas nuevas** tras revisar la literatura: picos de demanda 1/3/5/10', RHIE, potencia metabólica, HMLD,
   gasto energético, trabajo:pausa, sRPE, desacople cardiaco y distancia por tercio. Detalle en METRICAS.md.
10. Los partidos guardados con la v0.1 se re-analizan solos al abrirlos (se conservan los datos crudos).

# Decisiones de la v0.3

1. **Registro del partido** (pantalla propia): rival, resultado, posición jugada, superficie, goles, asistencias, tiros y
   tiros al arco, pases clave, recuperaciones, duelos, tarjetas, RPE (escala CR-10), autoevaluación, sensaciones y notas.
   El RPE alimenta la carga sRPE y la posición jugada orienta la dirección de ataque. Los totales de goles y asistencias
   aparecen en el inicio.
2. **Dirección de ataque con pista de posición**: si la posición (del registro o la habitual del perfil) es ofensiva,
   la heurística asume que el jugador pasa más tiempo en campo rival. Antes un delantero salía invertido por defecto.
3. **Consejos para el partido** (pestaña Coach): reglas explicables sobre energía, ritmo, caídas de intensidad,
   esfuerzos repetidos, desconexión, FC, posición según el rol, producción (tiros, duelos, llegada) y recuperación
   (RPE alto con FC baja, HRV baja). Cada consejo indica su minuto cuando aplica. Máximo 8, ordenados por prioridad.
4. **Entrenamientos**: los físicos salen de los datos (sprint repetido si cae la intensidad, 4×4 aeróbico si la energía
   baja o hay desacople, velocidad si hay pocos sprints, cambios de dirección si hay pocas aceleraciones, y siempre
   prevención de isquios/aductores). Los técnicos y tácticos salen de una biblioteca por grupo de rol (8 grupos).
5. **Comparación con profesionales**: huella de rol de 9 rasgos (zona más frecuente, tercios, carril central, recorrido
   vertical y lateral, área, dirección de sprints) comparada con 33 perfiles aproximados de jugadores actuales.
   Los perfiles son estimaciones a partir de mapas de calor y descripciones de rol públicas, no datos oficiales de tracking;
   la app lo aclara. Similitud = exp(−distancia ponderada²).
