package org.tidydial.ui.contacts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material Symbols Rounded "check" (Apache-2.0), used for selected chips and avatars. */
internal val CheckIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Check",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).addPath(
        pathData = addPathNodes(
            "M382,606L721,267Q733,255 749,255Q765,255 777,267Q789,279 789,295.5Q789,312 777,324" +
                "L410,692Q398,704 382,704Q366,704 354,692L182,520Q170,508 170.5,491.5Q171,475 183,463" +
                "Q195,451 211.5,451Q228,451 240,463L382,606Z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
