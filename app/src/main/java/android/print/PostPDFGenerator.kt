package android.print

import android.content.ContentResolver
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.documentfile.provider.DocumentFile
import com.philkes.notallyx.presentation.nameWithoutExtension

/**
 * This class needs to be in android.print package to access the package private methods of
 * [PrintDocumentAdapter]
 */
object PostPDFGenerator {

    fun create(file: DocumentFile, content: String, context: Context, onResult: OnResult) {
        val webView = WebView(context)
        webView.loadDataWithBaseURL(null, content, "text/html", "utf-8", null)
        webView.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(view: WebView?, url: String?) {
                    val adapter = webView.createPrintDocumentAdapter(file.nameWithoutExtension!!)
                    print(context.contentResolver, file, adapter, onResult)
                }
            }
    }

    private fun print(
        contentResolver: ContentResolver,
        file: DocumentFile,
        adapter: PrintDocumentAdapter,
        onResult: OnResult,
    ) {
        val onLayoutResult =
            object : PrintDocumentAdapter.LayoutResultCallback() {

                override fun onLayoutFailed(error: CharSequence?) {
                    onResult.onFailure(error)
                }

                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    writeToFile(contentResolver, file, adapter, onResult)
                }
            }

        adapter.onLayout(null, getPrintAttributes(), null, onLayoutResult, null)
    }

    private fun writeToFile(
        contentResolver: ContentResolver,
        file: DocumentFile,
        adapter: PrintDocumentAdapter,
        onResult: OnResult,
    ) {
        val onWriteResult =
            object : PrintDocumentAdapter.WriteResultCallback() {

                override fun onWriteFailed(error: CharSequence?) {
                    onResult.onFailure(error)
                }

                override fun onWriteFinished(pages: Array<out PageRange>?) {
                    onResult.onSuccess(file)
                }
            }

        val pages = arrayOf(PageRange.ALL_PAGES)
        val fileDescriptor = contentResolver.openFileDescriptor(file.uri, "rw")
        adapter.onWrite(pages, fileDescriptor, null, onWriteResult)
    }

    private fun getPrintAttributes(): PrintAttributes {
        val builder = PrintAttributes.Builder()
        builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        builder.setResolution(PrintAttributes.Resolution("Standard", "Standard", 100, 100))
        return builder.build()
    }

    interface OnResult {

        fun onSuccess(file: DocumentFile)

        fun onFailure(message: CharSequence?)
    }
}
