package net.evopoint.tidydial.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.evopoint.tidydial.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Renders the real adaptive launcher icon the way launchers and stores show it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconScreenshotTest {

    private val icon: AdaptiveIconDrawable
        get() {
            val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
            val drawable = ResourcesCompat.getDrawable(resources, R.mipmap.ic_launcher, null)
            return (drawable as AdaptiveIconDrawable).apply { setBounds(0, 0, SIZE, SIZE) }
        }

    /** Full-bleed square, as used for the store listing (stores apply their own mask). */
    @Test
    fun fullBleed() {
        render { canvas ->
            val icon = icon
            icon.background.draw(canvas)
            icon.foreground.draw(canvas)
        }.captureRoboImage(filePath = "src/test/screenshots/icon_full_bleed.png")
    }

    /** Circle mask, the most common launcher shape. */
    @Test
    fun circle() {
        render { canvas ->
            val icon = icon
            canvas.clipPath(circlePath())
            icon.background.draw(canvas)
            icon.foreground.draw(canvas)
        }.captureRoboImage(filePath = "src/test/screenshots/icon_circle.png")
    }

    /** Android 13+ themed icon: monochrome layer tinted on a tonal background. */
    @Test
    fun monochrome() {
        render { canvas ->
            val monochrome = checkNotNull(icon.monochrome).mutate()
            canvas.clipPath(circlePath())
            canvas.drawColor(THEMED_BACKGROUND)
            monochrome.setTint(THEMED_FOREGROUND)
            monochrome.draw(canvas)
        }.captureRoboImage(filePath = "src/test/screenshots/icon_monochrome.png")
    }

    private fun render(draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    private fun circlePath() = Path().apply {
        addCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, Path.Direction.CW)
    }

    private companion object {
        const val SIZE = 512
        val THEMED_BACKGROUND = Color.parseColor("#1F2A3D")
        val THEMED_FOREGROUND = Color.parseColor("#D6E3FF")
    }
}
