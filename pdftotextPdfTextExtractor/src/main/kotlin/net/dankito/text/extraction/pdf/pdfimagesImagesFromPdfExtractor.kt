package net.dankito.text.extraction.pdf

import net.dankito.text.extraction.model.ExtractedImages
import net.dankito.utils.process.CommandConfig
import net.dankito.utils.process.CommandExecutor
import net.dankito.utils.process.ICommandExecutor
import java.io.File


open class pdfimagesImagesFromPdfExtractor(
    protected val commandExecutor: ICommandExecutor = CommandExecutor()
) : IImagesFromPdfExtractor {


    override fun extractImages(pdfFile: File): ExtractedImages {
        val tmpDir = createTempDir("ExtractImagesFrom${pdfFile.nameWithoutExtension}")
        tmpDir.deleteOnExit()

        val commandArgs = listOf(
            "pdfimages",
            "-p", // add page number to file name
            "-tiff", // change the default output format to TIFF
            pdfFile.absolutePath,
            File(tmpDir, pdfFile.nameWithoutExtension).absolutePath
        )

        val result = commandExecutor.executeCommand(CommandConfig(commandArgs))

        if (result.successful == false) {
            return ExtractedImages(listOf(), Exception(result.errors))
        }

        return ExtractedImages(tmpDir.listFiles().toList())
    }

}