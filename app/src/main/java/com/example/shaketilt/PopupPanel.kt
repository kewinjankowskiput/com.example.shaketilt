package com.example.shaketilt

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View


data class PopupButton(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val drawable: Drawable?,
    val action: () -> Unit
)

open class PopupPanel(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var panelGradient: Shader? = null
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var buttons: List<PopupButton> = emptyList()
    var contentDrawer: ((Canvas) -> Unit)? = null

    var panelAlpha = 0f
    var panelOffsetY = 0f
    var panelAnimating = false
    var panelAnimDuration = 300L
    var showingPanel = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showingPanel && panelAlpha <= 0f) return

        canvas.save()
        canvas.translate(0f, panelOffsetY)
        paint.alpha = (255 * panelAlpha).toInt()

        panelGradient?.let { paint.shader = it }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        buttons.forEach { btn ->
            btn.drawable?.let { drawable ->
                val size = btn.radius * 1.5f
                drawable.setBounds(
                    (btn.cx - size / 2).toInt(),
                    (btn.cy - size / 2).toInt(),
                    (btn.cx + size / 2).toInt(),
                    (btn.cy + size / 2).toInt()
                )
                drawable.draw(canvas)
            }
        }

        contentDrawer?.invoke(canvas)

        paint.alpha = 255
        canvas.restore()
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        event?.let { ev ->
            if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
                buttons.forEach { btn ->
                    val dx = ev.x - btn.cx
                    val dy = ev.y - btn.cy
                    if (dx * dx + dy * dy <= btn.radius * btn.radius) {
                        btn.action()
                        return true
                    }
                }
            }
        }
        return true
    }

    fun showPanel() {
        showingPanel = true
        animatePanel(true)
    }

    fun hidePanel() {
        animatePanel(false)
    }

    private fun animatePanel(show: Boolean) {
        if (panelAnimating) return
        panelAnimating = true

        val startAlpha = if (show) 0f else 1f
        val endAlpha = if (show) 1f else 0f
        val startOffset = if (show) height.toFloat() else 0f
        val endOffset = if (show) 0f else height.toFloat()

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = panelAnimDuration
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedFraction
            panelAlpha = startAlpha + fraction * (endAlpha - startAlpha)
            panelOffsetY = startOffset + fraction * (endOffset - startOffset)
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                panelAnimating = false
                if (!show) showingPanel = false
            }
        })
        animator.start()
    }
}