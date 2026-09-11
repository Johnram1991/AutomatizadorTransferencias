# AutomatizadorTransferencias

App Android (Kotlin + Jetpack Compose) que extrae datos de PDFs de transferencias bancarias y los escribe en una plantilla Excel.

## Stack

- Kotlin, Android SDK 35, minSdk 26
- Jetpack Compose + Material 3
- Apache POI 5.2.5 (Excel .xlsx)
- PdfBox-Android 2.0.27.0

## Estructura principal

```
app/src/main/java/com/autotransfer/
├── MainActivity.kt             — Activity principal
├── MainApplication.kt          — Application
├── MainViewModel.kt            — ViewModel que orquesta todo el flujo
├── excel/
│   └── ExcelManager.kt         — Lectura/escritura de Excel .xlsx
├── pdf/
│   ├── ExtractorPDF.kt         — Extrae datos de PDF por patrones regex
│   └── PDFReader.kt            — Extrae texto plano de PDFs
├── file/
│   ├── FileNavigator.kt        — Escanea carpetas usando SAF
│   └── FileOrganizer.kt        — Mueve PDFs procesados
├── match/
│   └── OperationMatcher.kt     — Coteja datos de Concentrado vs Transfer
└── model/
    ├── ExcelConfig.kt          — Config de columnas Excel
    ├── Operacion.kt            — DTO de operación
    └── Rule.kt                 — Regla de extracción
```

## Cambios realizados

### 0. Ago 2026 — Meses bilingües (inglés/español), hoja auto-creada y clientes nuevos de zona
- **Problema**: Con datos de agosto, "Procesar desde Firebase" devolvía 0 operaciones (julio funcionaba).
- **Causa**: App Holland guarda las fechas con mes en inglés (`05-Aug-2026`); los parsers usaban meses en español (`Ago`). `Aug` → mes `00` → la venta quedaba fuera del rango `01-Ago..31-Ago`. Julio funcionó porque `Jul` es idéntico en ambos idiomas.
- **Nuevo `util/Meses.kt`**: mapa único mes→número que reconoce `Ago`/`Aug`, `Ene`/`Jan`, `Abr`/`Apr`, `Dic`/`Dec`, etc., y devuelve nombre de mes en español.
- **Aplicado en**:
  - `FirebaseRepository.dateToNum()` — repara el filtro de fechas (0 operaciones).
  - `MainViewModel.parseFecha()` — filtros Desde/Hasta en pantalla y ordenamiento.
  - `ExcelManager.getSheetName()` — resuelve la hoja del mes con ambos idiomas y formatos `dd-MMM-yyyy`, `dd/MM/yyyy` e ISO.
- **Excel**: si la hoja del mes (p. ej. "Agosto") no existe, `ExcelManager` la crea automáticamente copiando la fila de encabezados y anchos desde una hoja existente (`crearHojaSiFalta`).
- **Clientes nuevos de la zona**: App Holland y `getVentasParaExcel` ahora incluyen ventas cuyo cliente no está aún en la colección `clientes` si la venta trae la zona solicitada (`zonaVenta`).
- **Diagnóstico**: `procesarFirebaseDiagnostic` muestra también `fechaSubidoString` de cada venta.
- **Icono**: se agregaron al repo los PNG de `ic_launcher_foreground` y `mipmap-*dpi/ic_launcher.png` (antes solo existían en el build local, no en git). El keystore sigue en `keystore/release.jks` (ignorado, no se sube).

### 1. ExtractorPDF.kt — Patrón de continuación sin nombre
- **Problema**: 7 filas no obtenían el sufijo del noTransfer
- **Causa**: La línea de continuación `"3 18:08:14 18:18:14"` (solo sufijo + timestamps, sin nombre) no matcheaba ningún patrón
- **Solución**: Se agregó `Regex("""(\d{1,2})\s+()(\d{2}:\d{2}:\d{2})\s+(\d{2}:\d{2}:\d{2})""")` en `continuationPatterns` (línea 42)
- El `()` vacío como grupo 2 evita modificar el nombre del cliente

### 2. ExcelManager.kt — ZipSecureFile en companion object
- **Problema**: `EOFException: Unexpected end of ZLIB input stream` al abrir el Excel
- **Causa**: `ZipSecureFile.setMinInflateRatio(0.0)` se ejecutaba después de que POI ya había cargado sus clases
- **Solución**: Movido a `companion object init` (líneas 14-19), se ejecuta al cargar `ExcelManager`

