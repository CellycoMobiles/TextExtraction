package net.dankito.text.extraction

import net.dankito.text.extraction.model.ExtractionResult
import java.io.File


interface ITextExtractor {

    companion object {
        const val TextExtractionQualityForUnsupportedFileType = -1
    }


    val isAvailable: Boolean

    val supportedFileTypes: List<String>

    fun isFileTypeSupported(file: File): Boolean {
        return supportedFileTypes.contains(file.extension.toLowerCase())
    }

    fun canExtractDataFromFile(file: File): Boolean {
        return isFileTypeSupported(file)
    }

    fun getTextExtractionQualityForFileType(file: File): Int

    fun extractText(file: File): ExtractionResult

}