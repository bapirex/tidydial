package net.evopoint.tidydial.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.evopoint.tidydial.BuildConfig
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.components.AppLogo
import net.evopoint.tidydial.ui.components.AppLogoDefaults
import net.evopoint.tidydial.ui.options.SheetGutter
import net.evopoint.tidydial.ui.options.SheetSection
import net.evopoint.tidydial.ui.options.TidyDialSheet
import net.evopoint.tidydial.ui.options.sheetListItemColors
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * About sheet in a `ModalBottomSheet`: logo, version, description, privacy promises, source code
 * link, license and the open-source libraries TidyDial is built with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    TidyDialSheet(onDismissRequest = onDismiss) {
        AboutContent(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            onOpenSourceCode = { context.openSourceCode() },
        )
    }
}

private fun Context.openSourceCode() {
    val intent = Intent(Intent.ACTION_VIEW, getString(R.string.about_source_code_url).toUri())
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

/** Scrollable body of [AboutSheet]. */
@Composable
internal fun AboutContent(
    versionName: String,
    versionCode: Int,
    onOpenSourceCode: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        AboutHeader(versionName = versionName, versionCode = versionCode)
        PrivacyCard(modifier = Modifier.padding(horizontal = SheetGutter))
        LinksSection(onOpenSourceCode = onOpenSourceCode, initiallyExpanded = initiallyExpanded)
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun AboutHeader(versionName: String, versionCode: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SheetGutter * 2)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo(
            size = 88.dp,
            shape = AppLogoDefaults.expressiveShape,
            contentDescription = stringResource(R.string.about_logo_description),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.about_version, versionName, versionCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private class Promise(@DrawableRes val icon: Int, @StringRes val text: Int)

private val Promises = listOf(
    Promise(R.drawable.ic_cloud_off, R.string.about_privacy_offline),
    Promise(R.drawable.ic_block, R.string.about_privacy_no_tracking),
    Promise(R.drawable.ic_lock, R.string.privacy_on_device),
)

@Composable
private fun PrivacyCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.about_privacy_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            Promises.forEach { PromiseRow(it) }
        }
    }
}

@Composable
private fun PromiseRow(promise: Promise) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(promise.icon), contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Text(text = stringResource(promise.text), style = MaterialTheme.typography.bodyLarge)
    }
}

private class Library(@StringRes val name: Int, @StringRes val license: Int)

/** Same components, grouped the same way, as NOTICE and the README credits. */
private val Libraries = listOf(
    Library(R.string.about_library_compose, R.string.about_license_apache),
    Library(R.string.about_library_material, R.string.about_license_apache),
    Library(R.string.about_library_material_symbols, R.string.about_license_apache),
    Library(R.string.about_library_libphonenumber, R.string.about_license_apache),
    Library(R.string.about_library_kotlin, R.string.about_license_apache),
    Library(R.string.about_library_okio, R.string.about_license_apache),
    Library(R.string.about_library_annotations, R.string.about_license_apache),
    Library(R.string.about_library_protobuf, R.string.about_license_bsd3),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinksSection(onOpenSourceCode: () -> Unit, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevron",
    )
    val expandedState = stringResource(if (expanded) R.string.about_expanded else R.string.about_collapsed)
    val count = 3

    SheetSection(title = stringResource(R.string.about_section_project)) {
        SegmentedListItem(
            onClick = onOpenSourceCode,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            colors = sheetListItemColors(),
            leadingContent = { Icon(painterResource(R.drawable.ic_code), contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.about_source_code_host)) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = stringResource(R.string.about_open_in_browser),
                )
            },
        ) {
            Text(stringResource(R.string.about_source_code))
        }
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
            colors = sheetListItemColors(),
            leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.about_license_name)) },
        ) {
            Text(stringResource(R.string.about_license))
        }
        SegmentedListItem(
            onClick = { expanded = !expanded },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = count),
            colors = sheetListItemColors(),
            modifier = Modifier.semantics { stateDescription = expandedState },
            leadingContent = { Icon(painterResource(R.drawable.ic_public), contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.about_libraries_summary)) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                )
            },
        ) {
            Text(stringResource(R.string.about_libraries))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            LibrariesCard(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun LibrariesCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Libraries.forEach { library ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(library.name),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(library.license),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun AboutContentPreview() {
    TidyDialTheme(dynamicColor = false) {
        AboutContent(versionName = "1.0.0", versionCode = 1, onOpenSourceCode = {}, initiallyExpanded = true)
    }
}
