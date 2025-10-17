package com.example.shaketilt

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

class LevelPanel(
    private val context: Context,
    private val redraw: () -> Unit,
    private val panelColor: Int = "#009664".toColorInt(),
    private val titleText: String = "Select Level"
) {

    private var showing = false
    private var animating = false
    private var panelAlpha = 0f
    private var panelOffsetY = 0f
    var animDuration: Long = 400

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var btnRadius = 0f
    private var level1Cx = 0f
    private var level2Cx = 0f
    private var level3Cx = 0f
    private var quitCx = 0f
    private var btnCy = 0f
    private var vPadding = 0f

    interface Listener {
        fun onLevelSelected(level: Int)
        fun onQuit()
    }

    var listener: Listener? = null

    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (!showing && panelAlpha <= 0f) return

        canvas.save()
        canvas.translate(0f, panelOffsetY)
        paint.alpha = (255 * panelAlpha).toInt()

        val hPadding = width * 0.15f
        vPadding = height * 0.1f

        paint.color = panelColor
        canvas.drawRect(hPadding, vPadding, width - hPadding, height - vPadding, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = Math.min(80f, width / 10f)
        canvas.drawText(titleText, width / 2f, vPadding + 150f, paint)

        btnRadius = Math.min(width, height) * 0.12f
        btnCy = height / 2f

        val spacing = btnRadius * 3f
        level1Cx = width / 2f - spacing
        level2Cx = width / 2f
        level3Cx = width / 2f + spacing

        fun drawLevelButton(cx: Float, cy: Float, text: String, color: Int) {
            paint.color = color
            canvas.drawCircle(cx, cy, btnRadius, paint)
            paint.color = Color.WHITE
            paint.textSize = btnRadius * 1.2f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(text, cx, cy + btnRadius * 0.35f, paint)
        }

        drawLevelButton(level1Cx, btnCy, "1", "#4CAF50".toColorInt())
        drawLevelButton(level2Cx, btnCy, "2", "#FF9800".toColorInt())
        drawLevelButton(level3Cx, btnCy, "3", "#2196F3".toColorInt())

        val quitColor = "#F44336".toColorInt()
        val quitRadius = btnRadius * 0.6f
        quitCx = width - hPadding - btnRadius
        val quitCy = vPadding + btnRadius

        paint.color = quitColor
        canvas.drawCircle(quitCx, quitCy, quitRadius, paint)

        val quitIcon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_back)
        quitIcon?.let {
            val iconSize = quitRadius * 1.2f
            it.setBounds(
                (quitCx - iconSize / 2f).toInt(),
                (quitCy - iconSize / 2f).toInt(),
                (quitCx + iconSize / 2f).toInt(),
                (quitCy + iconSize / 2f).toInt()
            )
            it.draw(canvas)
        }

        paint.alpha = 255
        canvas.restore()
    }

    fun handleTouch(event: MotionEvent): Boolean {
        if (!showing) return false
        if (event.action != MotionEvent.ACTION_DOWN) return false

        fun isInsideCircle(cx: Float, cy: Float, radius: Float): Boolean {
            val dx = event.x - cx
            val dy = event.y - cy
            return dx * dx + dy * dy <= radius * radius
        }

        return when {
            isInsideCircle(level1Cx, btnCy, btnRadius) -> { listener?.onLevelSelected(1); true }
            isInsideCircle(level2Cx, btnCy, btnRadius) -> { listener?.onLevelSelected(2); true }
            isInsideCircle(level3Cx, btnCy, btnRadius) -> { listener?.onLevelSelected(3); true }
            isInsideCircle(quitCx, vPadding + btnRadius, btnRadius) -> { listener?.onQuit(); true }
            else -> false
        }
    }

    fun show() { showing = true; animate(true) }
    fun hide() { animate(false) }
    fun isShowing(): Boolean = showing

    private fun animate(show: Boolean) {
        if (animating) return
        animating = true

        val startAlpha = if (show) 0f else 1f
        val endAlpha = if (show) 1f else 0f
        val startOffset = if (show) 1000f else 0f
        val endOffset = if (show) 0f else 1000f

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = animDuration
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedFraction
            panelAlpha = startAlpha + fraction * (endAlpha - startAlpha)
            panelOffsetY = startOffset + fraction * (endOffset - startOffset)
            redraw()
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                animating = false
                if (!show) showing = false
            }
        })
        animator.start()
    }
}