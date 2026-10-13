# AutomatizadorTransferencias

App Android (Kotlin + Jetpack Compose) que extrae datos de PDFs de transferencias
bancarias y los escribe en una plantilla Excel (.xlsx con Apache POI). Además trae
una integración con Firebase Firestore (App Holland) para filtrar ventas por zona,
una app de revisión de ciudades (Room SQLite) y un lector/escritor de Excel que
valida el paquete por dentro para que Excel no lo marque como dañado.

## Stack

- Kotlin, Android SDK 35, minSdk 26, targetSdk 35
- Jetpack Compose + Material 3
- Apache POI 5.2.5 (Excel .xlsx), xmlbeans 5.1.1, Woodstox 6.5.1
- PdfBox-Android 2.0.27.0 (extractor de texto de PDFs)
- Firebase Auth + Firestore (App Holland), Room 2.6.1 (ciudades)

## Estructura principal

```
app/src/main/java/com/autotransfer/
├── MainActivity.kt            — Activity principal (picker Excel/carpeta, diálogos, Holland)
├── MainApplication.kt         — Application (persiste permisos de URI, config inicial)
├── MainViewModel.kt           — ViewModel que orquesta todo el flujo (PDFs, Firebase, Excel)
├── data/
│   ├── AppDatabase.kt, CityDao.kt, CityDatabase.kt, CityEntity.kt, CityRepository.kt
│   │                          — Room: revisión manual de ciudades no reconocidas
│   └── firebase/
│       ├── FirebaseRepository.kt — Firestore: login, clientes, ventas, filtro de zona
│       ├── FirebaseCliente.kt, FirebaseVenta.kt  — DTOs + fromMap robusto
│       └── HollandCache.kt    — Caché diaria por zona en disco (filesDir/holland_<zonas>.json)
├── excel/
│   ├── ExcelManager.kt        — Lectura/escritura de Excel, estilos, fechas, verificación
│   ├── ExcelValidador.kt      — Valida el paquete por dentro (lo que Excel SÍ aplica)
│   └── PaqueteXlsx.kt         — Manipulación del ZIP: calcChain, apilado, relleno
├── file/
│   ├── FileNavigator.kt       — Escanea carpetas usando SAF
│   └── FileOrganizer.kt       — Mueve PDFs procesados
├── match/
│   └── OperationMatcher.kt    — Coteja datos de Concentrado vs Transfer
├── model/
│   ├── ExcelConfig.kt         — Config de columnas Excel (+ machoteSheet/plantillaSheet)
│   ├── Operacion.kt           — DTO de operación
│   └── Rule.kt                — Regla de extracción
├── pdf/
│   ├── ExtractorPDF.kt        — Extrae datos de PDFs por patrones regex
│   └── PDFReader.kt           — Extrae texto plano de PDFs
├── ui/
│   ├── cities/                — CitiesScreen, CitiesViewModel, CityEditDialog
│   ├── components/            — MunicipiosData, ProductTable
│   ├── holland/               — HollandScreen, Menu, Clientes, Detalle, Ventas, Resumen,
│   │                            Graficos, BarChart, DateFilter, Destino, VentaItemCard
│   ├── login/LoginScreen.kt   — Login de Firebase
│   ├── splash/OsirisSplashScreen.kt — Pantalla de arranque estilo OSIRIS (HUD/compás)
│   └── theme/                 — Color, SithBackground, Theme
└── util/
    ├── Fechas.kt              — Parseo bilingüe de fechas, rango 1º del mes → hoy
    ├── Fuzzy.kt               — Búsqueda difusa local (coincide/levenshtein)
    └── Meses.kt               — Mapa mes→número bilingüe (Ago/Aug, Ene/Jan…) + nombre español
```

## Comandos útiles

```bash
# Tests unitarios (Excel)
./gradlew testDebugUnitTest

# Compilar APK debug
./gradlew assembleDebug

# APK release firmado (keystore/release.jks, ignora .gitignore)
./gradlew assembleRelease

# APK generado en:
# app/build/outputs/apk/release/app-release.apk
# app/build/outputs/apk/debug/app-debug.apk

# Limpiar build
./gradlew clean
```

## Dependencias clave (app/build.gradle.kts)

