package org.tidydial.model

data class ContactGroup(
    val contactId: Long,
    val displayName: String,
    val entries: List<PhoneEntry>,
) {
    val initials: String = initialsOf(displayName)

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** Up to two letters taken from the first two words; "#" for number-only names, "?" when empty. */
        fun initialsOf(name: String): String {
            val words = name.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            if (words.isEmpty()) return "?"
            val letters = words
                .map { it.codePointAt(0) }
                .filter { Character.isLetter(it) }
                .take(2)
            if (letters.isEmpty()) return "#"
            return buildString { letters.forEach { appendCodePoint(it) } }.uppercase()
        }
    }
}

/** Groups entries by contact, keeping the order in which contacts first appear. */
fun List<PhoneEntry>.groupByContact(): List<ContactGroup> {
    val groups = LinkedHashMap<Long, MutableList<PhoneEntry>>()
    for (entry in this) {
        groups.getOrPut(entry.contactId) { mutableListOf() }.add(entry)
    }
    return groups.map { (contactId, entries) ->
        ContactGroup(contactId, entries.first().displayName, entries)
    }
}

/**
 * One number as the user sees it. The same number synced from several accounts (for example
 * Google and Google Meet) is one item holding every data row, so it is shown and ticked once.
 */
data class NumberItem(val entries: List<PhoneEntry>) {
    init {
        require(entries.isNotEmpty())
    }

    /** All rows share the number, type and status, so the first one stands for the item. */
    val entry: PhoneEntry get() = entries.first()

    val dataIds: List<Long> = entries.map { it.dataId }

    /** Account names in first-seen order, without repeats. */
    val accountLabels: List<String> = entries.map { it.accountLabel }.distinct()

    /** Every data row of this item is in [selection]. */
    fun isSelectedIn(selection: Set<Long>): Boolean = dataIds.all { it in selection }
}

/** Collapses rows with the same number, type and status into one [NumberItem], keeping order. */
fun List<PhoneEntry>.toNumberItems(): List<NumberItem> =
    groupBy { listOf(it.originalNumber, it.formattedNumber, it.typeLabel, it.isValid, it.isModified) }
        .values
        .map(::NumberItem)
