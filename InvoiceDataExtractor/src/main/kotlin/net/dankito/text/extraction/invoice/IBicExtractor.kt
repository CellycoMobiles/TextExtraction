package net.dankito.text.extraction.invoice

import net.dankito.text.extraction.invoice.model.StringSearchResult


interface IBicExtractor {

    fun extractBics(text: String): List<StringSearchResult>

    fun extractBics(lines: List<String>): List<StringSearchResult>

}