- `com.tom-roush:pdfbox-android:2.0.27.0`
- `org.apache.poi:poi:5.2.5` + `poi-ooxml:5.2.5` (excluye xmlbeans y stax-api)
- `org.apache.xmlbeans:xmlbeans:5.1.1`
- `com.fasterxml.woodstox:woodstox-core:6.5.1` + `javax.xml.stream:stax-api:1.0-2`
- Firebase BOM 32.7.0 (`firebase-auth-ktx`, `firebase-firestore-ktx`)
- Room 2.6.1 (runtime/ktx + KSP compiler), `androidx.documentfile:documentfile:1.0.1`
- Tests: junit 4.13.2, `org.json:json:20231013` (el del SDK es un stub que lanza en JVM).
  `testOptions { unitTests.isReturnDefaultValues = true }` para que `android.util.Log` no reviente.

## Archivos de configuración (assets/)

- `config_excel.json` — Mapeo de columnas del Excel. Campos: `headerRow` (5),
  `dataStartRow` (6), `columnaId` (B), `columnas` (fecha=A, noTransfer=B, cliente=C,
  distribuidor=D, ciudad=E, monto=F), `excelFileName`, y `machoteSheet`/`plantillaSheet`
  = `"Machote en blanco"`.
- `patrones_concentrado.json` — Patrones regex para PDFs Concentrado
- `patrones_transfer.json` — Patrones regex para PDFs Transfer
- `patrones_correcciones.json` — Correcciones de nombres de clientes

---

## Estado actual (Oct 2026)

### Objetivo
App Android que procesa PDFs de transferencias bancarias y actualiza un Excel.
Soporta SAF (`ACTION_OPEN_DOCUMENT`/`ACTION_GET_CONTENT`) para el archivo y
`ACTION_OPEN_DOCUMENT_TREE` para carpetas. Tiene integración con Firebase (App
Holland) para filtrar por zona y procesar ventas directamente, y una revisión
manual de ciudades con Room.

### Características activas
- Selector del Excel con **diálogo de dos opciones**: "Drive / Archivos del sistema"
  (`ACTION_OPEN_DOCUMENT`, persistible) y "Mis archivos (Samsung) u otras apps"
  (`ACTION_GET_CONTENT`, acceso temporal). Si el permiso no queda persistido, la URI
  no se guarda en prefs y se avisa en pantalla.
- Escritura con un solo botón **"💾 Grabar datos Excel"** que pide confirmación
  antes de sobrescribir. `_pendingOperaciones` **no se vacía solo**: tras grabar se
  mantienen (re-grabar es idempotente por noTransfer) hasta el botón
  **"✖ Limpiar pendientes"**.
- **"📄 Guardar copia en otro lugar…"** (`ACTION_CREATE_DOCUMENT`): genera el Excel
  con los pendientes y lo escribe donde elijas (local, SD, etc.), sin tocar el original.
- **"🔬 Ver diagnóstico de escritura"**: reporta permisos, tamaños, apilado y el
  resultado de la última verificación, para distinguir cuenta vs. archivo.
- Toda escritura se verifica (tamaño + SHA-256 con reintentos). Si falla y el destino
  quedó ilegible, se restaura el original; el resultado y el original se rescatan en
  Descargas (`FormatoTransfer_NUEVO_<ts>.xlsx` / `_ORIGINAL_<ts>.xlsx`).
- Generación 100% en memoria (`construirExcel`), sin tocar el disco ni el machote;
  validación del paquete con `ExcelValidador` + limpieza de `calcChain` con
  `PaqueteXlsx` antes de escribir.
- App Holland: menú (Clientes/Ventas/Resumen/Gráficos), caché diaria por zona en
  disco, búsqueda difusa local, detalle de cliente y gráficas con Canvas.
- **Splash OSIRIS** al arrancar: fondo negro radial, compás rojo Sith girando, título
  "AUTO TRANSFER" letra por letra en blanco, subtítulo "BY JOHN RAM" en rojo, barra de
  progreso y mensajes de boot (PDF ENGINE / CONFIG / FIREBASE / SYSTEM READY). Colores
  de la app (paleta Sith de `ui/theme/Color.kt`, sin dorado). Fuentes JetBrains Mono
  (OFL) en `res/font/jetbrains_mono_{regular,bold}.ttf`. Dura ~**2 s**, sin botones,
  `onTimeout` automático, `rememberSaveable` para no repetirse al rotar.

