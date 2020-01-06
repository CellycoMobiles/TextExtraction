package net.dankito.text.extraction.pdf

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import net.dankito.text.extraction.ITextExtractor
import net.dankito.text.extraction.model.ExtractedText
import net.dankito.text.extraction.model.Page
import org.slf4j.LoggerFactory
import java.io.File


class OpenPdfPdfTextExtractor: ITextExtractor {

    companion object {
        private val log = LoggerFactory.getLogger(OpenPdfPdfTextExtractor::class.java)
    }


    override val isAvailable = true

    override fun canExtractDataFromFile(file: File): Boolean {
        return "pdf" == file.extension.toLowerCase()
    }


    override fun extractText(file: File): ExtractedText {
        val reader = PdfReader(file.inputStream())

        val textExtractor = PdfTextExtractor(reader)

        val countPages = reader.numberOfPages
        val extractedText = ExtractedText(countPages)

        for (pageNum in 1..countPages) {
            extractedText.addPage(Page(textExtractor.getTextFromPage(pageNum), pageNum))

            log.debug("Extracted text of page $pageNum / $countPages")
        }

        reader.close()

        return extractedText
    }

}