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
