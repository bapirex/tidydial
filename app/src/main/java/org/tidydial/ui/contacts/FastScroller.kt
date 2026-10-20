package org.tidydial.ui.contacts

import android.icu.text.AlphabeticIndex
import android.icu.util.ULocale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.tidydial.R
import java.util.Locale
import kotlin.math.roundToInt

/** One letter on the fast-scroll rail and the list item it jumps to. */
@Immutable
data class IndexSection(val label: String, val itemIndex: Int)

/**
 * Locale-aware letter buckets ("A", "Ж", "あ"), the scheme the system Contacts app uses.
 * Names that start with a digit, symbol or an unindexed script share the "#" bucket.
 */
class ContactIndexer(locale: Locale = Locale.getDefault()) {

    private val index = AlphabeticIndex<Any>(ULocale.forLocale(locale))
        .addLabels(Locale.ENGLISH)
        .buildImmutableIndex()

    fun labelOf(name: String): String {
        val bucket = index.getBucket(index.getBucketIndex(name.trim()))
        return if (bucket.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL) bucket.label else OTHER_LABEL
    }

    /**
     * Sections in list order, starting at [firstItemIndex]. A label that shows up again further
     * down (the provider's collation can differ from ICU's) keeps its first position.
     */
    fun sections(names: List<String>, firstItemIndex: Int = 0): List<IndexSection> {
        val seen = HashSet<String>()
        val result = ArrayList<IndexSection>()
        names.forEachIndexed { i, name ->
            val label = labelOf(name)
            if (seen.add(label)) result += IndexSection(label, firstItemIndex + i)
        }
        return result
    }

    companion object {
        const val OTHER_LABEL = "#"
    }
}

/** Width the list reserves at its end edge while the rail is shown. */
val FastScrollRailWidth = 32.dp

private val SlotHeight = 20.dp
private val BubbleSize = 64.dp
private val BubbleGap = 8.dp
private val RailPadding = 8.dp

/**
 * Vertical letter index that jumps [listState] to a section. Press or drag along the rail;
 * a bubble shows the letter under the finger. For TalkBack the rail is one adjustable control:
 * swipe up or down to move a section, or pick any letter from the actions menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FastScrollRail(
    sections: List<IndexSection>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) return
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    var dragging by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(-1) }
    var touchY by remember { mutableFloatStateOf(0f) }

    val currentIndex by remember(sections, listState) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            sections.indexOfLast { it.itemIndex <= first }.coerceAtLeast(0)
        }
    }
    val highlighted = if (dragging && activeIndex >= 0) activeIndex else currentIndex

    fun jumpTo(index: Int) {
        val section = sections[index.coerceIn(sections.indices)]
        scope.launch { listState.scrollToItem(section.itemIndex) }
    }

    val railDescription = stringResource(R.string.list_fast_scroll)
    val sectionDescription = stringResource(R.string.list_fast_scroll_section, sections[currentIndex].label)
    val resources = LocalResources.current
    val jumpActions = remember(sections, listState, resources) {
        sections.mapIndexed { i, section ->
            CustomAccessibilityAction(resources.getString(R.string.list_fast_scroll_jump, section.label)) {
                jumpTo(i)
                true
            }
        }
    }
    val railColor by animateColorAsState(
        targetValue = if (dragging) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        animationSpec = motion.fastEffectsSpec(),
        label = "railColor",
    )

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val maxSlots = (maxHeight / SlotHeight).toInt().coerceAtLeast(1)
        // Every section stays reachable by dragging; only the labels drawn are thinned out.
        val step = (sections.size + maxSlots - 1) / maxSlots
        val shown = sections.indices.filter { it % step == 0 }

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(
                modifier = Modifier
                    .width(FastScrollRailWidth - 8.dp)
                    .clip(CircleShape)
                    .background(railColor)
                    .padding(vertical = RailPadding)
                    .semantics {
                        contentDescription = railDescription
                        stateDescription = sectionDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = currentIndex.toFloat(),
                            range = 0f..(sections.size - 1).toFloat(),
                            steps = (sections.size - 2).coerceAtLeast(0),
                        )
                        setProgress { target ->
                            jumpTo(target.roundToInt())
                            true
                        }
                        customActions = jumpActions
                    }
                    .pointerInput(sections) {
                        fun update(y: Float) {
                            touchY = y
                            val fraction = (y / size.height).coerceIn(0f, 0.9999f)
                            val index = (fraction * sections.size).toInt()
                            if (index != activeIndex) {
                                activeIndex = index
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                jumpTo(index)
                            }
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            dragging = true
                            update(down.position.y)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()
                                if (change.pressed) update(change.position.y)
                            } while (event.changes.any { it.pressed })
                            dragging = false
                            activeIndex = -1
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                shown.forEach { index ->
                    RailLabel(
                        label = sections[index].label,
                        highlighted = index == highlighted ||
                            (step > 1 && highlighted in index until index + step),
                    )
                }
            }
            // Drawn outside the rail's bounds, to its start, following the finger.
            AnimatedVisibility(
                visible = dragging && activeIndex >= 0,
                enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
                // Zero-sized in layout so the rail never moves; placed to the rail's start,
                // centred on the finger. Reading touchY here only re-runs placement.
                modifier = Modifier.layout { measurable, _ ->
                    val bubble = measurable.measure(Constraints())
                    layout(0, 0) {
                        bubble.place(
                            x = -(bubble.width + BubbleGap.roundToPx()),
                            y = (RailPadding.toPx() + touchY - bubble.height / 2f).roundToInt(),
                        )
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(BubbleSize)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = sections.getOrNull(activeIndex)?.label.orEmpty(),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = scheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** A drawn letter only; the rail carries the accessibility semantics for all of them. */
@Composable
private fun RailLabel(label: String, highlighted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(
        targetValue = if (highlighted) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "railLabel",
    )
    Text(
        text = label,
        modifier = Modifier
            .height(SlotHeight)
            .clearAndSetSemantics {},
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}
