package net.dankito.text.extraction.app.javafx.window.main.controls

import net.dankito.text.extraction.ITextExtractor
import net.dankito.text.extraction.pdf.pdfToTextPdfTextExtractor
import net.dankito.utils.IThreadPool


class pdfToTextPdfTextExtractorExtractTextTab(threadPool: IThreadPool) : ExtractTextTabBase(threadPool) {

    override fun createTextExtractor(): ITextExtractor {
        return pdfToTextPdfTextExtractor()
    }

}