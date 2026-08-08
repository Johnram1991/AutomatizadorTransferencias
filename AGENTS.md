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

## Estado actual del proyecto (28 Jul 2026)

### Objetivo
App Android que procesa PDFs de transferencias bancarias y actualiza Excel. Soporta SAF (Google Drive) y **ruta directa** para servicios sin DocumentsProvider como OneDrive. Tiene integración con Firebase Firestore (App Holland) para filtrar por zona y procesar ventas directamente.

### Características activas
- Selección de archivo Excel vía `ACTION_OPEN_DOCUMENT` (funciona con Samsung My Files, OneDrive, Drive)
- SAF vía `ACTION_OPEN_DOCUMENT_TREE` para seleccionar carpeta (Drive, almacenamiento local)
- App Holland: login Firebase, clientes por zona, ventas filtradas, city_review
- Procesamiento de PDFs (Concentrado/Transfer) con extracción por regex
- Escritura en Excel con Apache POI (3 estrategias de apertura) — escribe directo en OneDrive
- City repository (Room SQLite) + revisión manual de ciudades no reconocidas

### Problemas conocidos
1. OneDrive sin suscripción no sincroniza archivos localmente → los archivos se ven en Samsung My Files pero no tienen una ruta física accesible via `java.io.File`
2. Google Drive SAF funciona si el Excel está en la raíz y hay carpetas Transfer/Concentrado
3. Si SAF no encuentra el Excel, probar `runExcelDiagnostic` para ver exactamente qué detecta

### Próximo paso
- Probar `Intent.createChooser` para que aparezcan Samsung My Files, Google Drive, etc. al seleccionar carpeta

### APK último build
- `app/build/outputs/apk/debug/app-debug.apk`
