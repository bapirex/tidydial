package net.evopoint.tidydial.ui.options

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.evopoint.tidydial.R
import net.evopoint.tidydial.model.AccountInfo
import net.evopoint.tidydial.model.Country
import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import net.evopoint.tidydial.ui.BackupUi
import net.evopoint.tidydial.ui.backup.BackupErrorText
import net.evopoint.tidydial.ui.permission.PermissionAction
import net.evopoint.tidydial.ui.permission.PermissionsUi
import net.evopoint.tidydial.ui.permission.UnusedAppRestrictionsSource
import net.evopoint.tidydial.ui.permission.rememberPermissionSettings
import net.evopoint.tidydial.ui.permission.rememberSystemUnusedAppRestrictions
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.PhoneNumberTextStyles
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * Format options in a `ModalBottomSheet`.
 *
 * Edits a local draft of [config] (remembered from the initial config, so later state emissions do
 * not reset it, and saved across configuration changes). "Apply" calls [onApply] with the draft
 * when it differs from [config]; "Cancel", back and swipe discard it. [onDismiss] runs once the
 * sheet has closed. The country row opens [CountryPickerSheet] on top.
 *
 * The Backups section is not part of the draft: its changes go through the system picker and
 * take effect right away, which its description says, so Cancel never seems to undo them.
 * [onRestoreBackup] is expected to close the sheet, which discards unsaved format edits like
 * Cancel does. The Permissions section ([rememberPermissionSettings], with
 * [unusedAppRestrictions] telling whether Android may remove permissions of unused apps) also
 * applies right away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatOptionsSheet(
    config: FormatConfig,
    detectedCountryIso: String,
    countries: List<Country>,
    accounts: List<AccountInfo>,
    exampleNumber: (String, NumberFormatStyle) -> String?,
    backup: BackupUi,
    onBackupEnabledChange: (Boolean) -> Unit,
    onChooseBackupFolder: () -> Unit,
    onRestoreBackup: () -> Unit,
    onApply: (FormatConfig) -> Unit,
    onDismiss: () -> Unit,
    unusedAppRestrictions: UnusedAppRestrictionsSource = rememberSystemUnusedAppRestrictions(),
) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = FormatConfigSaver) { mutableStateOf(config) }
    var pickingCountry by rememberSaveable { mutableStateOf(false) }
    // Outside the sheet's window, so an answer still arrives after the activity was recreated.
    val permissions = rememberPermissionSettings(unusedAppRestrictions)

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    TidyDialSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        FormatOptionsContent(
            draft = draft,
            onDraftChange = { draft = it },
            country = countries.firstOrNull { it.isoCode == draft.countryIso },
            detectedCountryIso = detectedCountryIso,
            accounts = accounts,
            exampleNumber = exampleNumber,
            backup = backup,
            onBackupEnabledChange = onBackupEnabledChange,
            onChooseBackupFolder = onChooseBackupFolder,
            onRestoreBackup = onRestoreBackup,
            permissions = permissions.ui,
            onPermissionAction = permissions.onAction,
            onPickCountry = { pickingCountry = true },
            onApply = {
                if (draft != config) onApply(draft)
                close()
            },
            onCancel = ::close,
        )
    }

    if (pickingCountry) {
        CountryPickerSheet(
            countries = countries,
            selectedIso = draft.countryIso,
            detectedIso = detectedCountryIso,
            onSelect = {
                draft = draft.copy(countryIso = it.isoCode)
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
    }
}

/**
 * The scrollable body of [FormatOptionsSheet] with its sticky footer. Stateless: every edit goes
 * through [onDraftChange]. [country] is [FormatConfig.countryIso] resolved against the catalog.
 */
@Composable
internal fun FormatOptionsContent(
    draft: FormatConfig,
    onDraftChange: (FormatConfig) -> Unit,
    country: Country?,
    detectedCountryIso: String,
    accounts: List<AccountInfo>,
    exampleNumber: (String, NumberFormatStyle) -> String?,
    backup: BackupUi,
    onBackupEnabledChange: (Boolean) -> Unit,
    onChooseBackupFolder: () -> Unit,
    onRestoreBackup: () -> Unit,
    permissions: PermissionsUi,
    onPermissionAction: (PermissionAction) -> Unit,
    onPickCountry: () -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(
                title = stringResource(R.string.format_options),
                subtitle = stringResource(R.string.options_subtitle),
            )
            CountrySection(
                countryIso = draft.countryIso,
                country = country,
                isDetected = draft.countryIso == detectedCountryIso,
                onClick = onPickCountry,
            )
            FormatStyleSection(
                selected = draft.formatStyle,
                countryIso = draft.countryIso,
                exampleNumber = exampleNumber,
                onSelect = { onDraftChange(draft.copy(formatStyle = it)) },
            )
            ExtensionSection(
                selected = draft.extensionDelimiter,
                onSelect = { onDraftChange(draft.copy(extensionDelimiter = it)) },
            )
            ShortNumbersSection(
                checked = draft.preserveShortNumbers,
                onCheckedChange = { onDraftChange(draft.copy(preserveShortNumbers = it)) },
            )
            AccountsSection(
                accounts = accounts,
                excluded = draft.excludedAccountTypes,
                onExcludedChange = { type, excluded ->
                    val types = draft.excludedAccountTypes
                    onDraftChange(
                        draft.copy(excludedAccountTypes = if (excluded) types + type else types - type),
                    )
                },
            )
            BackupsSection(
                backup = backup,
                onEnabledChange = onBackupEnabledChange,
                onChooseFolder = onChooseBackupFolder,
                onRestore = onRestoreBackup,
            )
            PermissionsSection(permissions = permissions, onAction = onPermissionAction)
        }
        if (scrollState.canScrollForward) HorizontalDivider()
        OptionsFooter(onApply = onApply, onCancel = onCancel)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountrySection(
    countryIso: String,
    country: Country?,
    isDetected: Boolean,
    onClick: () -> Unit,
) {
    SheetSection(
        title = stringResource(R.string.options_section_country),
        description = stringResource(R.string.options_country_summary),
    ) {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            colors = sheetListItemColors(),
            leadingContent = { FlagBadge(Country.flagEmojiOf(countryIso)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (isDetected) R.string.options_country_auto_detected else R.string.options_country_change,
                    ),
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (country != null) {
                        Text(
                            text = country.callingCodeLabel,
                            style = MaterialTheme.typography.titleMedium.merge(PhoneNumberTextStyles.medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null)
                }
            },
        ) {
            Text(country?.name ?: countryIso)
        }
    }
}

private val NumberFormatStyle.titleRes: Int
    @StringRes get() = when (this) {
        NumberFormatStyle.INTERNATIONAL -> R.string.options_style_international
        NumberFormatStyle.NATIONAL -> R.string.options_style_national
        NumberFormatStyle.E164 -> R.string.options_style_e164
        NumberFormatStyle.RFC3966 -> R.string.options_style_rfc3966
    }

private val NumberFormatStyle.summaryRes: Int
    @StringRes get() = when (this) {
        NumberFormatStyle.INTERNATIONAL -> R.string.options_style_international_summary
        NumberFormatStyle.NATIONAL -> R.string.options_style_national_summary
        NumberFormatStyle.E164 -> R.string.options_style_e164_summary
        NumberFormatStyle.RFC3966 -> R.string.options_style_rfc3966_summary
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FormatStyleSection(
    selected: NumberFormatStyle,
    countryIso: String,
    exampleNumber: (String, NumberFormatStyle) -> String?,
    onSelect: (NumberFormatStyle) -> Unit,
) {
    val styles = NumberFormatStyle.entries
    SheetSection(
        title = stringResource(R.string.options_section_format),
        modifier = Modifier.selectableGroup(),
    ) {
        styles.forEachIndexed { index, style ->
            val example = remember(countryIso, style) { exampleNumber(countryIso, style) }
            SegmentedListItem(
                selected = style == selected,
                onClick = { onSelect(style) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = styles.size),
                colors = sheetListItemColors(),
                leadingContent = { RadioButton(selected = style == selected, onClick = null) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(style.summaryRes))
                        Text(
                            text = example ?: stringResource(R.string.options_example_unavailable),
                            style = if (example != null) PhoneNumberTextStyles.large else MaterialTheme.typography.bodyMedium,
                            color = if (example != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            ) {
                Text(stringResource(style.titleRes))
            }
        }
    }
}

private class DelimiterOption(
    val delimiter: ExtensionDelimiter,
    @StringRes val label: Int,
    @StringRes val summary: Int,
    val symbol: String?,
)

private val DelimiterOptions = listOf(
    DelimiterOption(
        ExtensionDelimiter.SEMICOLON,
        R.string.options_extension_wait,
        R.string.options_extension_wait_summary,
        ";",
    ),
    DelimiterOption(
        ExtensionDelimiter.COMMA,
        R.string.options_extension_pause,
        R.string.options_extension_pause_summary,
        ",",
    ),
    DelimiterOption(
        ExtensionDelimiter.KEEP_ORIGINAL,
        R.string.options_extension_keep,
        R.string.options_extension_keep_summary,
        null,
    ),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExtensionSection(
    selected: ExtensionDelimiter,
    onSelect: (ExtensionDelimiter) -> Unit,
) {
    SheetSection(title = stringResource(R.string.options_section_extension)) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            DelimiterOptions.forEachIndexed { index, option ->
                val checked = option.delimiter == selected
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { onSelect(option.delimiter) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        DelimiterOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(stringResource(option.label), maxLines = 1)
                    if (option.symbol != null) {
                        Text(
                            text = option.symbol,
                            style = PhoneNumberTextStyles.large,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
        AnimatedContent(targetState = selected, label = "delimiterSummary") { delimiter ->
            Text(
                text = stringResource(DelimiterOptions.first { it.delimiter == delimiter }.summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SheetGutter, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortNumbersSection(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SheetSection(title = stringResource(R.string.options_section_short_numbers)) {
        SegmentedListItem(
            checked = checked,
            onCheckedChange = onCheckedChange,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            colors = sheetListItemColors(),
            modifier = Modifier.semantics { role = Role.Switch },
            supportingContent = { Text(stringResource(R.string.options_short_numbers_summary)) },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        ) {
            Text(stringResource(R.string.options_short_numbers_title))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountsSection(
    accounts: List<AccountInfo>,
    excluded: Set<String>,
    onExcludedChange: (type: String, excluded: Boolean) -> Unit,
) {
    SheetSection(
        title = stringResource(R.string.options_section_accounts),
        description = stringResource(R.string.options_accounts_summary),
    ) {
        if (accounts.isEmpty()) {
            Text(
                text = stringResource(R.string.options_accounts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SheetGutter, vertical = 8.dp),
            )
        }
        accounts.forEachIndexed { index, account ->
            val isExcluded = account.type in excluded
            val state = stringResource(
                if (isExcluded) R.string.options_account_skipped else R.string.options_account_included,
            )
            SegmentedListItem(
                checked = isExcluded,
                onCheckedChange = { onExcludedChange(account.type, it) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = accounts.size),
                colors = sheetListItemColors(),
                modifier = Modifier.semantics {
                    role = Role.Switch
                    stateDescription = state
                },
                supportingContent = {
                    Text(
                        pluralStringResource(R.plurals.number_count, account.count, account.count),
                    )
                },
                trailingContent = { Switch(checked = isExcluded, onCheckedChange = null) },
            ) {
                Text(account.label)
            }
        }
    }
}

/**
 * Backups before each update, the folder they go to, and restoring from a backup file. The
 * folder row only shows once a folder was chosen. [BackupUi.error] shows under the rows, since a
 * Snackbar would be hidden behind the sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupsSection(
    backup: BackupUi,
    onEnabledChange: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onRestore: () -> Unit,
) {
    val folderName = backup.folderName
    val count = if (folderName != null) 3 else 2
    val changeFolderLabel = stringResource(R.string.options_backups_change_folder_action)
    SheetSection(
        title = stringResource(R.string.options_section_backups),
        description = stringResource(R.string.options_backups_summary),
    ) {
        SegmentedListItem(
            checked = backup.enabled,
            onCheckedChange = onEnabledChange,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            colors = sheetListItemColors(),
            modifier = Modifier.semantics { role = Role.Switch },
            supportingContent = if (folderName == null) {
                { Text(stringResource(R.string.options_backups_folder_none)) }
            } else {
                null
            },
            trailingContent = { Switch(checked = backup.enabled, onCheckedChange = null) },
        ) {
            Text(stringResource(R.string.options_backups_toggle))
        }
        if (folderName != null) {
            SegmentedListItem(
                onClick = onChooseFolder,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
                colors = sheetListItemColors(),
                // "Double-tap to change backup folder" rather than a bare "Double-tap to activate".
                modifier = Modifier.semantics {
                    onClick(label = changeFolderLabel) {
                        onChooseFolder()
                        true
                    }
                },
                leadingContent = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                supportingContent = {
                    if (backup.folderAvailable) {
                        Text(stringResource(R.string.options_backups_change_folder))
                    } else {
                        Text(
                            text = stringResource(R.string.options_backups_folder_unavailable),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                trailingContent = { Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null) },
            ) {
                Text(folderName)
            }
        }
        SegmentedListItem(
            onClick = onRestore,
            shapes = ListItemDefaults.segmentedShapes(index = count - 1, count = count),
            colors = sheetListItemColors(),
            leadingContent = {
                Icon(painterResource(R.drawable.ic_settings_backup_restore), contentDescription = null)
            },
            supportingContent = { Text(stringResource(R.string.options_backups_restore_summary)) },
        ) {
            Text(stringResource(R.string.options_backups_restore))
        }
        backup.error?.let {
            BackupErrorText(it, Modifier.padding(horizontal = SheetGutter, vertical = 8.dp))
        }
    }
}

@Composable
private fun OptionsFooter(onApply: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = SheetGutter * 1.5f, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.titleMedium)
        }
        Button(
            onClick = onApply,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.MediumContentPadding,
        ) {
            Text(stringResource(R.string.options_apply), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Saves a [FormatConfig] draft in a Bundle-friendly list. */
internal val FormatConfigSaver = listSaver<FormatConfig, Any>(
    save = {
        listOf(
            it.countryIso,
            it.formatStyle.name,
            it.extensionDelimiter.name,
            it.preserveShortNumbers,
            ArrayList(it.excludedAccountTypes),
        )
    },
    restore = {
        @Suppress("UNCHECKED_CAST")
        FormatConfig(
            countryIso = it[0] as String,
            formatStyle = NumberFormatStyle.valueOf(it[1] as String),
            extensionDelimiter = ExtensionDelimiter.valueOf(it[2] as String),
            preserveShortNumbers = it[3] as Boolean,
            excludedAccountTypes = (it[4] as List<String>).toSet(),
        )
    },
)

private val PreviewExamples = mapOf(
    NumberFormatStyle.INTERNATIONAL to "+1 201-555-0123",
    NumberFormatStyle.NATIONAL to "(201) 555-0123",
    NumberFormatStyle.E164 to "+12015550123",
    NumberFormatStyle.RFC3966 to "tel:+1-201-555-0123",
)

/** Sample examples for previews and screenshots, matching libphonenumber's US mobile example. */
internal fun previewExampleNumber(region: String, style: NumberFormatStyle): String? =
    if (region == "US") PreviewExamples[style] else null

@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun FormatOptionsContentPreview() {
    TidyDialTheme(dynamicColor = false) {
        FormatOptionsContent(
            draft = SampleData.config,
            onDraftChange = {},
            country = SampleData.countries.first { it.isoCode == "US" },
            detectedCountryIso = "US",
            accounts = SampleData.accounts,
            exampleNumber = ::previewExampleNumber,
            backup = SampleData.backupUi,
            onBackupEnabledChange = {},
            onChooseBackupFolder = {},
            onRestoreBackup = {},
            permissions = SampleData.permissionsUi,
            onPermissionAction = {},
            onPickCountry = {},
            onApply = {},
            onCancel = {},
        )
    }
}
