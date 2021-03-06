package net.dankito.text.extraction.info

import net.dankito.text.extraction.info.model.DateData
import net.dankito.text.extraction.info.model.DatePartsPosition
import org.slf4j.LoggerFactory
import java.util.regex.Matcher
import java.util.regex.Pattern


open class DateExtractor : IDateExtractor {

    companion object {
        const val Day = "([012][0-9]|3[01]|[1-9])"
        const val Month = "(1[012]|0?[1-9])"
        const val Year = "(19|20)?\\d\\d"
        const val Separator = "[./ -]"
        const val Boundary = "\\b"

        // = "\\b([012][0-9]|3[01]|[1-9])[- /.](1[012]|0?[1-9])[- /.](19|20)?\\d\\d\\b"
        const val DayMonthYearPatternString = "$Boundary$Day$Separator$Month$Separator$Year$Boundary"
        val DayMonthYearPattern = Pattern.compile(DayMonthYearPatternString)
        val DayMonthYearDatePartsPosition = DatePartsPosition(1, 2, 3)

        // = "\\b(1[012]|0?[1-9])[- /.]([012][0-9]|3[01]|[1-9])[- /.](19|20)?\\d\\d\\b"
        const val MonthDayYearPatternString = "$Boundary$Month$Separator$Day$Separator$Year$Boundary"
        val MonthDayYearPattern = Pattern.compile(MonthDayYearPatternString)
        val MonthDayYearDatePartsPosition = DatePartsPosition(2, 1, 3)

        // = "\\b(19|20)?\\d\\d[- /.](1[012]|0?[1-9])[- /.]([012][0-9]|3[01]|[1-9])\\b"
        const val YearMonthDayPatternString = "$Boundary$Year$Separator$Month$Separator$Day$Boundary"
        val YearMonthDayPattern = Pattern.compile(YearMonthDayPatternString)
        val YearMonthDayDatePartsPosition = DatePartsPosition(3, 2, 1)

        // may check also:
        // http://regexlib.com/DisplayPatterns.aspx?cattabindex=4&categoryId=5&AspxAutoDetectCookieSupport=1
        // https://www.regular-expressions.info/dates.html
        // https://stackoverflow.com/questions/51224/regular-expression-to-match-valid-dates
        // https://github.com/HeidelTime/heideltime


        private val log = LoggerFactory.getLogger(DateExtractor::class.java)

    }


    override fun extractDates(text: String): List<DateData> {
        return extractDates(text.split("\n"))
    }

    override fun extractDates(lines: List<String>): List<DateData> {

        val dayMonthYearDates = findDateStrings(lines, DayMonthYearPattern, DayMonthYearDatePartsPosition)

        val monthDayYearDates = findDateStrings(lines, MonthDayYearPattern, MonthDayYearDatePartsPosition)

        val yearMonthDayDates = findDateStrings(lines, YearMonthDayPattern, YearMonthDayDatePartsPosition)


        return getLargestList(dayMonthYearDates, monthDayYearDates, yearMonthDayDates)
    }

    protected open fun <T> getLargestList(vararg lists: List<T>): List<T> {
        if (lists.isNotEmpty()) {
            var largestList = lists.first()

            for (i in 1 until lists.size) {
                val list = lists[i]

                if (list.size > largestList.size) {
                    largestList = list
                }
            }

            return largestList
        }

        return listOf()
    }

    protected open fun findDateStrings(lines: List<String>, pattern: Pattern, datePartsPosition: DatePartsPosition): List<DateData> {
        val matchers = lines.associateBy( { it } , { pattern.matcher(it) } )

        val matches = matchers.filter { it.value.find() }

        val dateStrings = matches.mapValues { findDateStrings(it.value) }

        return mapStringsToDates(dateStrings, datePartsPosition)
    }

    protected open fun findDateStrings(matcher: Matcher): MutableList<String> {
        matcher.reset()
        val matches = mutableListOf<String>()

        while (matcher.find()) {
            matches.add(matcher.group())
        }

        return matches
    }

    protected open fun mapStringsToDates(dateStrings: Map<String, List<String>>, datePartsPosition: DatePartsPosition): List<DateData> {
        return dateStrings.map { mapStringsToDates(it.key, it.value, datePartsPosition) }.flatten()
    }

    protected open fun mapStringsToDates(line: String, dateStrings: List<String>, datePartsPosition: DatePartsPosition): List<DateData> {
        return dateStrings.mapNotNull { mapStringToDate(line, it, datePartsPosition) }
    }

    protected open fun mapStringToDate(line: String, dateString: String, datePartsPosition: DatePartsPosition): DateData? {
        try {
            val dateStringWithNormalizedSeparator = dateString.replace(".", " ").replace("/", " ").replace("-", " ")

            val dateParts = dateStringWithNormalizedSeparator.split(" ")

            if (dateParts.size == 3) {
                return DateData(dateParts[datePartsPosition.dayPosition - 1].toInt(),
                    dateParts[datePartsPosition.monthPosition - 1].toInt(),
                    dateParts[datePartsPosition.yearPosition - 1].toInt(),
                    dateString,
                    line)
            }
        } catch (e: Exception) {
            log.error("Could not map date string $dateString to Date", e)
        }

        return null
    }

}