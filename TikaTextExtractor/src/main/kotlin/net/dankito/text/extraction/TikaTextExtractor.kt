package net.dankito.text.extraction

import net.dankito.text.extraction.ITextExtractor.Companion.TextExtractionQualityForUnsupportedFileType
import net.dankito.text.extraction.image.model.OcrLanguage
import net.dankito.text.extraction.image.model.OcrOutputType
import net.dankito.text.extraction.image.model.TesseractHelper
import net.dankito.text.extraction.model.ExtractionResult
import net.dankito.text.extraction.model.Page
import net.dankito.text.extraction.model.PdfContentExtractorStrategy
import net.dankito.text.extraction.model.TikaSettings
import net.dankito.text.extraction.pdf.ISearchablePdfTextExtractor
import net.dankito.utils.os.OsHelper
import org.apache.tika.config.ServiceLoader
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaTypeRegistry
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.DefaultParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.Parser
import org.apache.tika.parser.external.ExternalParser
import org.apache.tika.parser.ocr.TesseractOCRConfig
import org.apache.tika.parser.ocr.TesseractOCRParser
import org.apache.tika.parser.pdf.PDFParser
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.sax.WriteOutContentHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.StringWriter


open class TikaTextExtractor @JvmOverloads constructor(
	protected val settings: TikaSettings,
	protected val tesseractHelper: TesseractHelper = TesseractHelper(),
	protected val osHelper: OsHelper = OsHelper()
): TextExtractorBase(), ISearchablePdfTextExtractor {
	
	companion object {
		private val log = LoggerFactory.getLogger(TikaTextExtractor::class.java)
	}


	constructor() : this(TikaSettings(PdfContentExtractorStrategy.OcrAndText,
		listOf(OcrLanguage.English, OcrLanguage.German), OcrOutputType.Text
	))


	override val name = "Tika"

	override val isAvailable: Boolean
		get() = osHelper.isRunningOnAndroid == false

	override val supportedFileTypes = listOf("pdf", "png", "jpg", "tif", "tiff", "odt", "docx", "ods", "xlsx", "csv") // TODO: set all supported file types

	override fun getTextExtractionQualityForFileType(file: File): Int {
		if ("pdf" == file.extension.toLowerCase()) {
			return 60
		}
		else if (TesseractHelper.SupportedFileTypes.contains(file.extension.toLowerCase())) {
			return 60 // try Tesseract4CommandlineImageTextExtractor - if available - first
		}
		else if (isFileTypeSupported(file)) {
			return 95
		}

		return TextExtractionQualityForUnsupportedFileType
	}


	protected lateinit var parser: Parser
	protected lateinit var context: ParseContext


	init {
		initTika()
	}


	override fun extractTextForSupportedFormat(file: File): ExtractionResult {
		val extractedTextWriter = StringWriter()
		extractText(file, extractedTextWriter)

		val extractedText = ExtractionResult()

		// TODO: try to get Hocr and ToHtmlContentHandler working to get aware of single pages
		val extractionResult = extractedTextWriter.toString()
		if (extractionResult.isNotBlank()) {
			extractedText.addPage(Page(extractionResult))
		}

		return extractedText
	}

	protected open fun extractText(file: File, extractedTextWriter: StringWriter) {
		val metadata = Metadata()
		metadata[Metadata.RESOURCE_NAME_KEY] = file.name

		val handler = WriteOutContentHandler(extractedTextWriter) // may add a limit in count of chars as second parameter

		FileInputStream(file).use { stream ->
			try {
				parser.parse(stream, BodyContentHandler(handler), metadata, context)
			} catch (e: Exception) {
				log.error("Could not extract content of file $file", e)
			}
		}
	}


	private fun initTika() {
		this.context = ParseContext()

		var pdfContentExtractorStrategy = settings.pdfContentExtractorStrategy
		val parserClassesToExclude = mutableListOf<Class<out Parser>>()

		if (settings.pdfContentExtractorStrategy.isOcrEnabled == false) {
			parserClassesToExclude.add(TesseractOCRParser::class.java)
		}
		else {
			if (ExternalParser.check("tesseract") == false) {
				// TODO: notify user that Tesseract is not found
				log.warn("Cannot enable OCR as Tesseract is not found")
				pdfContentExtractorStrategy = PdfContentExtractorStrategy.NoOcr
				parserClassesToExclude.add(TesseractOCRParser::class.java)
			}
			else {
				parserClassesToExclude.add(PDFParser::class.java)

				initTesseractOCRConfig(context)
			}
		}

		val defaultParser = DefaultParser(MediaTypeRegistry.getDefaultRegistry(), ServiceLoader(), parserClassesToExclude)
		val pdfParser = PDFParser()
		pdfParser.setOcrStrategy(getPdfParserOptionName(pdfContentExtractorStrategy))
		parser = AutoDetectParser(defaultParser, pdfParser)

		context.set(Parser::class.java, parser)
	}

	protected open fun initTesseractOCRConfig(context: ParseContext) {
		log.debug("Configuring Tesseract:\n" +
					"Tesseract path = '${settings.tesseractPath}'\ntessdata directory = '${settings.tessdataDirectory}'\n" +
					"OCR languages = '${settings.ocrLanguages}'\nOCR output type = '${settings.ocrOutputType}'")

		val config = TesseractOCRConfig()

		settings.tesseractPath?.let { tesseractPath ->
			config.tesseractPath = tesseractPath.absolutePath
		}

		settings.tessdataDirectory?.let { tessdataDirectory ->
			config.tessdataPath = tessdataDirectory.absolutePath
		}

		config.language = tesseractHelper.getTesseractLanguageString(settings.ocrLanguages)

		config.outputType = getTesseractOcrOutputType(settings.ocrOutputType)

		context.set(TesseractOCRConfig::class.java, config)
	}

	protected open fun getPdfParserOptionName(pdfContentExtractorStrategy: PdfContentExtractorStrategy): String {
		return when (pdfContentExtractorStrategy) {
			PdfContentExtractorStrategy.NoOcr -> "no_ocr"
			PdfContentExtractorStrategy.OcrOnly -> "ocr_only"
			PdfContentExtractorStrategy.OcrAndText -> "ocr_and_text"
		}
	}

	protected open fun getTesseractOcrOutputType(outputType: OcrOutputType): TesseractOCRConfig.OUTPUT_TYPE {
		return when (outputType) {
			OcrOutputType.Text -> TesseractOCRConfig.OUTPUT_TYPE.TXT
			OcrOutputType.Hocr -> TesseractOCRConfig.OUTPUT_TYPE.HOCR
		}
	}

}