package net.evopoint.tidydial.ui.contacts

import android.icu.text.ListFormatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.model.NumberItem
import net.evopoint.tidydial.model.PhoneEntry
import net.evopoint.tidydial.model.toNumberItems
import net.evopoint.tidydial.ui.isSelectable
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.PhoneNumberTextStyles
import net.evopoint.tidydial.ui.theme.TidyDialTheme

private val RowShape = RoundedCornerShape(16.dp)

/**
 * One number of a contact ([item] merges the same number stored in several accounts). Selectable
 * numbers (valid and changing) are a single merged checkbox row; invalid numbers are
 * informational. Numbers that are already formatted get a quiet one-line row with a check.
 * Every row ends with a call button, a separate control next to (never inside) the toggle.
 *
 * [showAccount] shows the account badges on up-to-date rows; it is only needed when the contact's
 * numbers come from more than one account. Rows that change or are invalid always show them.
 */
@Composable
fun NumberRow(
    item: NumberItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
    showAccount: Boolean = true,
) {
    val entry = item.entry
    val description = rowDescription(item)
    if (entry.isValid && !entry.isModified) {
        TidyNumberRow(item, description, showAccount, onCall, modifier)
        return
    }
    val selectable = entry.isSelectable
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val tint by animateColorAsState(
        targetValue = if (selectable && selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "rowTint",
    )
    val interaction = if (selectable) {
        Modifier
            .toggleable(value = selected, role = Role.Checkbox) { checked ->
                haptics.performHapticFeedback(
                    if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onToggle()
            }
            .clearAndSetSemantics { contentDescription = description }
    } else {
        Modifier.clearAndSetSemantics { contentDescription = description }
    }
    Row(
        modifier = modifier
            .clip(RowShape)
            .background(tint),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The toggle and its TalkBack sentence stop before the call button, so both stay reachable.
        Row(
            modifier = Modifier
                .weight(1f)
                .then(interaction)
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NumberLabels(entry.typeLabel, item.accountLabels)
                if (!entry.isValid) {
                    Text(entry.originalNumber, style = PhoneNumberTextStyles.large)
                    StatusLine(
                        iconRes = R.drawable.ic_error,
                        text = stringResource(R.string.list_number_invalid),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    NumberChange(entry)
                }
            }
            if (selectable) {
                // The row itself is the toggle; the checkbox only shows the state.
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
        CallButton(item, onCall)
    }
}

/**
 * A number that is already formatted: number, label and a quiet check, on one line when they
 * fit. The number is never cut off; the label and account badges move below it together, so
 * the accounts of one number are never split across lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TidyNumberRow(
    item: NumberItem,
    description: String,
    showAccount: Boolean,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = description }
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.entry.formattedNumber,
                    style = PhoneNumberTextStyles.large,
                )
                // One FlowRow item, so the whole group wraps as a unit; it only breaks internally
                // when it is wider than a full line on its own.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.entry.typeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (showAccount) item.accountLabels.forEach { AccountBadge(it) }
                }
            }
            Icon(
                imageVector = CheckIcon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        CallButton(item, onCall)
    }
}

/**
 * Starts a call to [item]'s number. Its own TalkBack stop, after the row; [IconButton] keeps the
 * 48dp touch target.
 */
@Composable
private fun CallButton(item: NumberItem, onCall: () -> Unit) {
    val entry = item.entry
    val number = if (entry.isValid) entry.formattedNumber else entry.originalNumber
    IconButton(onClick = onCall) {
        Icon(
            painter = painterResource(R.drawable.ic_call),
            contentDescription = stringResource(R.string.list_number_call, number),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rowDescription(item: NumberItem): String {
    val entry = item.entry
    val accounts = joinLabels(item.accountLabels)
    return when {
        !entry.isValid -> stringResource(
            R.string.list_number_invalid_description,
            entry.typeLabel,
            accounts,
            entry.originalNumber,
        )

        entry.isModified -> stringResource(
            R.string.list_number_will_become,
            entry.typeLabel,
            accounts,
            entry.originalNumber,
            entry.formattedNumber,
        )

        else -> stringResource(
            R.string.list_number_formatted_description,
            entry.typeLabel,
            accounts,
            entry.formattedNumber,
        )
    }
}

/** "Google", "Google and Google Meet", "A, B and C" in the user's language. */
@Composable
private fun joinLabels(labels: List<String>): String {
    if (labels.size <= 1) return labels.firstOrNull().orEmpty()
    val locale = LocalConfiguration.current.locales[0]
    return remember(labels, locale) { ListFormatter.getInstance(locale).format(labels) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberLabels(typeLabel: String, accountLabels: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        accountLabels.forEach { AccountBadge(it) }
    }
}

/** Small tonal pill naming the account a number is stored in. */
@Composable
private fun AccountBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** "original -> formatted"; wraps onto two lines when the font is large or the screen narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberChange(entry: PhoneEntry) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.originalNumber,
            style = PhoneNumberTextStyles.medium.copy(textDecoration = TextDecoration.LineThrough),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = entry.formattedNumber,
                style = PhoneNumberTextStyles.large.copy(
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusLine(iconRes: Int, text: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun NumberRowPreview() {
    TidyDialTheme(dynamicColor = false) {
        Column(Modifier.padding(8.dp)) {
            SampleData.entries.take(6).toNumberItems().forEach { item ->
                NumberRow(
                    item = item,
                    selected = item.isSelectedIn(SampleData.defaultSelection),
                    onToggle = {},
                    onCall = {},
                )
            }
        }
    }
}
