package com.example.shaketilt

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

class WinPanel(
    private val context: Context,
    private val redraw: () -> Unit,
    private val panelColor: Int = "#009664".toColorInt(),
    private val titleText: String = "You Won!"
) {

    private var showingWin = false
    private var animating = false
    private var panelAlpha = 0f
    private var panelOffsetY = 0f
    var animDuration: Long = 400

    private var btnRadius = 0f
    private var restartCx = 0f
    private var leaderboardCx = 0f
    private var quitCx = 0f
    private var facebookCx = 0f
    private var btnCy = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    interface Listener {
        fun onRestart()
        fun onShowLeaderboard()
        fun onQuitToMenu()
        fun onFacebookShare() // add listener for FB share
    }

    var listener: Listener? = null
    var timeText: String? = null

    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (!showingWin && panelAlpha <= 0f) return

        canvas.save()
        canvas.translate(0f, panelOffsetY)
        paint.alpha = (255 * panelAlpha).toInt()

        val hPadding = Math.min(240f, width * 0.25f)
        val vPadding = Math.min(60f, height * 0.05f)

        paint.color = panelColor
        canvas.drawRect(hPadding, vPadding, width - hPadding, height - vPadding, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = Math.min(80f, width / 10f)
        canvas.drawText(titleText, width / 2f, vPadding + 150f, paint)
        timeText?.let {
            paint.textSize = Math.min(60f, width / 12f)
            canvas.drawText(it, width / 2f, vPadding + 250f, paint)
        }

        btnRadius = Math.min(width, height) * 0.08f
        btnCy = height - vPadding - btnRadius - 50f
        val spacing = width / 4f
        restartCx = width / 2f - spacing * 0.75f
        leaderboardCx = width / 2f - spacing * 0.325f
        quitCx = width / 2f + spacing* 0.325f
        facebookCx = width / 2f + spacing* 0.75f

        fun drawButton(cx: Float, cy: Float, iconRes: Int, color: Int) {
            paint.color = color
            canvas.drawCircle(cx, cy, btnRadius, paint)
            val icon = ContextCompat.getDrawable(context, iconRes)
            icon?.let { drawIcon(canvas, it, cx, cy, btnRadius * 1.5f) }
        }

        drawButton(restartCx, btnCy, R.drawable.ic_restart, "#4CAF50".toColorInt())
        drawButton(leaderboardCx, btnCy, R.drawable.ic_leaderboard, "#2196F3".toColorInt())
        drawButton(quitCx, btnCy, R.drawable.ic_home, "#F44336".toColorInt())
        drawButton(facebookCx, btnCy, R.drawable.ic_facebook, "#3b5998".toColorInt())

        paint.alpha = 255
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas, drawable: Drawable, cx: Float, cy: Float, size: Float) {
        drawable.setBounds(
            (cx - size / 2).toInt(),
            (cy - size / 2).toInt(),
            (cx + size / 2).toInt(),
            (cy + size / 2).toInt()
        )
        drawable.draw(canvas)
    }

    fun handleTouch(event: MotionEvent): Boolean {
        if (!showingWin) return false
        if (event.action != MotionEvent.ACTION_DOWN) return false

        fun isInsideCircle(cx: Float, cy: Float, radius: Float): Boolean {
            val dx = event.x - cx
            val dy = event.y - cy
            return dx * dx + dy * dy <= radius * radius
        }

        return when {
            isInsideCircle(restartCx, btnCy, btnRadius) -> {
                listener?.onRestart(); true
            }
            isInsideCircle(leaderboardCx, btnCy, btnRadius) -> {
                listener?.onShowLeaderboard(); true
            }
            isInsideCircle(quitCx, btnCy, btnRadius) -> {
                listener?.onQuitToMenu(); true
            }
            isInsideCircle(facebookCx, btnCy, btnRadius) -> {
                listener?.onFacebookShare(); true
            }
            else -> false
        }
    }

    fun showPanel() { showingWin = true; animatePanel(true) }
    fun hidePanel() { animatePanel(false) }
    fun isShowing(): Boolean = showingWin

    private fun animatePanel(show: Boolean) {
        if (animating) return
        animating = true

        val startAlpha = if (show) 0f else 1f
        val endAlpha = if (show) 1f else 0f
        val startOffset = if (show) 1000f else 0f
        val endOffset = if (show) 0f else 1000f

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val animator = ValueAnimator.ofFloat(0f, 1f)
            animator.duration = animDuration
            animator.addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                panelAlpha = startAlpha + fraction * (endAlpha - startAlpha)
                panelOffsetY = startOffset + fraction * (endOffset - startOffset)
                redraw()
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animating = false
                    if (!show) showingWin = false
                }
            })
            animator.start()
        }
    }
}