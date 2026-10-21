package net.evopoint.tidydial.ui.contacts

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/** A button shown under a [ListMessage]. */
data class MessageAction(val label: String, val onClick: () -> Unit, val primary: Boolean = false)

/**
 * Centered, friendly message for empty, no-match and error states: an icon in an Expressive
 * shape, a headline, a body and an optional action. Scrolls when the font is very large.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListMessage(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: MessageAction? = null,
    isError: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container: Color = if (isError) scheme.errorContainer else scheme.secondaryContainer
    val content: Color = if (isError) scheme.onErrorContainer else scheme.onSecondaryContainer
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 400.dp),
            )
            if (action != null) {
                Spacer(Modifier.height(24.dp))
                if (action.primary) {
                    Button(onClick = action.onClick) { Text(action.label) }
                } else {
                    FilledTonalButton(onClick = action.onClick) { Text(action.label) }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 480)
@Composable
private fun ListMessagePreview() {
    TidyDialTheme(dynamicColor = false) {
        ListMessage(
            iconRes = R.drawable.ic_error,
            title = stringResource(R.string.error_load_contacts),
            body = stringResource(R.string.list_error_body),
            action = MessageAction(stringResource(R.string.list_error_retry), {}, primary = true),
            isError = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
