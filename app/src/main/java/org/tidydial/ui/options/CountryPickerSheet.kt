package org.tidydial.ui.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.launch
import org.tidydial.R
import org.tidydial.model.Country
import org.tidydial.ui.preview.SampleData
import org.tidydial.ui.theme.PhoneNumberTextStyles
import org.tidydial.ui.theme.TidyDialTheme

/**
 * Full-height country picker in a `ModalBottomSheet`, with search (localized name, ISO code and
 * calling code, accent-insensitive) and the detected country pinned on top.
 *
 * Picking a row animates the sheet closed, then calls [onSelect]; the caller then removes it.
 * [onDismiss] runs on back or swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    countries: List<Country>,
    selectedIso: String,
    detectedIso: String,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    TidyDialSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CountryPickerContent(
            countries = countries,
            selectedIso = selectedIso,
            detectedIso = detectedIso,
            onSelect = { country ->
                scope.launch { sheetState.hide() }.invokeOnCompletion { onSelect(country) }
            },
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

/** Search field and country list of [CountryPickerSheet]. */
@Composable
internal fun CountryPickerContent(
    countries: List<Country>,
    selectedIso: String,
    detectedIso: String,
    onSelect: (Country) -> Unit,
    modifier: Modifier = Modifier,
    searchState: TextFieldState = rememberTextFieldState(),
) {
    val search = remember(countries) { CountrySearch(countries) }
    val query by remember(searchState) { derivedStateOf { searchState.text.toString().trim() } }
    val results by remember(search) { derivedStateOf { search.filter(query) } }
    val detected = remember(countries, detectedIso) { countries.firstOrNull { it.isoCode == detectedIso } }

    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        SheetHeader(title = stringResource(R.string.country_picker_title))
        CountrySearchField(
            state = searchState,
            onSearch = { results.firstOrNull()?.let(onSelect) },
            modifier = Modifier.padding(horizontal = SheetGutter, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (query.isEmpty() && detected != null) {
                listHeader(R.string.country_picker_detected, key = "header-detected")
                item(key = "detected") {
                    CountryRow(detected, selected = detected.isoCode == selectedIso, onClick = { onSelect(detected) })
                }
                listHeader(R.string.country_picker_all, key = "header-all")
            }
            items(results, key = { it.isoCode }) { country ->
                CountryRow(
                    country = country,
                    selected = country.isoCode == selectedIso,
                    onClick = { onSelect(country) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (results.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.country_picker_no_results, query),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    )
                }
            }
            item(key = "bottom-inset") {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

private fun LazyListScope.listHeader(label: Int, key: String) {
    item(key = key) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = SheetGutter * 2, end = SheetGutter * 2, top = 16.dp, bottom = 8.dp)
                .semantics { heading() },
        )
    }
}

@Composable
private fun CountrySearchField(
    state: TextFieldState,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    TextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.country_picker_search_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_clear_search),
                    )
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Search,
        ),
        onKeyboardAction = { onSearch() },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountryRow(
    country: Country,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.padding(horizontal = SheetGutter / 2),
        leadingContent = { FlagBadge(country.flagEmoji) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = country.callingCodeLabel,
                    style = PhoneNumberTextStyles.medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected) {
                    Icon(
                        painterResource(R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(country.name)
    }
}

/** Accent- and case-insensitive country search over name, ISO code and calling code. */
internal class CountrySearch(private val countries: List<Country>) {
    private val keys: List<String> = countries.map { it.name.searchKey() }

    /**
     * Countries matching [query], best matches first: exact ISO or calling code, then names that
     * start with the query, then names with a word starting with it, then any substring. A blank
     * query returns every country in catalog order.
     */
    fun filter(query: String): List<Country> {
        val text = query.trim()
        if (text.isEmpty()) return countries
        val key = text.searchKey()
        val code = text.removePrefix("+").trim()
        val numeric = code.isNotEmpty() && code.all { it in '0'..'9' }
        return countries.indices
            .mapNotNull { index ->
                val country = countries[index]
                val rank = if (numeric) {
                    rankCallingCode(country, code)
                } else {
                    rankName(country, keys[index], key)
                }
                rank?.let { it to country }
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun rankCallingCode(country: Country, code: String): Int? {
        val callingCode = country.callingCode.toString()
        return when {
            callingCode == code -> 0
            callingCode.startsWith(code) -> 1
            else -> null
        }
    }

    private fun rankName(country: Country, name: String, key: String): Int? = when {
        country.isoCode.equals(key, ignoreCase = true) -> 0
        name.startsWith(key) -> 1
        name.split(WORD_SEPARATOR).any { it.startsWith(key) } -> 2
        name.contains(key) -> 3
        else -> null
    }

    private companion object {
        val WORD_SEPARATOR = Regex("[\\s\\-'’()]+")
        val COMBINING_MARKS = Regex("\\p{Mn}+")

        fun String.searchKey(): String =
            Normalizer.normalize(this, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase(Locale.ROOT)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CountryPickerContentPreview() {
    TidyDialTheme(dynamicColor = false) {
        CountryPickerContent(
            countries = SampleData.countries,
            selectedIso = "DE",
            detectedIso = "US",
            onSelect = {},
        )
    }
}
