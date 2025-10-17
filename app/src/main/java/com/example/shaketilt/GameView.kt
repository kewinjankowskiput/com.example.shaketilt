// GameView.kt
package com.example.shaketilt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import kotlin.math.max
import kotlin.math.sqrt
import androidx.core.graphics.toColorInt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import androidx.core.graphics.withTranslation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.io.FileOutputStream

data class PolygonPlatform(
    val vertices: List<Pair<Float, Float>>,
    val gradientId: Int = 0
)

data class RotatingPlatform(
    val baseVertices: List<Pair<Float, Float>>,
    val pivot: Pair<Float, Float>,
    var angle: Float = 0f,
    val angularSpeed: Float = 1f,
    val gradientId: Int = 0
) {
    fun getRotatedVertices(): List<Pair<Float, Float>> {
        val rad = Math.toRadians(angle.toDouble())
        val cos = kotlin.math.cos(rad)
        val sin = sin(rad)
        val (px, py) = pivot
        return baseVertices.map { (x, y) ->
            val dx = x - px
            val dy = y - py
            val rx = px + dx * cos - dy * sin
            val ry = py + dx * sin + dy * cos
            rx.toFloat() to ry.toFloat()
        }
    }

    fun update() {
        angle = (angle + angularSpeed) % 360f
    }

    fun resetRotation() {
        angle = 0f
    }
}

data class Spike(
    var x: Float,
    var y: Float,
    val radius: Float = 40f
)

data class GameButton(
    val rect: RectF,
    val text: String,
    val action: () -> Unit
)

class GameView(context: Context, private val levelInto: Int) : SurfaceView(context), Runnable, SensorEventListener, SurfaceHolder.Callback {
    private var currentLevel: Level
    private var currentLevelId = levelInto
    private val db: LocalDBHelper = LocalDBHelper(context)

    private val leaderboardPanel = LeaderboardPanel(context, db, currentLevelId) {
        drawGame()
    }.apply {
        listener = object : LeaderboardPanel.Listener {
            override fun onBack() = backToMenu()
            override fun onSwitchScores(showOnline: Boolean) = drawGame()
            override fun onEraseLocalScores() = drawGame()
            override fun onLevelChanged(newLevelId: Int) {
                levelId = newLevelId
                drawGame()
            }
        }
    }

    private val winPanel = WinPanel(context, redraw = { invalidate() }, panelColor = "#009664".toColorInt(), titleText = "You Won!").apply {
        listener = object : WinPanel.Listener {
            override fun onRestart() = restartLevel()
            override fun onShowLeaderboard() = leaderboardPanel.showLeaderboardAnimated()
            override fun onQuitToMenu() = backToMenu()
            override fun onFacebookShare() {
                postScreenshotToFacebook()
            }
        }
    }

