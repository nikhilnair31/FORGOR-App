// app/src/main/java/com/sil/others/ThreadUtils.kt
package com.sil.others

import android.os.Looper
import java.io.PrintWriter
import java.io.StringWriter

object ThreadUtils {

    /**
     * Captures and returns the stack traces of all threads in the current process.
     */
    fun getAllThreadStackTraces(): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)

        val threadMap = Thread.getAllStackTraces()
        val currentThread = Thread.currentThread()

        printWriter.println("--- All Thread Stack Traces ---")
        printWriter.println("Current Thread: ${currentThread.name} (ID: ${currentThread.id})")
        if (Looper.myLooper() == Looper.getMainLooper()) {
            printWriter.println("This is the Main (UI) thread.")
        }

        for ((thread, stackTrace) in threadMap) {
            printWriter.println("\nThread: ${thread.name} (ID: ${thread.id}, State: ${thread.state})")
            if (thread == currentThread) {
                printWriter.println("  (Current Thread)")
            }
            if (thread == Looper.getMainLooper().thread) {
                printWriter.println("  (Main/UI Thread)")
            }
            for (element in stackTrace) {
                printWriter.println("    at $element")
            }
        }
        printWriter.println("--- End Thread Stack Traces ---")
        return stringWriter.toString()
    }
}