### 3. ExcelManager.kt — Mejora en logging de errores
- El catch ahora imprime el stacktrace completo (primeros 1000 chars)

### 4. MainViewModel.kt — Mejora en logging
- El catch general imprime el stacktrace completo (primeros 1500 chars)

### 5. ExcelManager.kt — 3 estrategias en cascada para abrir .xlsx
- **Problema**: `Can't read content types part` en ciertos archivos .xlsx
- **Causa**: POI falla al leer `[Content_Types].xml` interno — varía según cómo se abra el ZIP
- **Solución**: Prueba 3 métodos en orden:
  1. `XSSFWorkbook(InputStream)` — code path directo
  2. `WorkbookFactory.create(File)` — auto-detecta formato
  3. `POIXMLDocument.openPackage(String)` → `XSSFWorkbook(OPCPackage)` — modo más permisivo
- Antes de abrir, verifica los primeros 4 bytes con magic `504b0304` para confirmar que es ZIP válido

### 6. App Holland — Tab "Resumen de ventas"
- Nuevo tercer tab "Resumen" en `HollandScreen` (junto a Clientes y Ventas) para ver ventas generales por cliente en un rango de fechas.
- **MainViewModel**: nueva `data class ResumenCliente(cliente, numVentas, total)`, estados `resumenClientes`/`resumenAsc`/`resumenClienteFiltro`, función `recalcularResumen()` que agrupa `_ventasFiltradas` por cliente (conteo + suma de total) y ordena por total según `_resumenAsc` (default descendente → mayor comprador primero). Públicos: `toggleResumenOrder()`, `setResumenClienteFiltro()`, `limpiarResumenClienteFiltro()`.
- **MainActivity**: toggle de 3 tabs; filtro de fechas compartido extraído a `HollandDateFilter` (Ventas y Resumen); tarjeta de venta extraída a `VentaItemCard` (evita duplicación).
- **Tab Resumen**: filtro por cliente (opcional), campo zona (default "25", al cambiar recarga vía `loadFirebaseData`), rango de fechas; card con total general y nº de clientes; botón `↓ Mayor total` / `↑ Menor total`; al tocar un cliente muestra el detalle de todas sus ventas del rango con botón `← Volver al resumen`.
- Todo es local en memoria (agrupa `_ventasFiltradas` ya cargadas); el único acceso a Firebase es una lectura al cambiar la zona (mismo patrón que el tab Clientes). No hay escrituras ni notificaciones a Firebase.

### 7. Sep 2026 — Filtro estricto de zona, lectura robusta de zona y escritura Excel verificada
- **Problemas**: 1) Al procesar desde Firebase con zona Z25 entraban transfers de otras zonas (p. ej. Z11A); 2) los archivos Excel se corrompían al escribir directo sobre OneDrive (prompt "Encontramos un problema con parte del contenido…", a veces ni se podían borrar/renombrar).
- **Zona — normalización (`FirebaseRepository` companion)**:
  - `normalizarZona(z)`: lowercase, quita espacios y guiones, `removePrefix("z")` — "Z 25"→"25", "Z11A"→"11a".
  - `normalizarNombre(nombre)`: lowercase, quita no-alfanuméricos, colapsa espacios.
  - `FirebaseVenta.fromMap` ahora lee la zona de forma robusta (`zonaClave ?: zona`, acepta `String`, `Number` y `Map{clave}` como la de `FirebaseCliente`). Antes si la zona venía en `zonaClave`/como mapa quedaba vacía y la regla de exclusión no actuaba.
  - Regla estricta en `getVentasParaExcel` y filtros de `MainViewModel`: si la venta trae zona llena → solo se incluye si su zona normalizada está en la selección; si la venta no trae zona → solo se incluye por coincidencia de nombre contra clientes de las zonas elegidas. Se eliminó la corrección hardcodeada `"macotel"→"macotela"` y el cruce `v.zonaVenta != clienteZona` que filtraba mal.
