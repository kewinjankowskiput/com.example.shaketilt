package com.example.shaketilt

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.PictureDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.*
import com.caverock.androidsvg.SVG
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var layout: ConstraintLayout
    private lateinit var sensorManager: SensorManager

    private var greetingText = ""
    private var rotationX = 0f
    private var rotationY = 0f

    private var iconDrawable: PictureDrawable? = null
    private var logoDrawable: PictureDrawable? = null

    private val db by lazy { LocalDBHelper(this) }
    private var levelId = 1
    private lateinit var leaderboardPanel: LeaderboardPanel
    private lateinit var levelPanel: LevelPanel

    private var startCx = 0f
    private var loginCx = 0f
    private var leaderboardCx = 0f
    private var btnCy = 0f
    private var btnRadius = 0f

    private var introRunning = true
    private var introAlpha = 1f
    private var ballY = -1000f
    private var ballVelocity = 0f
    private var ballAcceleration = 2500f
    private var ballRadius = 0f
    private var ballTargetY = 0f
    private var ballDrawable: PictureDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        levelPanel = LevelPanel(this, redraw = { layout.invalidate() }).apply {
            listener = object : LevelPanel.Listener {
                override fun onLevelSelected(level: Int) {
                    startActivity(Intent(this@MainActivity, GameActivity::class.java).apply {
                        putExtra("LEVEL_ID", level)
                    })
                    hide()
                }
                override fun onQuit() { hide() }
            }
        }

        layout = object : ConstraintLayout(this) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (introRunning) drawIntro(canvas) else drawMainMenu(canvas)
            }
        }.apply { setWillNotDraw(false) }
        layout.setBackgroundColor(Color.parseColor("#00bfff"))
        setContentView(layout)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        iconDrawable = PictureDrawable(SVG.getFromResource(this, R.raw.icon).renderToPicture())
        logoDrawable = PictureDrawable(SVG.getFromResource(this, R.raw.textlogo).renderToPicture())
        ballDrawable = PictureDrawable(SVG.getFromResource(this, R.raw.ball).renderToPicture())

        leaderboardPanel = LeaderboardPanel(this, db, levelId) { layout.invalidate() }.apply {
            listener = object : LeaderboardPanel.Listener {
                override fun onBack() = hideLeaderboardAnimated()
                override fun onSwitchScores(showOnline: Boolean) = layout.invalidate()
                override fun onEraseLocalScores() = layout.invalidate()
                override fun onLevelChanged(newLevelId: Int) {
                    levelId = newLevelId
                    layout.invalidate()
                }
            }
        }

        updateGreeting()
        layout.post { startIntroAnimation() }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            rotationX = max(-15f, min(15f, -event.values[0]))
            rotationY = max(-15f, min(15f, event.values[1]))
            layout.invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateGreeting() {
        val user = auth.currentUser
        greetingText = if (user != null) {
            val displayName = user.displayName ?: user.email?.substringBefore("@")?.take(user.email!!.length / 2)
            "Hello, ${displayName ?: "Player"}! Click to change handle"
        } else {
            "Unregistered! Log in to upload high scores"
        }
        layout.invalidate()
    }

    private fun startIntroAnimation() {
        val width = layout.width.toFloat()
        val height = layout.height.toFloat()
        ballRadius = min(width, height) * 0.1f
        ballY = -ballRadius
        ballTargetY = height - ballRadius - 50f
        ballVelocity = 0f

        val fps = 60
        val frameDuration = 1000L / fps

        introRunning = true
        val animator = ValueAnimator.ofInt(0, Int.MAX_VALUE)
        animator.duration = Long.MAX_VALUE
        animator.addUpdateListener {
            if (!introRunning) return@addUpdateListener

            val dt = frameDuration / 1000f

            introAlpha = max(0f, introAlpha - dt / 2f)

            ballVelocity += ballAcceleration * dt
            ballY += ballVelocity * dt

            if (ballY > ballTargetY) {
                ballY = ballTargetY
                ballVelocity = -ballVelocity * 0.5f // bounce with damping
                if (kotlin.math.abs(ballVelocity) < 50f) introRunning = false
            }

            layout.invalidate()
        }
        animator.start()
    }

    private fun drawIntro(canvas: Canvas) {
        val width = layout.width.toFloat()
        val height = layout.height.toFloat()
        if (width == 0f || height == 0f) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.alpha = (introAlpha * 255).toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        ballDrawable?.let { ball ->
            val size = ballRadius * 6
            ball.setBounds(((width - size) / 2f).toInt(), ballY.toInt(),
                ((width + size) / 2f).toInt(), (ballY + size).toInt())
            ball.draw(canvas)
        }
    }

    private var hillOffset = 0f
    private val hillSpeed = 2f

    private fun drawHills(canvas: Canvas, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val amplitude = 50f
        val frequency = 3f * PI / width

        val path = Path()
        path.moveTo(0f, height)

        val yOffset = height * 0.9f

        val step = 20f
        var x = 0f
        while (x <= width) {
            val y = (sin(frequency * (x + hillOffset)) * amplitude).toFloat() + yOffset
            path.lineTo(x, y)
            x += step
        }

        path.lineTo(width, height)
        path.close()

        paint.color = "#008060".toColorInt()
        canvas.drawPath(path, paint)

        hillOffset += hillSpeed
        if (hillOffset > width) hillOffset -= width
    }

    private fun drawMainMenu(canvas: Canvas) {
        val width = layout.width.toFloat()
        val height = layout.height.toFloat()
        if (width == 0f || height == 0f) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hPadding = width * 0.1f
        val vPadding = height * 0.05f

        iconDrawable?.let { icon ->
            val pic = icon.picture
            val iconWidth = pic.width.toFloat()
            val iconHeight = pic.height.toFloat()

            val targetWidth = width * 0.5f
            val targetHeight = height * 0.5f
            val cx = width / 2f
            val cy = vPadding + targetHeight / 2f

            val scale = max(targetWidth / iconWidth, targetHeight / iconHeight)

            val scaledWidth = iconWidth * scale
            val scaledHeight = iconHeight * scale

            val dx = cx - scaledWidth / 2f
            val dy = cy - scaledHeight / 2f

            canvas.save()
            canvas.clipRect(cx - targetWidth / 2f, cy - targetHeight / 2f,
                cx + targetWidth / 2f, cy + targetHeight / 2f)

            canvas.translate(dx, dy)
            canvas.scale(scale, scale)

            icon.setBounds(0, 0, pic.width, pic.height)
            icon.draw(canvas)
            canvas.restore()
        }

        logoDrawable?.let { logo ->
            val cx = width * 0.65f
            val mainIconHeight = min(width, height) * 0.5f
            val targetWidth = width * 0.8f
            val aspectRatio = logo.intrinsicHeight.toFloat() / logo.intrinsicWidth
            val targetHeight = targetWidth * aspectRatio
            val cy = vPadding + mainIconHeight / 2

            logo.setBounds((cx - targetWidth / 2).toInt(), (cy - targetHeight / 2).toInt(), (cx + targetWidth / 2).toInt(), (cy + targetHeight / 2).toInt())
            drawHills(canvas, width, height)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(rotationY, 0f, 0f)
            canvas.translate(-cx, -cy)
            logo.draw(canvas)
            canvas.restore()
        }

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(width / 12f, 60f)
        canvas.drawText(greetingText, width / 2f, height * 0.7f, paint)

        btnRadius = min(width, height) * 0.08f
        btnCy = height * 0.8f
        val spacing = width / 4f
        startCx = width / 2f - spacing * 0.5f
        loginCx = width / 2f
        leaderboardCx = width / 2f + spacing * 0.5f

        paint.color = "#4CAF50".toColorInt()
        canvas.drawCircle(startCx, btnCy, btnRadius, paint)
        drawButtonIcon(canvas, startCx, btnCy, btnRadius, R.drawable.ic_play)

        paint.color = "#FF4081".toColorInt()
        canvas.drawCircle(loginCx, btnCy, btnRadius, paint)
        val loginIcon = if (auth.currentUser != null) R.drawable.ic_logout else R.drawable.ic_login
        drawButtonIcon(canvas, loginCx, btnCy, btnRadius, loginIcon)

        paint.color = "#2196F3".toColorInt()
        canvas.drawCircle(leaderboardCx, btnCy, btnRadius, paint)
        drawButtonIcon(canvas, leaderboardCx, btnCy, btnRadius, R.drawable.ic_leaderboard)

        leaderboardPanel.draw(canvas, width, height)
        levelPanel.draw(canvas, width, height)
    }

    private fun drawButtonIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, iconRes: Int) {
        val drawable = ContextCompat.getDrawable(this, iconRes) ?: return
        val size = radius * 1.2f
        drawable.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
        drawable.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)

        if (levelPanel.handleTouch(event)) return true
        if (leaderboardPanel.handleTouch(event, layout.width.toFloat(), layout.height.toFloat())) return true

        val x = event.x
        val y = event.y

        if (!leaderboardPanel.showingLeaderboard && !levelPanel.isShowing() && isInsideCircle(x, y, startCx, btnCy, btnRadius)) {
            levelPanel.show()
            return true
        }
        if (!leaderboardPanel.showingLeaderboard && !levelPanel.isShowing() && isInsideCircle(x, y, loginCx, btnCy, btnRadius)) {
            handleLoginLogout()
            return true
        }
        if (isInsideCircle(x, y, leaderboardCx, btnCy, btnRadius) && !levelPanel.isShowing()) {
            leaderboardPanel.showOnlineLeaderboard()
            return true
        }

        return super.onTouchEvent(event)
    }

    private fun isInsideCircle(x: Float, y: Float, cx: Float, cy: Float, radius: Float) =
        (x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius

    private fun handleLoginLogout() {
        if (auth.currentUser != null) auth.signOut() else startActivity(Intent(this, LoginActivity::class.java))
        updateGreeting()
    }
}