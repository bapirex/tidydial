package net.evopoint.tidydial.ui.permission

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.ui.PermissionStatus
import net.evopoint.tidydial.ui.theme.TidyDialTheme

private val MaxContentWidth = 600.dp
private val MaxTwoPaneWidth = 1040.dp
private val TwoPaneMinWidth = 600.dp
private val ScreenPadding = 24.dp

// Below this height (or with a very large font) pinned actions would crowd out the content.
private val PinnedActionsMinHeight = 560.dp
private const val PinnedActionsMaxFontScale = 1.5f

/**
 * Full-screen explainer shown before the system permission dialogs: what TidyDial does, a
 * before/after example, what each permission is for (contacts required, phone calls optional)
 * and two privacy promises.
 *
 * For [PermissionStatus.Denied] (and [PermissionStatus.Unknown]) the button calls [onRequest];
 * for [PermissionStatus.PermanentlyDenied] the system dialog no longer appears, so it explains
 * that access must be granted in system settings and calls [onOpenSettings].
 *
 * Portrait shows one scrolling column, capped at [MaxContentWidth], with the actions pinned at the
 * bottom when the window is tall enough; in short windows or with very large fonts the actions
 * scroll with the content instead, so they can never be cut off. Wide, short windows (landscape
 * phones) split into two panes that scroll when the content does not fit, with the actions under
 * the headline.
 */
@Composable
fun PermissionScreen(
    status: PermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entrance = rememberEntrance()
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            val twoPane = maxWidth >= TwoPaneMinWidth && maxWidth > maxHeight
            val pinActions = maxHeight >= PinnedActionsMinHeight &&
                LocalDensity.current.fontScale < PinnedActionsMaxFontScale
            val actions = @Composable {
                PermissionActions(
                    status = status,
                    onRequest = onRequest,
                    onOpenSettings = onOpenSettings,
                )
            }
            if (twoPane) {
                TwoPaneLayout(minHeight = maxHeight, entrance = entrance, actions = actions)
            } else if (pinActions) {
                PinnedActionsLayout(entrance = entrance, actions = actions)
            } else {
                SinglePaneLayout(minHeight = maxHeight, entrance = entrance, actions = actions)
            }
        }
    }
}