- **Excel — escritura con verificación y restauración (`ExcelManager`)**:
  - `updateExcel`: lee los bytes originales (`leerExcelBytes`, valida tamaño y magic ZIP `504b0304`), construye el nuevo workbook en memoria (`construirExcel`) y lo escribe con `escribirYVerificar` (write + relectura post-write). Si la escritura falla o el archivo queda inválido, **re-escribe los bytes originales** para restaurarlo y lo reporta.
  - `guardarCopiaNueva(context, fuenteUri, destinoUri, ops)`: nueva función para escribir la copia en un destino distinto (no toca el original) con la misma verificación.
  - **Eliminado `fixZipOrder`**: ya no se descomprime/recomprime el .xlsx (era una fuente de daño cuando el write a OneDrive no era atómico).
  - `prepararHojaDelMes` ahora elimina la hoja del mes solo si está **vacía** (sin filas de datos), y `crearHojaSiFalta` la regenera copiando desde la hoja machote real (`esHojaMachote`: nombre contiene "machote") con `copiarDesdeMachote` (anchos, alturas, estilos y merges). Si la hoja ya tiene datos, NO se reemplaza.
- **UI (`MainActivity`/`MainViewModel`)**: nuevo botón "📄 Guardar copia nueva (formato actual)" que abre el selector de guardado (`ACTION_CREATE_DOCUMENT`, nombre sugerido `<excel>_PROCESADO.xlsx`) y llama `grabarDatosExcelEn(uri)`. El botón "💾 Grabar datos Excel" sigue escribiendo al original pero con restauración automática si falla.
- **Diagnóstico**: `procesarFirebaseDiagnostic` imprime bloque `=== ZONAS ===` con las zonas solicitadas y las zonas reales de las ventas incluidas (crudo→normalizado) para depurar el cruce.

## Comandos útiles

```bash
# Compilar APK debug
./gradlew assembleDebug

# APK generado en:
# app/build/outputs/apk/debug/app-debug.apk

# Limpiar build
./gradlew clean
```

## Dependencias clave (app/build.gradle.kts)

- `com.tom-roush:pdfbox-android:2.0.27.0`
- `org.apache.poi:poi:5.2.5`
- `org.apache.poi:poi-ooxml:5.2.5` (excluye xmlbeans y stax-api)
- `org.apache.xmlbeans:xmlbeans:5.1.1`
- `com.fasterxml.woodstox:woodstox-core:6.5.1`

## Archivos de configuración (assets/)

- `config_excel.json` — Mapeo de columnas del Excel
- `patrones_concentrado.json` — Patrones regex para PDFs Concentrado
- `patrones_transfer.json` — Patrones regex para PDFs Transfer
- `patrones_correcciones.json` — Correcciones de nombres de clientes

---

## Estado actual del proyecto (Sep 2026)

### Objetivo
App Android que procesa PDFs de transferencias bancarias y actualiza Excel. Soporta SAF (Google Drive) y **ruta directa** para servicios sin DocumentsProvider como OneDrive. Tiene integración con Firebase Firestore (App Holland) para filtrar por zona y procesar ventas directamente.

### Características activas
- Selección de archivo Excel vía `ACTION_OPEN_DOCUMENT` (funciona con Samsung My Files, OneDrive, Drive)
- SAF vía `ACTION_OPEN_DOCUMENT_TREE` para seleccionar carpeta (Drive, almacenamiento local)
- App Holland: login Firebase, clientes por zona, ventas filtradas, city_review
- Procesamiento de PDFs (Concentrado/Transfer) con extracción por regex
- Escritura en Excel con Apache POI (3 estrategias de apertura + verificación post-write y restauración del original) — escribe directo en OneDrive
- Filtro estricto de zona (normalización `Z 25`/`25`/`Z11A`→`11a`) al procesar desde Firebase
- Botón "Guardar copia nueva (formato actual)" → escribe un archivo `.xlsx` nuevo sin tocar el original
- City repository (Room SQLite) + revisión manual de ciudades no reconocidas

### Problemas conocidos
1. OneDrive sin suscripción no sincroniza archivos localmente → los archivos se ven en Samsung My Files pero no tienen una ruta física accesible via `java.io.File`
2. El write directo a OneDrive no es atómico → puede corromper el Excel; ante dudas usar "Guardar copia nueva" en lugar de sobrescribir
3. Google Drive SAF funciona si el Excel está en la raíz y hay carpetas Transfer/Concentrado
4. Si SAF no encuentra el Excel, probar `runExcelDiagnostic` para ver exactamente qué detecta

### Próximo paso
- Probar `Intent.createChooser` para que aparezcan Samsung My Files, Google Drive, etc. al seleccionar carpeta

### APK último build
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk` (firmado), copia en `/storage/emulated/0/Download/Download/app-release.apk`
