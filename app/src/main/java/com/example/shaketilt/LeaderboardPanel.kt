package com.example.shaketilt

import android.content.Context
import android.graphics.*
import android.text.format.DateFormat
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.graphics.createBitmap

class LeaderboardPanel(
    private val context: Context,
    private val scoreDb: LocalDBHelper,
    private var currentLevelId: Int,
    private val redraw: () -> Unit
) {

    var showingLeaderboard = false
        private set
    private var showOnlineScores = false

    private var localScoresCache: List<Score> = emptyList()
    private var onlineScoresCache: List<Score> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var leaderboardAlpha = 0f
    private var leaderboardOffsetY = 0f
    private var leaderboardAnimating = false

    private var btnRadius = 0f
    private var backCx = 0f
    private var backCy = 0f
    private var switchCx = 0f
    private var switchCy = 0f
    private var eraseCx = 0f
    private var eraseCy = 0f

    private var levelBtnRadius = 0f
    private var levelBtnY = 0f
    private var prevLevelCx = 0f
    private var nextLevelCx = 0f

    private var backgroundBitmap: Bitmap? = null
    private var backIconBitmap: Bitmap? = null
    private var switchIconBitmap: Bitmap? = null
    private var eraseIconBitmap: Bitmap? = null
    private var prevLevelBitmap: Bitmap? = null
    private var nextLevelBitmap: Bitmap? = null

    private var formattedLocalScores: List<Pair<String, String>> = emptyList()
    private var formattedOnlineScores: List<Pair<String, String>> = emptyList()

    interface Listener {
        fun onBack()
        fun onSwitchScores(showOnline: Boolean)
        fun onEraseLocalScores()
        fun onLevelChanged(newLevelId: Int)
    }
    var listener: Listener? = null

    init {
        backIconBitmap = drawableToBitmap(ContextCompat.getDrawable(context, R.drawable.ic_arrow_back)!!)
        switchIconBitmap = drawableToBitmap(ContextCompat.getDrawable(context, R.drawable.ic_sync)!!)
        eraseIconBitmap = drawableToBitmap(ContextCompat.getDrawable(context, R.drawable.ic_delete)!!)
        prevLevelBitmap = drawableToBitmap(ContextCompat.getDrawable(context, android.R.drawable.ic_media_previous)!!)
        nextLevelBitmap = drawableToBitmap(ContextCompat.getDrawable(context, android.R.drawable.ic_media_next)!!)
    }

    // --- Public API ---
    fun showLeaderboardAnimated() {
        if (showingLeaderboard || leaderboardAnimating) return

        showingLeaderboard = true
        leaderboardAlpha = 1f
        leaderboardOffsetY = 0f

        localScoresCache = scoreDb.getTopScores(currentLevelId)
        preformatLocalScores()

        fetchOnlineScores()

        redraw()
    }

    fun showOnlineLeaderboard() {
        refreshScores()
        showLeaderboardAnimated()
    }

    fun fetchOnlineScores() {
        onlineScoresCache = emptyList()
        onlineScoreHandles.clear()

        FirebaseFirestore.getInstance().collection("scores")
            .whereEqualTo("level_id", currentLevelId)
            .get()
            .addOnSuccessListener { snapshot ->
                val scores = mutableListOf<Score>()

                snapshot.documents.forEach { doc ->
                    val levelId = doc.getLong("level_id")?.toInt()
                    val timeMs = doc.getLong("time_ms")
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                    if (levelId != null && timeMs != null) {
                        val score = Score(levelId, timeMs, timestamp)
                        scores.add(score)

                        val handle = doc.getString("user_handle")
                            ?: doc.getString("user_email")?.substringBefore("@")
                            ?: "Anonymous"
                        onlineScoreHandles[score] = handle
                    }
                }

                onlineScoresCache = scores.sortedBy { it.timeMs }
                preformatOnlineScores()
                redraw()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    private fun preformatOnlineScores() {
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)

        formattedOnlineScores = onlineScoresCache.map { score ->
            val date = java.util.Date(score.timestamp)
            val ts = "${dateFormat.format(date)} ${timeFormat.format(date)}"
            val time = "${score.timeMs / 1000f} s"
            val handle = onlineScoreHandles[score] ?: "Anonymous"
            "$handle ($ts)" to time
        }
    }

    fun hideLeaderboardAnimated() {
        if (!showingLeaderboard || leaderboardAnimating) return
        animateLeaderboard(false)
    }

    fun setLevel(levelId: Int) {
        currentLevelId = levelId
        refreshScores()
    }


    private var onlineScoreHandles: MutableMap<Score, String> = mutableMapOf()

    fun refreshScores() {
        localScoresCache = scoreDb.getTopScores(currentLevelId)
        preformatLocalScores()
        redraw()

        FirebaseFirestore.getInstance().collection("scores")
            .whereEqualTo("level_id", currentLevelId)
            .get()
            .addOnSuccessListener { snapshot ->
                onlineScoreHandles.clear()
                onlineScoresCache = snapshot.documents.mapNotNull { doc ->
                    val levelId = doc.getLong("level_id")?.toInt()
                    val timeMs = doc.getLong("time_ms")
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val handle = doc.getString("user_handle")
                        ?: doc.getString("user_email")?.substringBefore("@")
                        ?: "Anonymous"
                    if (levelId != null && timeMs != null) {
                        val score = Score(levelId, timeMs, timestamp)
                        onlineScoreHandles[score] = handle
                        score
                    } else null
                }.sortedBy { it.timeMs }

                preformatOnlineScores()
                redraw()
            }
    }

    private fun preformatLocalScores() {
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)

        formattedLocalScores = localScoresCache.map { score ->
            val date = java.util.Date(score.timestamp)
            val ts = "${dateFormat.format(date)} ${timeFormat.format(date)}"
            val time = "${score.timeMs / 1000f} s"
            ts to time
        }
    }

    fun handleTouch(event: MotionEvent, width: Float, height: Float): Boolean {
        if (!showingLeaderboard) return false
        if (event.action != MotionEvent.ACTION_DOWN) return false

        fun isInsideCircle(cx: Float, cy: Float, radius: Float): Boolean {
            val dx = event.x - cx
            val dy = event.y - cy
            return dx * dx + dy * dy <= radius * radius
        }

        if (isInsideCircle(prevLevelCx, levelBtnY, levelBtnRadius)) {
            val newLevel = (currentLevelId + 1) % 3 + 1
            setLevel(newLevel)
            listener?.onLevelChanged(newLevel)
            return true
        }
        if (isInsideCircle(nextLevelCx, levelBtnY, levelBtnRadius)) {
            val newLevel = (currentLevelId) % 3 + 1
            setLevel(newLevel)
            listener?.onLevelChanged(newLevel)
            return true
        }

        if (isInsideCircle(backCx, backCy, btnRadius)) {
            hideLeaderboardAnimated()
            return true
        }
        if (isInsideCircle(switchCx, switchCy, btnRadius)) {
            showOnlineScores = !showOnlineScores
            listener?.onSwitchScores(showOnlineScores)
            redraw()
            return true
        }
        if (!showOnlineScores && isInsideCircle(eraseCx, eraseCy, btnRadius)) {
            scoreDb.clearAllScores()
            localScoresCache = emptyList()
            formattedLocalScores = emptyList()
            listener?.onEraseLocalScores()
            redraw()
            return true
        }

        return false
    }

    fun draw(canvas: Canvas, width: Float, height: Float) {
        if (!showingLeaderboard && leaderboardAlpha <= 0f) return

        val hPadding = Math.min(240f, width * 0.25f)
        val vPadding = Math.min(60f, height * 0.05f)

        if (backgroundBitmap == null || backgroundBitmap?.width != width.toInt() || backgroundBitmap?.height != height.toInt()) {
            backgroundBitmap = createBitmap(width.toInt(), height.toInt())
            val bgCanvas = Canvas(backgroundBitmap!!)

            paint.color = Color.argb(200, 127, 0, 0)
            bgCanvas.drawRect(hPadding, vPadding, width - hPadding, height - vPadding, paint)
        }

        canvas.save()
        canvas.translate(0f, leaderboardOffsetY)
        paint.alpha = (255 * leaderboardAlpha).toInt()
        canvas.drawBitmap(backgroundBitmap!!, 0f, 0f, paint)
        paint.alpha = 255

        val titleFontSize = Math.min(60f, width / 12f) * 1.5f
        val textFontSize = Math.min(40f, width / 20f) * 1.5f

        btnRadius = Math.min(width, height) * 0.08f
        levelBtnRadius = btnRadius * 0.6f
        levelBtnY = vPadding + titleFontSize
        prevLevelCx = hPadding + titleFontSize
        nextLevelCx = width - hPadding - titleFontSize

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = titleFontSize
        val titleText = if (showOnlineScores) "Leaderboard: LV$currentLevelId (Online)" else "Leaderboard: LV$currentLevelId (Local)"
        canvas.drawText(titleText, width / 2, vPadding + titleFontSize, paint)

        paint.color = "#b08080".toColorInt()
        canvas.drawCircle(prevLevelCx, levelBtnY, levelBtnRadius, paint)
        canvas.drawCircle(nextLevelCx, levelBtnY, levelBtnRadius, paint)

        prevLevelBitmap?.let { drawBitmapCentered(canvas, it, prevLevelCx, levelBtnY, levelBtnRadius * 1.2f) }
        nextLevelBitmap?.let { drawBitmapCentered(canvas, it, nextLevelCx, levelBtnY, levelBtnRadius * 1.2f) }

        btnRadius = Math.min(width, height) * 0.08f
        val btnY = height - vPadding - btnRadius - 20f
        backCx = width / 4
        backCy = btnY
        switchCx = width * 3 / 4
        switchCy = btnY
        eraseCx = width / 2
        eraseCy = btnY

        paint.color = "#b08080".toColorInt()
        canvas.drawCircle(backCx, btnY, btnRadius, paint)
        canvas.drawCircle(switchCx, btnY, btnRadius, paint)
        if (!showOnlineScores) {
            paint.color = "#ff4040".toColorInt()
            canvas.drawCircle(eraseCx, eraseCy, btnRadius, paint)
        }

        backIconBitmap?.let { drawBitmapCentered(canvas, it, backCx, btnY, btnRadius * 1.2f) }
        switchIconBitmap?.let { drawBitmapCentered(canvas, it, switchCx, btnY, btnRadius * 1.2f) }
        if (!showOnlineScores) eraseIconBitmap?.let { drawBitmapCentered(canvas, it, eraseCx, btnY, btnRadius * 1.2f) }

        paint.color = "#ffffff".toColorInt()
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = textFontSize
        var y = vPadding + titleFontSize * 2.5f
        val leftX = hPadding + 20f

        val scoresToDraw = if (showOnlineScores) formattedOnlineScores else formattedLocalScores
        scoresToDraw.forEach { (ts, time) ->
            if (y + textFontSize > btnY - 20f) return@forEach
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("($ts)", width - hPadding - 20f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(time, leftX, y, paint)
            y += textFontSize + 10f
        }

        canvas.restore()
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun drawBitmapCentered(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, size: Float) {
        val scale = size / bitmap.width
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(cx - size / 2, cy - size / 2)
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun animateLeaderboard(show: Boolean) {
        leaderboardAnimating = false
        showingLeaderboard = show
        leaderboardAlpha = if (show) 1f else 0f
        leaderboardOffsetY = if (show) 0f else 1000f
        redraw()
    }

    private val redrawRunnable = Runnable { redraw() }
}