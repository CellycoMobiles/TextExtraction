package net.dankito.text.extraction.invoice

import net.dankito.text.extraction.invoice.model.StringSearchResult


interface IIbanExtractor {

    fun extractIbans(text: String): List<StringSearchResult>

    fun extractIbans(lines: List<String>): List<StringSearchResult>

}