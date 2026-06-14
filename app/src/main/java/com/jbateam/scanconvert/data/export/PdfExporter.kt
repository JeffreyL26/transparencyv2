package com.jbateam.scanconvert.data.export

import android.content.Context
import android.print.PdfPrint
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Rendert die Export-HTML ([buildListExportHtml]) in eine echte, vektorbasierte A4-PDF.
 *
 * Weg (§2 der Spec): eine Offscreen-[WebView] lädt das HTML (Fonts aus `assets/export/`),
 * danach wird über `createPrintDocumentAdapter()` + [PdfPrint] direkt in eine Datei
 * „gedruckt" — NICHT über den System-Druckdialog. Läuft auf dem Main-Thread
 * (WebView/Print-Adapter). Liefert die fertige Datei oder `null` bei Fehler.
 */
object PdfExporter {

    suspend fun renderListPdf(context: Context, html: String, outFile: File): File? =
        withContext(Dispatchers.Main) {
            val app = context.applicationContext
            suspendCancellableCoroutine { cont ->
                var settled = false
                var printStarted = false
                lateinit var webView: WebView

                fun finish(result: File?) {
                    if (settled) return
                    settled = true
                    webView.post { runCatching { webView.destroy() } }
                    if (cont.isActive) cont.resume(result)
                }

                webView = WebView(app).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (printStarted) return
                            printStarted = true
                            // Kurze Verzögerung, damit @font-face + Layout sicher fertig sind.
                            view.postDelayed({
                                runCatching {
                                    val adapter = view.createPrintDocumentAdapter(outFile.nameWithoutExtension)
                                    PdfPrint.print(adapter, outFile) { ok -> finish(if (ok) outFile else null) }
                                }.onFailure { finish(null) }
                            }, 350)
                        }
                    }
                }
                cont.invokeOnCancellation { webView.post { runCatching { webView.destroy() } } }
                webView.loadDataWithBaseURL(
                    "file:///android_asset/export/", html, "text/html", "UTF-8", null,
                )
            }
        }
}
