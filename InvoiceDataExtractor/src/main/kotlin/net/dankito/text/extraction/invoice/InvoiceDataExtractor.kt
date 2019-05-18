package net.dankito.text.extraction.invoice

import net.dankito.text.extraction.invoice.model.Invoice


open class InvoiceDataExtractor @JvmOverloads constructor(
    protected val amountExtractor: IAmountExtractor = AmountExtractor(),
    protected val amountCategorizer: AmountCategorizer = AmountCategorizer()
) {


    open fun extractInvoiceData(text: String): Invoice? {
        return extractInvoiceData(text.split("\n"))
    }

    open fun extractInvoiceData(lines: List<String>): Invoice? {

        val percentages = amountExtractor.extractPercentages(lines)

        val potentialVatRate = amountCategorizer.findValueAddedTaxRate(percentages)

        val amounts = amountExtractor.extractAmountsOfMoney(lines)

        amountCategorizer.findTotalNetAndVatAmount(amounts)?.let { potentialAmounts ->
            return Invoice(potentialAmounts.totalAmount, potentialAmounts.netAmount, potentialAmounts.valueAddedTax, potentialVatRate)
        }

        return null
    }

}