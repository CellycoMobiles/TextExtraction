package net.dankito.text.extraction.invoice

import net.dankito.text.extraction.invoice.model.AmountOfMoney
import net.dankito.text.extraction.invoice.model.Invoice
import org.slf4j.LoggerFactory
import java.text.NumberFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


open class InvoiceDataExtractor(protected val currencySymbolPatternString: String = "\\p{Sc}|EUR",
                                protected val decimalNumberPatternString: String = "\\d+([\\,\\.]\\d{1,2})?"
) {

    companion object {
        val UserNumberFormat = NumberFormat.getNumberInstance()

        // most languages have the English number format with a dot as decimal separator
        val EnglishNumberFormat = NumberFormat.getNumberInstance(Locale.ENGLISH)

        // German uses comma as decimal separator
        val GermanNumberFormat = NumberFormat.getNumberInstance(Locale.GERMAN)


        private val log = LoggerFactory.getLogger(InvoiceDataExtractor::class.java)
    }


    open fun extractInvoiceData(text: String): Invoice? {
        return extractInvoiceData(text.split("\n"))
    }

    open fun extractInvoiceData(lines: List<String>): Invoice? {
        val pattern = createCurrencySymbolPattern(currencySymbolPatternString)

        val matchers = lines.associateBy( { it } , { pattern.matcher(it) } )

        val matchersWithCurrencySymbols = matchers.filter { it.value.find() }

        val amounts = matchersWithCurrencySymbols.mapNotNull {
            extractAmountsOfMoney(it.value, it.key)
        }.flatten().toSet()

        val vatRateCandidates = extractValueAddedTaxRate(lines)

        findTotalNetAndVatAmount(amounts)?.let { potentialAmounts ->
            return Invoice(potentialAmounts.first, potentialAmounts.second, potentialAmounts.third)
        }

        return null
    }

    protected open fun extractAmountsOfMoney(matcherWithCurrencySymbol: Matcher, line: String): List<AmountOfMoney> {

        matcherWithCurrencySymbol.reset()
        var lineSubstringStart = 0

        val amounts = mutableListOf<AmountOfMoney>()

        while (matcherWithCurrencySymbol.find()) {
            extractAmountOfMoney(matcherWithCurrencySymbol, line, lineSubstringStart)?.let {
                amounts.add(it)
            }

            lineSubstringStart = matcherWithCurrencySymbol.end()
        }

        return amounts
    }

    protected open fun extractAmountOfMoney(matcherWithCurrencySymbol: Matcher, line: String, lineSubstringStart: Int): AmountOfMoney? {
        val relevantLinePart = line.substring(lineSubstringStart)
        val currencySymbol = matcherWithCurrencySymbol.group()


        val decimalNumberBeforeCurrencySymbolMatcher = createPatternForDecimalNumberBeforeCurrencySymbol(
            decimalNumberPatternString, currencySymbol
        ).matcher(relevantLinePart.substring(0, matcherWithCurrencySymbol.end()))

        if (decimalNumberBeforeCurrencySymbolMatcher.find()) {
            return extractAmountOfMoney(decimalNumberBeforeCurrencySymbolMatcher, line, currencySymbol)
        }


        val decimalNumberAfterCurrencySymbolMatcher = createPatternForDecimalNumberAfterCurrencySymbol(
            decimalNumberPatternString, currencySymbol
        ).matcher(relevantLinePart.substring(matcherWithCurrencySymbol.start()))

        if (decimalNumberAfterCurrencySymbolMatcher.find()) {
            return extractAmountOfMoney(decimalNumberAfterCurrencySymbolMatcher, line, currencySymbol)
        }

        return null
    }

    protected open fun extractAmountOfMoney(decimalNumberMatcher: Matcher, line: String, currencySymbol: String): AmountOfMoney {
        val amountWithCurrency = decimalNumberMatcher.group()
        val amount = extractAmount(amountWithCurrency, currencySymbol)

        return AmountOfMoney(amount, currencySymbol, amountWithCurrency, line)
    }

    protected open fun extractAmount(amountWithCurrency: String, currencySymbol: String): Double {
        val amountString = amountWithCurrency.replace(currencySymbol, "").trim()

        return extractNumber(amountString)?.toDouble() ?: amountString.toDouble()
    }

    protected open fun extractNumber(numberString: String): Number? {
        try {
            return UserNumberFormat.parse(numberString)
        } catch (ignored: Exception) { }

        try {
            return EnglishNumberFormat.parse(numberString)
        } catch (ignored: Exception) { }

        try {
            return GermanNumberFormat.parse(numberString)
        } catch (ignored: Exception) { }

        return null
    }


    protected open fun extractValueAddedTaxRate(lines: List<String>): List<AmountOfMoney> {
        val percentageSymbol = getPercentageSymbol()
        val vatRatePattern = createPatternForValueAddedTaxRate(decimalNumberPatternString, percentageSymbol)

        val matchers = lines.associateBy( { it } , { vatRatePattern.matcher(it) } )
        val matchersWithPercentage = matchers.filter { it.value.find() }

        return matchersWithPercentage.mapNotNull { mapPercentageToAmountOfMoney(it.value, it.key, percentageSymbol) }
    }

    protected open fun mapPercentageToAmountOfMoney(matcherWithPercentage: Matcher, line: String,
                                                    percentageSymbol: String): AmountOfMoney? {

        val percentageString = matcherWithPercentage.group()
        val percentageNumberAsString = percentageString.replace(percentageSymbol, "").trim()

        try {
            val percentageNumberAsFloat = extractNumber(percentageNumberAsString)?.toFloat() ?: percentageNumberAsString.toFloat()

            if (percentageNumberAsFloat in 0f..100f) { // is really a percentage
                return AmountOfMoney(percentageNumberAsFloat.toDouble(), percentageSymbol, percentageString, line)
            }
        } catch (e: Exception) { log.warn("Could not map $percentageNumberAsString to Float", e) }

        return null
    }


    protected open fun findTotalNetAndVatAmount(amounts: Collection<AmountOfMoney>)
            : Triple<AmountOfMoney, AmountOfMoney?, AmountOfMoney?>? {

        if (amounts.isNotEmpty()) {
            val amountsSorted = amounts.sortedByDescending { it.amount }

            for (totalIndex in 0 until amountsSorted.size) {
                val potentialTotal = amountsSorted[totalIndex]

                for (netIndex in (totalIndex + 1) until amountsSorted.size - 1) {
                    val potentialNet = amountsSorted[netIndex]

                    for (vatIndex in (netIndex + 1) until amountsSorted.size) {
                        val potentialVat = amountsSorted[vatIndex]

                        if (potentialTotal.amount == (potentialNet.amount + potentialVat.amount)) {
                            return Triple(potentialTotal, potentialNet, potentialVat)
                        }
                    }
                }
            }

            return Triple(amountsSorted.first(), null, null)
        }

        return null
    }


    protected open fun createCurrencySymbolPattern(currencySymbolPatternString: String): Pattern {
        return Pattern.compile(currencySymbolPatternString, Pattern.CASE_INSENSITIVE)
    }

    protected open fun createPatternForDecimalNumberBeforeCurrencySymbol(decimalNumberPatternString: String,
                                                                         currencySymbol: String): Pattern {

        return Pattern.compile(decimalNumberPatternString + "\\s*" + currencySymbol, Pattern.CASE_INSENSITIVE)
    }

    protected open fun createPatternForDecimalNumberAfterCurrencySymbol(decimalNumberPatternString: String,
                                                                         currencySymbol: String): Pattern {

        return Pattern.compile(currencySymbol + "\\s*" + decimalNumberPatternString, Pattern.CASE_INSENSITIVE)
    }

    protected open fun getPercentageSymbol(): String {
        return "%"
    }

    protected open fun createPatternForValueAddedTaxRate(decimalNumberPatternString: String,
                                                         percentageSymbol: String): Pattern {

        return Pattern.compile(decimalNumberPatternString + "\\s*" + percentageSymbol, Pattern.CASE_INSENSITIVE)
    }

}