### Problemas conocidos
1. **OneDrive online-only no refleja la escritura** (evidencia 12-oct, sección 15 del
   historial): `openOutputStream` no trunca y la verificación relee el buffer del
   proveedor, no el archivo real. La mtime cambia una vez y luego se congela. Por eso
   la app puede decir "Listo" y el archivo no tener nada. **CORREGIDO/confirmado**:
   la escritura sí llega a OneDrive (visible en otros dispositivos del folder); el
   cliente de OneDrive del celular es el que no baja los cambios de archivos
   online-only. Se reporta con advertencia y se ofrece "Guardar copia en otro lugar";
   en el celular usar "Disponible sin conexión"/Descargar.
2. Elegir el .xlsx con **"Mis archivos"** en un archivo de nube entrega solo lectura
   → `updateExcel` lo detecta antes de tocar nada y pide elegirlo con "Drive / Archivos
   del sistema".
3. El picker de carpeta no tiene equivalente a `GET_CONTENT`; las raíces se cambian
   con el ☰ del picker.
4. Si SAF no encuentra el Excel, `runExcelDiagnostic` (o el nuevo "Ver diagnóstico de
   escritura") muestra exactamente qué detecta.

---

## Historial de cambios

Las secciones siguientes son el registro histórico, en orden cronológico. Cuando una
decisión cambió después, la versión vigente está en "Estado actual" de arriba; este
historial se conserva como evidencia de los problemas y sus causas.

### 0. Ago 2026 — Meses bilingües (inglés/español), hoja auto-creada y clientes nuevos de zona
- **Problema**: Con datos de agosto, "Procesar desde Firebase" devolvía 0 operaciones (julio funcionaba).
- **Causa**: App Holland guarda las fechas con mes en inglés (`05-Aug-2026`); los parsers usaban meses en español (`Ago`). `Aug` → mes `00` → la venta quedaba fuera del rango. Julio funcionó porque `Jul` es idéntico en ambos idiomas.
- **Nuevo `util/Meses.kt`**: mapa único mes→número que reconoce `Ago`/`Aug`, `Ene`/`Jan`, `Abr`/`Apr`, `Dic`/`Dec`, etc., y devuelve nombre de mes en español.
- **Aplicado en**: `FirebaseRepository.dateToNum()`, `MainViewModel.parseFecha()`, `ExcelManager.getSheetName()` (con formatos `dd-MMM-yyyy`, `dd/MM/yyyy` e ISO).
- **Excel**: si la hoja del mes no existe, se crea copiando encabezados y anchos.
- **Clientes nuevos de zona**: se incluyen ventas cuyo cliente no está en `clientes` si la venta trae la zona solicitada (`zonaVenta`).
- **Icono**: PNGs de `ic_launcher_foreground`/`mipmap-*` subidos al repo. Keystore sigue en `keystore/release.jks` (ignorado).

### 1. ExtractorPDF.kt — Patrón de continuación sin nombre
- Se agregó `Regex("""(\d{1,2})\s+()(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})""")` en `continuationPatterns` (línea 42) para líneas de continuación sin nombre; el `()` vacío evita modificar el nombre del cliente.

### 2. ExcelManager.kt — ZipSecureFile en companion object
- `ZipSecureFile.setMinInflateRatio(0.0)` se ejecutaba después de que POI cargaba sus clases → movido al `companion object init` (se ejecuta al cargar `ExcelManager`).

### 3 y 4. Logging de errores
- `ExcelManager` y `MainViewModel` imprimen stacktrace completo (primeros 1000/1500 chars).

### 5. ExcelManager.kt — 3 estrategias en cascada para abrir .xlsx
- POI fallaba con `Can't read content types part` → se probaban 3 métodos (InputStream, `WorkbookFactory.create(File)`, `POIXMLDocument.openPackage`). **Superado por la sección 10**: hoy se abre y serializa 100% en memoria sin cascada ni archivo temporal.

### 6. Ago 2026 — App Holland: tab "Resumen de ventas"
- Tercer tab con ventas generales por cliente en un rango de fechas. `ResumenCliente(cliente, numVentas, total)`, `recalcularResumen()` agrupa `_ventasFiltradas`, orden por total (default descendente), filtro por cliente opcional. Todo local en memoria; el único acceso a Firebase es una lectura al cambiar la zona.
- **Nota**: este tab quedó en el paquete `ui.holland` tras el rediseño de la sección 9.

### 7. Sep 2026 — Filtro estricto de zona, lectura robusta de zona y escritura Excel verificada
- **Zona**: `normalizarZona` (lowercase, sin espacios/guiones, `removePrefix("z")`), `normalizarNombre`; `FirebaseVenta.fromMap` lee zona de forma robusta (`zonaClave ?: zona`, String/Number/Map). Regla estricta: venta con zona llena solo entra si coincide con la selección; sin zona, solo por coincidencia de nombre contra clientes de las zonas. Eliminada la corrección hardcodeada `"macotel"→"macotela"`.
- **Excel**: `updateExcel` con verificación post-write y restauración del original; `guardarCopiaNueva` (vacía a partir de la sección 11); eliminado `fixZipOrder`.
- **UI**: botón "Guardar copia nueva" y "Grabar datos Excel". **Obsoletos** desde la sección 11/15.

### 8. Sep 2026 — Respaldo previo, verificación por hash y prueba de generación local
- `config_excel.json` gana `machoteSheet` = `"Machote en blanco"`. Escritura con `escribirYVerificar` (`openOutputStream(uri, "rwt")` + flush + relectura con SHA-256). Respaldo automático en Descargas, `copiarDesdeMachote` con merges protegidos. `probarGeneracionLocal` para distinguir OneDrive vs POI. **Obsoleto parcialmente desde la 8/11** (el flujo pasó a un solo botón en la 11).

### 9. Sep 2026 — App Holland rediseñada: menú, detalle de cliente, gráficas e icono propio
- Icono de Holland (H blanca, perro y gato, círculo `#FF4237`), generado con `/tmp/opencode/gen_icon.py`. `.gitignore` fuerza `!app/src/main/res/**/ic_launcher*.png`.
- Menú en vez de tabs (`HollandDestino`), carga bajo demanda con `LaunchedEffect`, búsqueda difusa local (`util/Fuzzy.kt`), detalle de cliente con rango por defecto, gráficas con `Canvas` (sin librerías), `util/Fechas.kt`. `MainViewModel` usa flujos propios por pantalla.

### 10. Sep 2026 — Excel: diagnóstico anterior corregido + caché diaria en App Holland
- **Corrección**: `removeSheetAt()` NO rompía el archivo; el culpable era el write a OneDrive cortado a medias. `ExcelValidador` nuevo (valida por dentro: Content_Types, relaciones, XML, calcChain), `PaqueteXlsx.quitarCalcChain`, `construirExcel` puro e in-memory, `asegurarHojaDelMes` (nunca borra la hoja; puebla en su lugar). Fecha como *fecha real* de Excel. `copiarPlantilla` clona merges.
- **Holland**: `ProductTable` con anchos fijos + `horizontalScroll`; `HollandCache` diario por zona en disco; botón "Actualizar Datos"; fechas 1º del mes → hoy; "Buscar por Ciudad".
- **Tests**: `ExcelPipelineTest` (14 tests con el archivo real como fixture).

### 11. Oct 2026 — Dos bugs de escritura: verificador que se negaba a trabajar y .xlsx de 0 KB
- **Bug 1**: el APK instalado usaba `DocumentBuilderFactory.setFeature(".../load-external-dtd", false)` que en Android lanza `IllegalArgumentException` → 38 "XML mal formados" falsos. `ExcelValidador` separa **errores** de **avisos**; cascada de parsers Woodstox → DOM sin `setFeature`; seam `factoryXml` para tests.
- **Bug 2**: `guardarEnDescargas` sin `RELATIVE_PATH` → archivos de 0 KB. Ahora pone `RELATIVE_PATH=Download/`, verifica tamaño+SHA-256, borra si no cuadra; falla a `getExternalFilesDir`.
- **Flujo simplificado (decisión del usuario)**: un solo botón "💾 Grabar datos Excel", se eliminaron `guardarCopiaNueva`, `probarGeneracionLocal`, respaldo automático. Rescate `NUEVO`/`ORIGINAL` en Descargas si OneDrive falla. Pendientes solo se limpian con éxito.

### 12. Oct 2026 — Selector del Excel: "Mis archivos" (Samsung) y acceso temporal
- Diálogo con dos botones: "Drive / Archivos del sistema" (`ACTION_OPEN_DOCUMENT`) y "Mis archivos (Samsung) u otras apps" (`ACTION_GET_CONTENT`). `setExcelUri` comprueba `persistedUriPermissions`; si no queda persistido no se guarda en prefs y se enciende `excelAccesoTemporal`. Al arrancar, una URI guardada sin permiso se descarta. `limpiarSobrasDePrueba` también borra `*_PROCESADO.xlsx` de 0 bytes.

### 13. Oct 2026 — OneDrive no acepta la escritura: permiso, verificación y restauración
- Con "Mis archivos", nube entrega solo lectura → `openOutputStream` lanza `SecurityException`. `tienePermisoEscritura()` (con `checkUriPermission`) **antes** de tocar nada.
- Con SAF sí hay permiso pero la relectura devuelve el contenido anterior → éxito falso (rescatados con tamaños iguales). `escribirConModos` (seam puro), `verificarEscritura` con 3 reintentos/500 ms, `estaDanado()` decide restauración solo si el destino quedó ILEGIBLE. Mensajes con `Causa: …`.
- Pendiente entonces: prueba guiada con "Disponible sin conexión".

### 14. Oct 2026 — .xlsx corrupto (ZIP apilado) y formatos mezclados
- **Apilado**: el proveedor escribió sin `O_TRUNC` → dos ZIP apilados y Excel leía el directorio viejo. `PaqueteXlsx.diagnosticar` / `primeraCopia` / `rellenarHasta` (comentario en el EOCD). `ExcelValidador.analizar` corta si hay apilado. `leerExcelBytes` se queda con la primera copia. `escribirConRelleno` reescribe igualado al tamaño del destino.
- **Formatos**: `estiloPlantilla`/`aplicarEstilosDePlantilla` heredan borde y tipografía del machote (B..G = estilo de la fila 6, F contable). Fechas: todas reales con un solo aspecto `FMT_FECHA = "[$-409]dd-MMM-yyyy"` vía `estiloFecha` + `normalizarFechas` (604 normalizadas con el archivo real).
- **Tests**: `EscrituraYFormatoTest` (11) + `ExcelPipelineTest` (18) = **29, 0 fallos**. Comprobado contra el archivo corrupto real.

### 15. Oct 2026 — "Exitoso" sin cambios reales en OneDrive + vuelve la copia local
- **Evidencia (12-oct, en el teléfono)**: la previsualización mostraba los datos bien, pero al abrir el archivo no estaban. La **fecha de modificación del archivo cambió la primera vez** y en los intentos siguientes quedó **congelada**; con un segundo archivo pasó lo mismo (mensaje "Exitoso" sin ningún cambio visible, ni hora, ni previsualización).
- **Causa**: se confirma que `openOutputStream` de OneDrive vía SAF no trunca y que **la verificación por relectura es circular para las nubes**: relee el buffer que el propio proveedor acaba de entregar, que no es el archivo que el usuario abre. La mtime que se mueve una sola vez y luego nunca más es la firma de que el proveedor no está reflejando las escrituras.
- **Cambios**:
  - `updateExcel`: cuando hubo relleno (destino que no recorta) el mensaje ya NO canta victoria: advierte que "la app cree que quedó escrito, pero puede que el archivo no se actualice (revisa la fecha de modificación)" y recomienda "Guardar copia en otro lugar".
  - `_pendingOperaciones` **nunca se vacía solo** (se quitó el auto-clear): re-grabar es idempotente (busca por noTransfer) y el usuario decide con el botón **"✖ Limpiar pendientes"**.
  - Vuelve la opción local que se había quitado: **"📄 Guardar copia en otro lugar…"** (`ExcelManager.guardarCopiaEn(context, fuenteUri, destinoUri, ops)` con `ACTION_CREATE_DOCUMENT`, sugiere `FormatoTransfer_NUEVO_<ts>.xlsx`). No toca el original ni vacía pendientes.
  - **"🔬 Ver diagnóstico de escritura"** (`ExcelManager.diagnosticarEscritura`): permisos read/write (persistidos o no), tamaño leído ahora, magic ZIP, estructura (apilado/sobras), tamaño del último paquete escrito, tamaño devuelto por el proveedor, último error y si hubo relleno. Para distinguir cuenta vs. archivo con datos.
- **Estado de la prueba guiada**: sigue pendiente probar con el archivo marcado "Disponible sin conexión" (sección 13). Con el diagnóstico nuevo, el plan es: grabar una vez → revisar mtime y contenido; grabar otra vez → ver si la mtime se congela; "Guardar copia en otro lugar…" a Descargas → abrir esa copia en Excel para confirmar que la generación es correcta.

### 16. Oct 2026 — Splash OSIRIS + prueba guiada resuelta
- **Prueba guiada resuelta (secciones 13/15)**: la escritura SÍ llega a OneDrive y se ve
  correctamente en **otros dispositivos** con acceso al folder. El problema era solo el
  cliente de OneDrive del celular que genera los datos, que **no baja los cambios** de
  archivos online-only ("no se descargan cambios"; mtime congelada). No es cuenta, no es
  archivo, no es la app.
- **Splash estilo osirisai.live** integrado (guía `GUIA_OSIRIS.md`, archivo de referencia
  `OsirisSplashScreen.kt`):
  - Nuevo `ui/splash/OsirisSplashScreen.kt` en el package `com.autotransfer.ui.splash`,
    íntegro del proyecto de referencia pero con `BOOT_STEPS` personalizados:
    `INITIALIZING PDF ENGINE... / LOADING CONFIG... / CONNECTING FIREBASE... / SYSTEM READY`.
  - **Colores de la app** (no los de la guía): fondo `SithBlack`/`SithDarkGray`, acentos
    `SithRed`/`SithCrimson`/`SithHighlight`, texto blanco/gris (`SithText*`). Sin dorado.
  - Fuentes JetBrains Mono en `res/font/jetbrains_mono_{regular,bold}.ttf` (OFL).
  - `MainActivity.AutoTransferApp`: `var splash by rememberSaveable { mutableStateOf(true) }`;
    si `splash` muestra `OsirisSplash(title = "Auto Transfer", subtitle = "By John Ram",
    onTimeout = { splash = false })` y `return`. Sin botones; se dispara solo (~**2 s**:
    200 ms + 4 pasos × 300 ms + 600 ms = 2.000 ms). `rememberSaveable` evita que se repita
    al rotar. "V <versionName>" en la esquina (versionName 1.0 del gradle).

### Próximo paso
- La prueba guiada de OneDrive quedó resuelta (sección 16): para que el celular que genera
  los datos vea las transferencias, marcar el Excel como "Disponible sin conexión" o
  "Descargar"; la app ya avisa y ofrece "Guardar copia en otro lugar" si el proveedor
  no refleja el cambio.

### APK último build
- `app/build/outputs/apk/release/app-release.apk` (firmado), copia en
  `/storage/emulated/0/Download/Download/Autotransfer/Auto Transfer V5.apk`
  (13-oct, release con splash Sith de 2 s, 27.428.843 B, SHA-256
  `dfc5c0cfe11b062f2c0a422ad49e6e6408f4b56d3d130aa026a2f99b98da403e`).
- Histórico 12-oct (splash sin adaptar): `Auto Transfer V5.apk`, 27.428.843 B,
  SHA-256 `fe83c16214c4e57763c31af559ed0e1cefd7507c67d246e22a0e95833416892b`.
- Histórico 12-oct (sin splash): `Auto Transfer V5.apk` previo, 27.170.851 B,
  SHA-256 `cb2775a5fae3d6f1486e8900dab943d3f9e940b74964ef9a28577d397b1bc3c6`.
- Histórico 06-oct: `AutoTransfer4v.apk` (27.170.851 B,
  SHA-256 `20a9e071f651a17ae58c8f83b745ca6a2ec13cb6f01e640cc1d8333b16816755`).