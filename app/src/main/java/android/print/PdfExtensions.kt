package android.print

import android.content.ContentResolver
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.documentfile.provider.DocumentFile
import com.philkes.notallyx.utils.nameWithoutExtension

/**
 * Needs to be in android.print package to access the package private methods of
 * [PrintDocumentAdapter]
 */
fun Context.printPdf(file: DocumentFile, content: String, pdfPrintListener: PdfPrintListener) {
    val webView = WebView(this)
    webView.loadDataWithBaseURL(null, content, "text/html", "utf-8", null)
    webView.webViewClient =
        object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                val adapter = webView.createPrintDocumentAdapter(file.nameWithoutExtension!!)
                contentResolver.printPdf(file, adapter, pdfPrintListener)
            }
        }
}

private fun ContentResolver.printPdf(
    file: DocumentFile,
    adapter: PrintDocumentAdapter,
    pdfPrintListener: PdfPrintListener,
) {
    val onLayoutResult =
        object : PrintDocumentAdapter.LayoutResultCallback() {

            override fun onLayoutFailed(error: CharSequence?) {
                pdfPrintListener.onFailure(error)
            }

            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                this@printPdf.writeToFile(file, adapter, pdfPrintListener)
            }
        }

    adapter.onLayout(null, createPrintAttributes(), null, onLayoutResult, null)
}

private fun ContentResolver.writeToFile(
    file: DocumentFile,
    adapter: PrintDocumentAdapter,
    pdfPrintListener: PdfPrintListener,
) {
    val onWriteResult =
        object : PrintDocumentAdapter.WriteResultCallback() {

            override fun onWriteFailed(error: CharSequence?) {
                pdfPrintListener.onFailure(error)
            }

            override fun onWriteFinished(pages: Array<out PageRange>?) {
                pdfPrintListener.onSuccess(file)
            }
        }

    val pages = arrayOf(PageRange.ALL_PAGES)
    val fileDescriptor = openFileDescriptor(file.uri, "rw")
    adapter.onWrite(pages, fileDescriptor, null, onWriteResult)
}

private fun createPrintAttributes(): PrintAttributes {
    return with(PrintAttributes.Builder()) {
        setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        setResolution(PrintAttributes.Resolution("Standard", "Standard", 100, 100))
        build()
    }
}

interface PdfPrintListener {

    fun onSuccess(file: DocumentFile)

    fun onFailure(message: CharSequence?)
}