@Composable
private fun PinnedActionsLayout(
    entrance: Entrance,
    actions: @Composable () -> Unit,
) {
    // The content scrolls and the actions stay pinned below it, so the button is always in reach.
    // A divider shows while more content is below.
    val scrollState = rememberScrollState()
    val divider = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))
                PermissionHero(logoScale = entrance.logoScale, logoAlpha = entrance.logoAlpha)
                Spacer(Modifier.height(28.dp))
                Column(
                    modifier = Modifier.entranceContent(entrance),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    ExampleCard()
                    PermissionUses()
                    PrivacyPromises()
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (scrollState.canScrollForward) {
                        drawLine(divider, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                    }
                }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 16.dp)
                    .entranceContent(entrance),
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun SinglePaneLayout(
    minHeight: Dp,
    entrance: Entrance,
    actions: @Composable () -> Unit,
) {
    // The actions sit at the bottom when everything fits, and scroll with the rest when not.
    ScrollingPane(
        minHeight = minHeight,
        modifier = Modifier.fillMaxSize(),
        maxContentWidth = MaxContentWidth,
    ) {
        Spacer(Modifier.height(16.dp))
        PermissionHero(logoScale = entrance.logoScale, logoAlpha = entrance.logoAlpha)
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.entranceContent(entrance),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ExampleCard()
            PermissionUses()
            PrivacyPromises()
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(28.dp))
        Box(Modifier.entranceContent(entrance)) { actions() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TwoPaneLayout(
    minHeight: Dp,
    entrance: Entrance,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(max = MaxTwoPaneWidth)
            .fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScrollingPane(
            minHeight = minHeight,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            PermissionHero(
                logoScale = entrance.logoScale,
                logoAlpha = entrance.logoAlpha,
                compact = true,
            )
            Box(Modifier.entranceContent(entrance)) { actions() }
        }
        ScrollingPane(
            minHeight = minHeight,
            modifier = Modifier
                .weight(1f)
                .entranceContent(entrance),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            ExampleCard()
            PermissionUses()
            PrivacyPromises()
        }
    }
}

/**
 * A vertically scrolling column that is at least [minHeight] tall, so weights and vertical
 * arrangements still apply when the content fits. Pads for the navigation bar inside the scroll.
 */
@Composable
private fun ScrollingPane(
    minHeight: Dp,
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = Dp.Unspecified,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** Animated values for the one-time entrance: the logo pops in, the content rises and fades in. */
private class Entrance(
    val logoScale: () -> Float,
    val logoAlpha: () -> Float,
    val contentAlpha: () -> Float,
    val contentOffset: () -> Float,
)

@Composable
private fun rememberEntrance(): Entrance {
    // Saved, so the entrance plays once and not again after rotation.
    var played by rememberSaveable { mutableStateOf(false) }
    val state = remember { MutableTransitionState(played) }
    LaunchedEffect(Unit) {
        state.targetState = true
        played = true
    }
    val transition = rememberTransition(state, label = "entrance")
    val motion = MaterialTheme.motionScheme
    val logoScale = transition.animateFloat({ motion.slowSpatialSpec() }, label = "logoScale") {
        if (it) 1f else 0.6f
    }
    val logoAlpha = transition.animateFloat({ motion.defaultEffectsSpec() }, label = "logoAlpha") {
        if (it) 1f else 0f
    }
    val contentAlpha = transition.animateFloat({ motion.slowEffectsSpec() }, label = "contentAlpha") {
        if (it) 1f else 0f
    }
    val offsetPx = with(LocalDensity.current) { 24.dp.toPx() }
    val contentOffset = transition.animateFloat({ motion.slowSpatialSpec() }, label = "contentOffset") {
        if (it) 0f else offsetPx
    }
    return remember(transition) {
        Entrance(
            logoScale = { logoScale.value },
            logoAlpha = { logoAlpha.value },
            contentAlpha = { contentAlpha.value },
            contentOffset = { contentOffset.value },
        )
    }
}

private fun Modifier.entranceContent(entrance: Entrance): Modifier = graphicsLayer {
    alpha = entrance.contentAlpha()
    translationY = entrance.contentOffset()
}

@Preview(name = "Light", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun PermissionScreenPreview() {
    TidyDialTheme(dynamicColor = false) {
        PermissionScreen(status = PermissionStatus.Denied, onRequest = {}, onOpenSettings = {})
    }
}

@Preview(name = "Dark", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun PermissionScreenDarkPreview() {
    TidyDialTheme(darkTheme = true, dynamicColor = false) {
        PermissionScreen(status = PermissionStatus.Denied, onRequest = {}, onOpenSettings = {})
    }
}

@Preview(name = "Permanently denied", showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun PermissionScreenPermanentlyDeniedPreview() {
    TidyDialTheme(dynamicColor = false) {
        PermissionScreen(
            status = PermissionStatus.PermanentlyDenied,
            onRequest = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Font scale 1.5", showBackground = true, fontScale = 1.5f, device = "spec:width=411dp,height=891dp")
@Composable
private fun PermissionScreenLargeFontPreview() {
    TidyDialTheme(dynamicColor = false) {
        PermissionScreen(status = PermissionStatus.Denied, onRequest = {}, onOpenSettings = {})
    }
}

@Preview(name = "Landscape", showBackground = true, device = "spec:width=891dp,height=411dp,orientation=landscape")
@Composable
private fun PermissionScreenLandscapePreview() {
    TidyDialTheme(dynamicColor = false) {
        PermissionScreen(status = PermissionStatus.Denied, onRequest = {}, onOpenSettings = {})
    }
}
