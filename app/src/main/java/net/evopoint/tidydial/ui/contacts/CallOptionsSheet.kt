package net.evopoint.tidydial.ui.contacts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.evopoint.tidydial.R
import net.evopoint.tidydial.model.CallOption
import net.evopoint.tidydial.ui.CallRequest
import net.evopoint.tidydial.ui.options.SheetGutter
import net.evopoint.tidydial.ui.options.SheetHeader
import net.evopoint.tidydial.ui.options.TidyDialSheet
import net.evopoint.tidydial.ui.options.rememberExpandedSheetState
import net.evopoint.tidydial.ui.options.sheetListItemColors
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.PhoneNumberTextStyles
import net.evopoint.tidydial.ui.theme.TidyDialTheme

private val LeadingSize = 40.dp

/**
 * Asks how to call a number in a `ModalBottomSheet`: the phone app, then each voice and video call
 * other apps offer for it. Picking one animates the sheet closed, then calls [onSelect] once; the
 * caller then removes it. [onDismiss] runs on back or swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallOptionsSheet(
    request: CallRequest.Choose,
    onSelect: (CallOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val scope = rememberCoroutineScope()
    // Unlike picking a country, starting a call twice is not harmless: taps while the sheet
    // closes are ignored.
    var picked by remember { mutableStateOf(false) }
    TidyDialSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CallOptionsContent(
            name = request.name,
            number = request.number,
            options = request.options,
            onSelect = { option ->
                if (!picked) {
                    picked = true
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        // Also when a touch stopped the animation, but not once the sheet is gone.
                        if (scope.isActive) onSelect(option)
                    }
                }
            },
        )
    }
}

/** Starts a call (or chat) [intent]; false when no app took it (none installed, or it refused TidyDial). */
internal fun Context.startCall(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/** Header and options of [CallOptionsSheet]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CallOptionsContent(
    name: String,
    number: String,
    options: List<CallOption>,
    onSelect: (CallOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.call_sheet_title, name),
            subtitle = number,
            subtitleStyle = PhoneNumberTextStyles.medium,
        )
        Column(
            modifier = Modifier.padding(start = SheetGutter, end = SheetGutter, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            options.forEachIndexed { index, option ->
                SegmentedListItem(
                    onClick = { onSelect(option) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
                    colors = sheetListItemColors(),
                    leadingContent = { CallOptionIcon(option) },
                    supportingContent = { Text(option.summary()) },
                ) {
                    Text(option.title())
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun CallOption.title(): String = when (this) {
    is CallOption.Phone -> stringResource(R.string.call_option_phone)
    is CallOption.App -> label
}

@Composable
private fun CallOption.summary(): String = when (this) {
    is CallOption.Phone -> stringResource(R.string.call_option_phone_summary)
    is CallOption.App -> stringResource(if (isVideo) R.string.call_option_video else R.string.call_option_voice)
}

/** The app's own icon, or a call / video symbol if it cannot be read. */
@Composable
private fun CallOptionIcon(option: CallOption) {
    val appIcon = (option as? CallOption.App)?.let { rememberAppIcon(it.packageName) }
    if (appIcon != null) {
        Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(LeadingSize))
        return
    }
    val video = option is CallOption.App && option.isVideo
    SymbolBadge(if (video) R.drawable.ic_videocam else R.drawable.ic_call)
}

@Composable
private fun SymbolBadge(@DrawableRes icon: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(LeadingSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/** Read on the main thread: a handful of icons the package manager already has cached. */
@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { LeadingSize.roundToPx() }
    return remember(packageName, sizePx) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}

@Preview(showBackground = true, widthDp = 411)
@Composable
private fun CallOptionsContentPreview() {
    TidyDialTheme(dynamicColor = false) {
        val choice = SampleData.callChoice
        CallOptionsContent(name = choice.name, number = choice.number, options = choice.options, onSelect = {})
    }
}
