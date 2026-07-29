package com.autotransfer

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Process
import android.provider.MediaStore

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = throwable.stackTraceToString()
                val fileName = "AutoTransfer_crash_${System.currentTimeMillis()}.log"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { os ->
                            os.write(stackTrace.toByteArray(Charsets.UTF_8))
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                        contentResolver.update(it, contentValues, null, null)
                    }
                }
            } catch (_: Exception) {}

            Process.killProcess(Process.myPid())
        }
    }
}