    fun postScreenshotToFacebook() {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            PixelCopy.request(this@GameView, bmp, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    shareBitmap(bmp)
                } else {
                    Toast.makeText(context, "Failed to capture screenshot", Toast.LENGTH_SHORT).show()
                }
            }, Handler(Looper.getMainLooper()))
        } else {
            val canvas = Canvas(bmp)
            draw(canvas)
            shareBitmap(bmp)
        }
    }

    private fun shareBitmap(bitmap: Bitmap) {
        try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "screenshot.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Share your score!"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private val gameOverPanel = WinPanel(context, redraw = { invalidate() }, panelColor = "#AA0000".toColorInt(), titleText = "Game Over!").apply {
        listener = object : WinPanel.Listener {
            override fun onRestart() = restartLevel()
            override fun onShowLeaderboard() = leaderboardPanel.showLeaderboardAnimated()
            override fun onQuitToMenu() = backToMenu()
            override fun onFacebookShare() {
                postScreenshotToFacebook()
            }
        }
    }

    private lateinit var pausePanel: PausePanel

    private var isPaused = false
    private var pausedStartTime = 0L
    private var totalPausedTime = 0L
    private var totalMs = 0L;

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this)
        thread?.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        scale = height.toFloat() / virtualHeight
        virtualWidth = width.toFloat() / scale

        offsetX = ((width - virtualWidth * scale) / 2f).coerceAtLeast(0f)
    }

    private var thread: Thread? = null
    private var running = false
    private val paint = Paint()
    private var canvas: Canvas? = null

    private val scoreDb = LocalDBHelper(context)

    private val virtualHeight = 1080f  // fixed
    private var virtualWidth = 1920f   // will adjust based on device
    private var scale = 1f
    private var offsetX = 0f

    private var uploadButton: Button? = null
    private var returnMenuButton: Button? = null
    private val winButtons = mutableListOf<GameButton>()

    private var ballX = 500f
    private var ballY = 500f
    private var ballRadius = 50f
    private var velX = 0f
    private var velY = 0f

    private val gravity = 1f
    private val airFriction = 0.98f
    private val jumpVelocity = -30f
    private var ballRotation = 0f
    private val rotationSpeedFactor = 0.5f

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var accX = 0f
    private var accY = 0f
    private var accZ = 0f
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f
    private var fusedTiltX = 0f
    private var roll = 0f
    private var pitch = 0f
    private var baselineRoll = 0f
    private var smoothedTiltX = 0.0F
    private val tiltDeadzone = 0.05f
    private val tiltSmoothing = 0.2f
    private val shakeThreshold = 15f
    private val rotatingPlatforms = mutableListOf<RotatingPlatform>()

    private var svgBall: com.caverock.androidsvg.SVG? = null
    private val svgBallResourceId = R.raw.ball

    private var svgSpike: com.caverock.androidsvg.SVG? = null
    private var gameOver = false

    private var win = false
    private var svgFlag: com.caverock.androidsvg.SVG? = null
    private val flagResourceId = R.raw.flag

    private var cameraX = 0f
    private var cameraY = 0f
    private var levelRight = 4000f
    private var levelBottom = 1000f

    private var lastFrameTime = System.currentTimeMillis()
    private val targetFPS = 60
    private val targetFrameTime = 1000L / targetFPS
    private var levelStartTime = System.currentTimeMillis()
    private var scoreSaved = false
    private var levelId = 1

    private val platformShaders = mutableListOf<Shader>()
    private val gradientPresets: List<(Float, Float, Float, Float) -> Shader> = listOf(
        { l, t, r, b -> LinearGradient(l, t, l, b, Color.RED, Color.YELLOW, Shader.TileMode.CLAMP) },
        { l, t, r, b -> LinearGradient(l, t, r, t, Color.BLUE, Color.CYAN, Shader.TileMode.CLAMP) },
        { l, t, r, b -> LinearGradient(l, t, r, b, Color.MAGENTA, Color.GREEN, Shader.TileMode.CLAMP) },
        { l, t, r, b -> LinearGradient(l, t, r, b, "#008000".toColorInt(), "#00d050".toColorInt(), Shader.TileMode.CLAMP) },
        { l, t, r, b ->
            val cx = (l + r)/2f
            val cy = (t + b)/2f
            val radius = max(r-l, b-t)/2f
            RadialGradient(cx, cy, radius, Color.WHITE, Color.GRAY, Shader.TileMode.CLAMP)
        }
    )

    private fun initPlatformShaders() {
        platformShaders.clear()
        currentLevel.platforms.forEach { platform ->
            val left = platform.vertices.minOf { it.first }
            val right = platform.vertices.maxOf { it.first }
            val top = platform.vertices.minOf { it.second }
            val bottom = platform.vertices.maxOf { it.second }

            val shader = gradientPresets[platform.gradientId % gradientPresets.size](left, top, right, bottom)
            platformShaders.add(shader)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        pausePanel = PausePanel(context, redraw = { invalidate() })
        pausePanel.listener = object : PausePanel.Listener {
            override fun onResume() {
                pausePanel.hidePanel {
                    isPaused = false
                    totalPausedTime += System.currentTimeMillis() - pausedStartTime
                    pausedStartTime = 0L
                }
            }
            override fun onQuitToMenu() = backToMenu()
        }

        paint.color = Color.RED
        paint.style = Paint.Style.FILL
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
        holder.addCallback(this)
        this.keepScreenOn = true

        try {
            svgBall = com.caverock.androidsvg.SVG.getFromResource(context, svgBallResourceId)
        } catch (e: Exception) {
            e.printStackTrace()
            svgBall = null
        }

        try {
            svgSpike = com.caverock.androidsvg.SVG.getFromResource(context, R.raw.spike)
        } catch (e: Exception) {
            e.printStackTrace()
            svgSpike = null
        }

        try {
            svgFlag = com.caverock.androidsvg.SVG.getFromResource(context, flagResourceId)
        } catch (e: Exception) {
            e.printStackTrace()
            svgFlag = null
        }

        currentLevel = Level.getLevelById(currentLevelId)

        ballX = currentLevel.ballStartX
        ballY = currentLevel.ballStartY

        levelBottom = currentLevel.height
        levelRight = currentLevel.width

        startLevel(currentLevelId);
    }

    fun startLevel(id: Int) {
        levelId = id
        levelStartTime = System.currentTimeMillis()
        pausedStartTime = 0L;
        totalPausedTime = 0L;
        win = false
        scoreSaved = false
    }

    override fun run() {
        while (running) {
            val currentTime = System.currentTimeMillis()
            val delta = (currentTime - lastFrameTime) / 1000f
            lastFrameTime = currentTime

            if (!gameOver && !isPaused) {
                updatePhysics(delta)
            }
            updateCamera()
            drawGame()

            val frameDuration = System.currentTimeMillis() - currentTime
            val sleepTime = (targetFrameTime - frameDuration).coerceAtLeast(0)
            Thread.sleep(sleepTime)
        }
    }

    fun resume() {
        running = true
        thread = Thread(this)
        thread?.start()
    }

    fun pause() {
        running = false
        thread?.join()
    }

    private fun updatePhysics(delta: Float) {
        if (win or gameOver or isPaused) {
            return;
        }
        val tiltXFromFusion = sin(roll - baselineRoll)
        smoothedTiltX = smoothedTiltX * (1 - tiltSmoothing) + tiltXFromFusion * tiltSmoothing
        val effectiveTilt = if (abs(smoothedTiltX) < tiltDeadzone) 0f else smoothedTiltX
        fusedTiltX = effectiveTilt
        velX += fusedTiltX * 1f

        velY += gravity

        velX *= airFriction
        velY *= airFriction

        velX = velX.coerceIn(-10f, 10f)
        velY = velY.coerceIn(-100f, 20f)

        if (win or gameOver) {
            velX = 0f;
            velY = 0f;
        }
        else {
            ballX += velX * delta * 60
            ballY += velY * delta * 60

            val speed = sqrt(velX * velX + velY * velY)
            if (speed > 0.5f) {
                val dir = if (velX >= 0) 1 else -1
                ballRotation = (ballRotation + dir * speed * rotationSpeedFactor) % 360f
            } else ballRotation *= 0.9f
        }

        checkPolygonCollisions(currentLevel.allPlatforms())
        rotatingPlatforms.forEach { platform ->
            checkPolygonCollisions(listOf(PolygonPlatform(platform.getRotatedVertices())))
            platform.update()
        }

        if (ballY > levelBottom + ballRadius) { gameOver = true
            onLose() }

        currentLevel.spikes.forEach { spike ->
            val dx = ballX - spike.x
            val dy = ballY - spike.y
            if (sqrt(dx*dx + dy*dy) < ballRadius + spike.radius) {
                gameOver = true
                onLose()
            }
        }

        if (currentLevel.isBallAtFlag(ballX, ballY) && !win) {
            win = true
            velX = 0f
            velY = 0f
            onWin()
        }
    }

    private fun checkPolygonCollisions(polygons: List<PolygonPlatform>) {
        for (platform in polygons) {
            val verts = platform.vertices
            for (i in verts.indices) {
                val a = verts[i]
                val b = verts[(i+1)%verts.size]
                val ex = b.first - a.first
                val ey = b.second - a.second
                val px = ballX - a.first
                val py = ballY - a.second
                val len2 = ex*ex + ey*ey
                val t = ((px*ex + py*ey)/len2).coerceIn(0f,1f)
                val closestX = a.first + t*ex
                val closestY = a.second + t*ey
                val dx = ballX - closestX
                val dy = ballY - closestY
                val dist = sqrt(dx*dx + dy*dy)
                if (dist < ballRadius) {
                    val overlap = ballRadius - dist
                    val nx = dx / dist
                    val ny = dy / dist
                    ballX += nx * overlap
                    ballY += ny * overlap
                    val dot = velX*nx + velY*ny
                    velX -= 1.5f*dot*nx
                    velY -= 1.5f*dot*ny
                }
            }
        }
    }


    private fun updateCamera() {
        val halfVW = virtualWidth / 2f
        val halfVH = virtualHeight / 2f

        val camX = (ballX - halfVW).coerceIn(0f, max(0f, levelRight - virtualWidth))
        val camY = (ballY - halfVH).coerceIn(0f, max(0f, levelBottom - virtualHeight))

        cameraX = camX
        cameraY = camY
    }

    private var fps = 0f
    private var frameCount = 0
    private var timerStr = ""
    private var lastTime = System.currentTimeMillis()

    private fun drawGame() {
        if (!holder.surface.isValid) return

        canvas = holder.lockCanvas() ?: return

        canvas?.drawColor("#00bfff".toColorInt())

        if (platformShaders.isEmpty()) initPlatformShaders()

        canvas?.withTranslation(offsetX, 0f) {

            this.scale(scale, scale)

            this.translate(-cameraX, -cameraY)

            if (!gameOver) {
                if (svgBall != null) {
                    this.withTranslation(ballX, ballY) {

                        this.rotate(ballRotation)
                        val scaledRadius = ballRadius * 1.15f

                        this.translate(-scaledRadius, -scaledRadius)

                        svgBall?.documentWidth = scaledRadius * 2f
                        svgBall?.documentHeight = scaledRadius * 2f

                        try {
                            svgBall?.renderToCanvas(this)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            paint.color = Color.RED
                            this.drawCircle(0f, 0f, ballRadius, paint)
                        }

                    }
                } else {
                    paint.color = Color.RED
                    this.drawCircle(ballX, ballY, ballRadius, paint)
                }
            }

            currentLevel.platforms.forEachIndexed { index, platform ->
                val path = Path()
                val first = platform.vertices.first()
                path.moveTo(first.first, first.second)
                for (v in platform.vertices.drop(1)) path.lineTo(v.first, v.second)
                path.close()
                paint.shader = platformShaders[index % platformShaders.size]
                this.drawPath(path, paint)
                paint.shader = null
            }

            currentLevel.rotatingPlatforms.forEach { platform ->
                val vertices = platform.getRotatedVertices()
                val path = Path()
                val first = vertices.first()
                path.moveTo(first.first, first.second)
                for (v in vertices.drop(1)) path.lineTo(v.first, v.second)
                path.close()

                // Compute bounding box of rotated vertices
                val left = vertices.minOf { it.first }
                val right = vertices.maxOf { it.first }
                val top = vertices.minOf { it.second }
                val bottom = vertices.maxOf { it.second }

                val shader = gradientPresets[platform.gradientId % gradientPresets.size](
                    left,
                    top,
                    right,
                    bottom
                )
                paint.shader = shader
                this.drawPath(path, paint)
                paint.shader = null

                // Update angle for next frame
                if (!isPaused) {
                platform.update()}
            }

            currentLevel.spikes.forEach { spike ->
                if (svgSpike != null) {
                    this.withTranslation(spike.x, spike.y) {
                        val visualScale = 1f
                        val scaledRadius = spike.radius * visualScale
                        this.translate(-scaledRadius, -scaledRadius)
                        svgSpike?.documentWidth = scaledRadius * 2f
                        svgSpike?.documentHeight = scaledRadius * 2f
                        try {
                            svgSpike?.renderToCanvas(this)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    paint.color = Color.GRAY
                    val path = Path()
                    path.moveTo(spike.x, spike.y - spike.radius)
                    path.lineTo(spike.x - spike.radius, spike.y + spike.radius)
                    path.lineTo(spike.x + spike.radius, spike.y + spike.radius)
                    path.close()
                    this.drawPath(path, paint)
                }
            }

            if (!win) {
                svgFlag?.let {
                    this.withTranslation(currentLevel.flagX, currentLevel.flagY) {
                        val scale = 1.5f
                        this.translate(-ballRadius * scale, -ballRadius * scale)
                        it.documentWidth = ballRadius * 2 * scale
                        it.documentHeight = ballRadius * 2 * scale
                        try {
                            it.renderToCanvas(this)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            frameCount++
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTime >= 1000) { // update every second
                fps = frameCount * 1000f / (currentTime - lastTime)
                frameCount = 0
                lastTime = currentTime
            }

        }

        paint.color = Color.WHITE;
        paint.textSize = 60f;
        paint.textAlign = Paint.Align.LEFT;
        if (!win && !gameOver) {
            totalMs = if (isPaused) {
                pausedStartTime - levelStartTime - totalPausedTime
            } else {
                System.currentTimeMillis() - levelStartTime - totalPausedTime
            }
        }

        if (isPaused && pausedStartTime == 0L) pausedStartTime = System.currentTimeMillis()

        val minutes = (totalMs / 1000 / 60).toInt()
        val seconds = ((totalMs / 1000) % 60).toInt()
        val milliseconds = (totalMs % 1000).toInt()
        timerStr = String.format("%d:%02d.%03d", minutes, seconds, milliseconds)
        if (!isPaused && !win && !gameOver) {
        canvas?.drawText("Time: $timerStr", 20f, 100f, paint)}

        if (!win && !gameOver && !isPaused)
        {
            val pauseBtnRadius = 80f
            val pauseBtnCx = width - pauseBtnRadius * 3
            val pauseBtnCy = pauseBtnRadius*2

            paint.color = Color.YELLOW
            canvas?.drawCircle(pauseBtnCx, pauseBtnCy, pauseBtnRadius, paint)

            paint.color = Color.BLACK
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas?.drawText("II", pauseBtnCx, pauseBtnCy + 15f, paint)
        }

        if (isPaused) {
            pausePanel.draw(canvas!!, width.toFloat(), height.toFloat())
        }

        if (win) winPanel.draw(canvas!!, width.toFloat(), height.toFloat())
        if (gameOver) gameOverPanel.draw(canvas!!, width.toFloat(), height.toFloat())

        paint.color = Color.BLACK
        paint.textSize = 50f
        paint.textAlign = Paint.Align.LEFT
        //canvas?.drawText("FPS: ${fps.toInt()}", 20f, 960f, paint)

        if (leaderboardPanel.showingLeaderboard) {
            leaderboardPanel.draw(canvas!!, width.toFloat(), height.toFloat())
        }

        holder.unlockCanvasAndPost(canvas)
    }


    private fun onLose() {
        velX = 0f;
        velY = 0f;
        gameOverPanel.timeText = timerStr
        gameOverPanel.showPanel();
    }

    private fun onWin() {
        velX = 0f;
        velY = 0f;

        saveScoreLocally(currentLevelId, totalMs);
        uploadScoreOnline(currentLevelId, totalMs);

        winPanel.timeText = timerStr
        winPanel.showPanel()
    }

    private fun backToMenu() {
        val parent = this.parent as? ConstraintLayout
        parent?.removeView(uploadButton)
        parent?.removeView(returnMenuButton)

        uploadButton = null
        returnMenuButton = null

        (context as? Activity)?.finish()
    }

    private fun saveScoreLocally(levelId: Int, timeMs: Long) {
        scoreDb.saveScore(levelId, timeMs)
    }

    private fun uploadScoreOnline(levelId: Int, timeMs: Long, handle: String? = null) {
        val activity = context as? Activity ?: return
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val timestamp = System.currentTimeMillis()
        val userId = user.uid
        val docId = "level_${levelId}_user_$userId"

        val userHandle = handle
            ?: user.displayName
            ?: user.email?.substringBefore("@")
            ?: "User$timestamp"

        val scoreData = hashMapOf(
            "level_id" to levelId,
            "time_ms" to timeMs,
            "timestamp" to timestamp,
            "user_handle" to userHandle,
            "user_email" to user.email
        )

        val db = FirebaseFirestore.getInstance()

        db.enableNetwork()
            .addOnSuccessListener {
                val docRef = db.collection("scores").document(docId)
                docRef.get()
                    .addOnSuccessListener { snapshot ->
                        val prevTime = snapshot.getLong("time_ms")
                        activity.runOnUiThread {
                            if (prevTime == null || timeMs < prevTime) {
                                docRef.set(scoreData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Best time uploaded!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        e.printStackTrace()
                                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        activity.runOnUiThread {
                            Toast.makeText(context, "Failed to retrieve existing time: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                activity.runOnUiThread {
                    Toast.makeText(context, "Failed to enable network: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }


    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { ev ->
            when (ev.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accX = ev.values[0]
                    accY = ev.values[1]
                    accZ = ev.values[2]

                    val accRoll = atan2(accY.toDouble(), accZ.toDouble()).toFloat()
                    roll = accRoll
                    baselineRoll = baselineRoll * 0.999f + accRoll * 0.001f

                    // Shake to jump using proper platform collision detection
                    val acceleration = sqrt(accX * accX + accY * accY + accZ * accZ)
                    if (acceleration > shakeThreshold) {
                        val touchingGround = ballY >= levelBottom - ballRadius - 1 ||
                                currentLevel.allPlatforms().any { platform ->
                                    platform.vertices.zip(platform.vertices.drop(1) + platform.vertices.take(1)).any { (a, b) ->
                                        val ex = b.first - a.first
                                        val ey = b.second - a.second
                                        val px = ballX - a.first
                                        val py = ballY - a.second
                                        val len2 = ex * ex + ey * ey
                                        val t = ((px * ex + py * ey) / len2).coerceIn(0f, 1f)
                                        val closestX = a.first + t * ex
                                        val closestY = a.second + t * ey
                                        val dx = ballX - closestX
                                        val dy = ballY - closestY
                                        val dist = sqrt(dx * dx + dy * dy)
                                        dist < ballRadius + 1 && dy < 0
                                    }
                                }
                        if (touchingGround) velY = jumpVelocity
                    }
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroX = ev.values[0]
                    gyroY = ev.values[1]
                    gyroZ = ev.values[2]
                    roll += gyroY * 0.02f
                    pitch += gyroX * 0.02f
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!isPaused && !gameOver && !win) {
                isPaused = true
                pausedStartTime = System.currentTimeMillis()
                pausePanel.showPanel()
                invalidate()
                return true
            }
            else
            {
                backToMenu();
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { ev ->

            if (pausePanel.isShowing()) {
                if (pausePanel.handleTouch(ev)) {
                    invalidate()
                    return true
                }
                return true
            }

            if (leaderboardPanel.showingLeaderboard) {
                if (leaderboardPanel.handleTouch(ev, width.toFloat(), height.toFloat())) {
                    invalidate()
                    return true
                }
                return true
            }

            if (winPanel.isShowing()) {
                if (winPanel.handleTouch(ev)) {
                    invalidate()
                    return true
                }
            }

            if (gameOverPanel.isShowing()) {
                if (gameOverPanel.handleTouch(ev)) {
                    invalidate()
                    return true
                }
            }

            val pauseBtnRadius = 80f
            val pauseBtnCx = width - pauseBtnRadius * 3
            val pauseBtnCy = pauseBtnRadius*2

            if (!isPaused && !gameOver && !win && ev.action == MotionEvent.ACTION_DOWN) {
                val dx = ev.x - pauseBtnCx
                val dy = ev.y - pauseBtnCy
                if (dx*dx + dy*dy <= pauseBtnRadius*pauseBtnRadius) {
                    isPaused = true
                    pausePanel.showPanel()
                    invalidate()
                    return true
                }
            }

        }

        return true
    }

    fun restartLevel() {
        gameOver = false
        win = false

        ballX = currentLevel.ballStartX
        ballY = currentLevel.ballStartY
        velX = 0f
        velY = 0f
        ballRotation = 0f

        cameraX = 0f
        cameraY = 0f

        currentLevel.rotatingPlatforms.forEach { it.resetRotation() }

        winPanel.hidePanel()
        gameOverPanel.hidePanel()
        leaderboardPanel.hideLeaderboardAnimated()

        winButtons.clear()

        levelStartTime = System.currentTimeMillis()

        fps = 0f
        frameCount = 0
        lastTime = System.currentTimeMillis()

        startLevel(currentLevelId);

        invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}