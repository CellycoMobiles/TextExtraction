package net.dankito.text.extraction.invoice

import net.dankito.text.extraction.invoice.model.AmountOfMoney
import net.dankito.text.extraction.invoice.model.Invoice
import java.lang.Exception
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
    }


    open fun extractInvoiceData(text: String): Invoice {
        return extractInvoiceData(text.split("\n"))
    }

    open fun extractInvoiceData(lines: List<String>): Invoice {
        val pattern = createCurrencySymbolPattern(currencySymbolPatternString)

        val matchers = lines.associateBy( { it } , { pattern.matcher(it) } )

        val matchersWithCurrencySymbols = matchers.filter { it.value.find() }

        val amounts = matchersWithCurrencySymbols.mapNotNull {
            extractAmountsOfMoney(it.key, it.value)
        }.flatMap { it }.toSet()

        return Invoice(AmountOfMoney(0.0, "", "")) // TODO
    }

    protected open fun extractAmountsOfMoney(line: String, matcherWithCurrencySymbol: Matcher): List<AmountOfMoney> {

        matcherWithCurrencySymbol.reset()
        var substringStart = 0

        val amounts = mutableListOf<AmountOfMoney>()

        while (matcherWithCurrencySymbol.find()) {
            extractAmountsOfMoney(matcherWithCurrencySymbol, line.substring(substringStart))?.let {
                amounts.add(it)
            }

            substringStart = matcherWithCurrencySymbol.end()
        }

        return amounts
    }

    private fun extractAmountsOfMoney(matcherWithCurrencySymbol: Matcher, line: String): AmountOfMoney? { // TODO: fix signature
        val currencySymbol = matcherWithCurrencySymbol.group()


        val decimalNumberBeforeCurrencySymbolMatcher = createPatternForDecimalNumberBeforeCurrencySymbol(
            decimalNumberPatternString, currencySymbol
        ).matcher(line.substring(0, matcherWithCurrencySymbol.end()))

        if (decimalNumberBeforeCurrencySymbolMatcher.find()) {
            return extractAmountOfMoney(decimalNumberBeforeCurrencySymbolMatcher, currencySymbol)
        }


        val decimalNumberAfterCurrencySymbolMatcher = createPatternForDecimalNumberAfterCurrencySymbol(
            decimalNumberPatternString, currencySymbol
        ).matcher(line.substring(matcherWithCurrencySymbol.start()))

        if (decimalNumberAfterCurrencySymbolMatcher.find()) {
            return extractAmountOfMoney(decimalNumberAfterCurrencySymbolMatcher, currencySymbol)
        }

        return null
    }

    protected open fun extractAmountOfMoney(decimalNumberMatcher: Matcher, currencySymbol: String): AmountOfMoney {
        val amountWithCurrency = decimalNumberMatcher.group()
        val amount = extractAmount(amountWithCurrency, currencySymbol)

        return AmountOfMoney(amount, currencySymbol, amountWithCurrency)
    }

    protected open fun extractAmount(amountWithCurrency: String, currencySymbol: String): Double {
        val amountString = amountWithCurrency.replace(currencySymbol, "").trim()

        try {
            return UserNumberFormat.parse(amountString).toDouble()
        } catch (ignored: Exception) { }

        try {
            return EnglishNumberFormat.parse(amountString).toDouble()
        } catch (ignored: Exception) { }

        try {
            return GermanNumberFormat.parse(amountString).toDouble()
        } catch (ignored: Exception) { }

        return amountString.toDouble()
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

}