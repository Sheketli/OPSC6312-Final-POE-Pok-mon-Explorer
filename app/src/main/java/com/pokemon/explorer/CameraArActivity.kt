package com.pokemon.explorer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import coil.load
import com.google.android.material.card.MaterialCardView
import com.pokemon.explorer.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.random.Random

class CameraArActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var arOverlayContainer: FrameLayout
    private lateinit var ivTargetReticle: ImageView
    private lateinit var cardPokemonMetrics: MaterialCardView
    private lateinit var tvArPokemonId: TextView
    private lateinit var tvArPokemonName: TextView
    private lateinit var tvTypePrimary: TextView
    private lateinit var tvArPokemonDescription: TextView
    private lateinit var btnCatchAction: Button
    private lateinit var tvRareSpawnTimer: TextView

    private lateinit var cameraExecutor: ExecutorService
    private val cameraPermissionRequestCode = 9911

    // Data class to store physical "world" coordinates for AR Pokemon
    private data class ArPokemon(val view: ImageView, val worldX: Float, val worldY: Float, val id: Int)
    
    private val arPokemons = mutableListOf<ArPokemon>()
    private var targetedPokemonId: Int? = null
    private var isFetching = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Rare Spawn logic
    private var rareSpawnId: Int = -1
    private var rareSpawnExpiresAt: Long = -1L
    private var spawnTimer: android.os.CountDownTimer? = null
    
    // Preferences
    private var isGyroscopeEnabled = true
    private var isScreenDraggingEnabled = true

    // Sensor Tracking
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var initialYaw = Float.NaN
    private var initialPitch = Float.NaN
    
    private var gyroOffsetX = 0f
    private var gyroOffsetY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            
            val rotationMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val orientationAngles = FloatArray(3)
            
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            SensorManager.getOrientation(remappedMatrix, orientationAngles)
            
            val currentYaw = orientationAngles[0]
            val currentPitch = orientationAngles[1]
            
            if (currentYaw.isNaN() || currentPitch.isNaN()) return
            
            if (initialYaw.isNaN()) {
                initialYaw = currentYaw
                initialPitch = currentPitch
            }
            
            var yawDelta = currentYaw - initialYaw
            if (yawDelta > PI) yawDelta -= (2 * PI).toFloat()
            if (yawDelta < -PI) yawDelta += (2 * PI).toFloat()
            
            var pitchDelta = currentPitch - initialPitch
            if (pitchDelta > PI) pitchDelta -= (2 * PI).toFloat()
            if (pitchDelta < -PI) pitchDelta += (2 * PI).toFloat()
            
            val displayMetrics = resources.displayMetrics
            val fovPixels = displayMetrics.widthPixels * 1.5f
            
            // Map physical rotation in remapped portrait space to virtual world offset
            val targetOffsetX = -(yawDelta / (PI / 3)).toFloat() * fovPixels
            val targetOffsetY = -(pitchDelta / (PI / 3)).toFloat() * displayMetrics.heightPixels * 1.2f
            
            if (!targetOffsetX.isNaN() && !targetOffsetY.isNaN()) {
                // Low-pass filter (smoothing) on gyro offset
                gyroOffsetX += (targetOffsetX - gyroOffsetX) * 0.2f
                gyroOffsetY += (targetOffsetY - gyroOffsetY) * 0.2f
                updatePokemonPositions()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val trackingHandler = Handler(Looper.getMainLooper())
    private val trackingRunnable = object : Runnable {
        override fun run() {
            checkCrosshairIntersection()
            trackingHandler.postDelayed(this, 100) // Check every 100ms
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_ar)

        // Read preferences
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        isGyroscopeEnabled = prefs.getBoolean("gyroscope_enabled", true)
        isScreenDraggingEnabled = prefs.getBoolean("screen_dragging_enabled", true)

        // Bind layout views
        viewFinder = findViewById(R.id.viewFinder)
        arOverlayContainer = findViewById(R.id.arOverlayContainer)
        ivTargetReticle = findViewById(R.id.ivTargetReticle)
        cardPokemonMetrics = findViewById(R.id.cardPokemonMetrics)
        tvArPokemonId = findViewById(R.id.tvArPokemonId)
        tvArPokemonName = findViewById(R.id.tvArPokemonName)
        tvTypePrimary = findViewById(R.id.tvTypePrimary)
        tvArPokemonDescription = findViewById(R.id.tvArPokemonDescription)
        btnCatchAction = findViewById(R.id.btnCatchAction)
        tvRareSpawnTimer = findViewById(R.id.tvRareSpawnTimer)

        rareSpawnId = intent.getIntExtra("rare_pokemon_id", -1)
        rareSpawnExpiresAt = intent.getLongExtra("expires_at", -1L)

        cameraExecutor = Executors.newSingleThreadExecutor()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Setup pseudo-AR tracking via touch drag on the viewfinder (Fallback for no-gyro devices)
        if (isScreenDraggingEnabled) {
            setupCameraTracking()
        }

        // Verify and process device streaming camera hardware permissions
        if (hasCameraPermission()) {
            bindCameraXLifecycleEngine()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), cameraPermissionRequestCode
            )
        }

        if (rareSpawnId != -1 && rareSpawnExpiresAt != -1L) {
            handleRareSpawn()
        } else {
            // Spawn multiple Pokémon after a short delay to simulate AR
            Handler(Looper.getMainLooper()).postDelayed({
                spawnArPokemons()
                startCrosshairTracking()
            }, 1500)
        }

        val appState = ViewModelProvider(this)[AppState::class.java]

        btnCatchAction.setOnClickListener {
            val caughtName = tvArPokemonName.text
            Toast.makeText(this, "$caughtName captured successfully!", Toast.LENGTH_SHORT).show()
            
            // Add the caught pokemon to the collection
            targetedPokemonId?.let { id ->
                appState.addCaught(id)

                val caughtArPoke = arPokemons.find { it.id == id }
                if (caughtArPoke != null) {
                    arOverlayContainer.removeView(caughtArPoke.view)
                    arPokemons.remove(caughtArPoke)
                }
            }
            
            // Reset HUD
            targetedPokemonId = null
            cardPokemonMetrics.visibility = View.GONE
            
            // Exit if all caught
            if (arPokemons.isEmpty()) {
                finishAfterTransition() 
            }
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        rareSpawnId = intent.getIntExtra("rare_pokemon_id", -1)
        rareSpawnExpiresAt = intent.getLongExtra("expires_at", -1L)
        
        if (rareSpawnId != -1 && rareSpawnExpiresAt != -1L) {
            spawnTimer?.cancel()
            handleRareSpawn()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isGyroscopeEnabled) {
            rotationSensor?.let {
                sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun handleRareSpawn() {
        val currentTime = System.currentTimeMillis()
        if (currentTime > rareSpawnExpiresAt) {
            Toast.makeText(this, "Oh no! The rare Pokémon fled...", Toast.LENGTH_LONG).show()
            
            rareSpawnId = -1
            rareSpawnExpiresAt = -1L
            
            Handler(Looper.getMainLooper()).postDelayed({
                spawnArPokemons()
                startCrosshairTracking()
            }, 1500)
            return
        }

        tvRareSpawnTimer.visibility = View.VISIBLE
        val timeLeft = rareSpawnExpiresAt - currentTime

        spawnTimer = object : android.os.CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000) % 60
                val minutesRemaining = (millisUntilFinished / 1000) / 60
                tvRareSpawnTimer.text = String.format("%02d:%02d", minutesRemaining, secondsRemaining)
            }

            override fun onFinish() {
                Toast.makeText(this@CameraArActivity, "The rare Pokémon ran away!", Toast.LENGTH_LONG).show()
                tvRareSpawnTimer.visibility = View.GONE
                
                // Remove the rare pokemon from the screen
                val fledPoke = arPokemons.find { it.id == rareSpawnId }
                if (fledPoke != null) {
                    arOverlayContainer.removeView(fledPoke.view)
                    arPokemons.remove(fledPoke)
                }
                
                if (targetedPokemonId == rareSpawnId) {
                    targetedPokemonId = null
                    cardPokemonMetrics.visibility = View.GONE
                }
                
                // Reset rare spawn tracking
                rareSpawnId = -1
                rareSpawnExpiresAt = -1L
                
                // Spawn normal pokémons to continue the hunt
                spawnArPokemons()
            }
        }.start()

        Handler(Looper.getMainLooper()).postDelayed({
            spawnArRarePokemon(rareSpawnId)
            startCrosshairTracking()
        }, 1500)
    }

    private fun setupCameraTracking() {
        // Attach touch listener directly to arOverlayContainer so touch events covering the viewport are caught
        arOverlayContainer.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    
                    dragOffsetX += dx
                    dragOffsetY += dy
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    
                    updatePokemonPositions()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun updatePokemonPositions() {
        val totalOffsetX = (if (gyroOffsetX.isNaN()) 0f else gyroOffsetX) + (if (dragOffsetX.isNaN()) 0f else dragOffsetX)
        val totalOffsetY = (if (gyroOffsetY.isNaN()) 0f else gyroOffsetY) + (if (dragOffsetY.isNaN()) 0f else dragOffsetY)
        
        for (poke in arPokemons) {
            val newX = poke.worldX + totalOffsetX
            val newY = poke.worldY + totalOffsetY
            
            if (newX.isNaN() || newY.isNaN()) continue
            
            // Apply the camera offset to keep them "anchored" in the physical world
            poke.view.x = newX
            poke.view.y = newY
            
            // Scale based on Y position to create faux depth (lower on screen = closer/bigger)
            // assuming bottom of screen is closer to user
            val displayMetrics = resources.displayMetrics
            val normalizedY = newY / displayMetrics.heightPixels
            val scale = if (normalizedY.isNaN()) 1.0f else 0.5f + (normalizedY * 1.5f).coerceIn(0f, 1.5f)
            
            if (!scale.isNaN()) {
                poke.view.scaleX = scale
                poke.view.scaleY = scale
            }
        }
    }

    private fun spawnArRarePokemon(pokemonId: Int) {
        arPokemons.clear()
        arOverlayContainer.removeAllViews()

        // Reset orientation calibration so rare spawn centers relative to current hold position
        initialYaw = Float.NaN
        initialPitch = Float.NaN
        gyroOffsetX = 0f
        gyroOffsetY = 0f
        dragOffsetX = 0f
        dragOffsetY = 0f

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // Place near reticle center with a subtle, easily manageable offset
        val randomOffsetX = kotlin.random.Random.nextInt(-60, 60).toFloat()
        val randomOffsetY = kotlin.random.Random.nextInt(-40, 40).toFloat()
        val worldX = (screenWidth / 2f) + randomOffsetX
        val worldY = (screenHeight * 0.45f) + randomOffsetY

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(400, 400) // Larger size for AR presence
            // Load sprite directly via network URL
            load("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$pokemonId.png") {
                crossfade(true)
                placeholder(R.drawable.ic_pokeball) // Show generic fallback while fetching high-res image
                error(R.drawable.ic_pokeball)
            }
        }
        
        arOverlayContainer.addView(imageView)
        val arPoke = ArPokemon(imageView, worldX, worldY, pokemonId)
        arPokemons.add(arPoke)
        
        animateBreathing(imageView)
        updatePokemonPositions()
    }

    private fun spawnArPokemons() {
        // Reset orientation calibration
        initialYaw = Float.NaN
        initialPitch = Float.NaN
        gyroOffsetX = 0f
        gyroOffsetY = 0f
        dragOffsetX = 0f
        dragOffsetY = 0f

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Spawn 4 random Pokémon
        val count = 4
        val existingPositions = mutableListOf<Pair<Float, Float>>()
        
        for (i in 0 until count) {
            val pokeId = Random.nextInt(1, 152) // Gen 1 Pokedex range
            
            var worldX = 0f
            var worldY = 0f
            var valid = false
            var attempts = 0
            
            while (!valid && attempts < 100) {
                // Spread them across a wide virtual cylinder around the user
                worldX = Random.nextInt((-screenWidth * 3.5f).toInt(), (screenWidth * 3.5f).toInt()).toFloat()
                worldY = Random.nextInt((screenHeight * 0.30f).toInt(), (screenHeight * 0.70f).toInt()).toFloat()
                
                valid = true
                for (pos in existingPositions) {
                    val dx = pos.first - worldX
                    val dy = pos.second - worldY
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                    // Ensure they are spaced apart in 3D space
                    if (dist < screenWidth * 1.5f) {
                        valid = false
                        break
                    }
                }
                attempts++
            }
            existingPositions.add(Pair(worldX, worldY))
            
            val imageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(400, 400)
                x = worldX
                y = worldY
                tag = pokeId
                
                // Load sprite using Coil (Official Artwork API)
                load("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$pokeId.png") {
                    crossfade(true)
                }
            }
            
            arOverlayContainer.addView(imageView)
            val arPoke = ArPokemon(imageView, worldX, worldY, pokeId)
            arPokemons.add(arPoke)
            
            animateBreathing(imageView)
        }
        
        // Apply initial camera offset and depth scaling
        updatePokemonPositions()
    }

    private fun animateBreathing(imageView: ImageView) {
        val duration = Random.nextLong(1500, 2500)
        
        // Just subtly scale up and down to look alive, relying on updatePokemonPositions for base scale
        imageView.animate()
            .scaleXBy(0.05f)
            .scaleYBy(-0.02f)
            .setDuration(duration)
            .withEndAction {
                imageView.animate()
                    .scaleXBy(-0.05f)
                    .scaleYBy(0.02f)
                    .setDuration(duration)
                    .withEndAction { animateBreathing(imageView) }
                    .start()
            }.start()
    }

    private fun startCrosshairTracking() {
        trackingHandler.post(trackingRunnable)
    }

    private fun checkCrosshairIntersection() {
        if (isFetching || arPokemons.isEmpty()) return

        val reticleRect = Rect()
        ivTargetReticle.getHitRect(reticleRect)
        
        val reticleCenterX = reticleRect.centerX()
        val reticleCenterY = reticleRect.centerY()
        val hitRadius = reticleRect.width() / 2f

        var foundTarget: ArPokemon? = null
        var closestDistance = Float.MAX_VALUE

        // Check if the center of the crosshair is close to any AR Pokémon center
        for (poke in arPokemons) {
            // Get actual current position taking camera offset and scaling into account
            val centerX = poke.view.x + (poke.view.width / 2f * poke.view.scaleX)
            val centerY = poke.view.y + (poke.view.height / 2f * poke.view.scaleY)
            
            val dx = reticleCenterX - centerX
            val dy = reticleCenterY - centerY
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            
            // Use a generous hit radius multiplied by the scale of the pokemon
            val effectiveRadius = hitRadius + (200f * poke.view.scaleX)
            
            if (distance < effectiveRadius && distance < closestDistance) {
                closestDistance = distance
                foundTarget = poke
            }
        }

        if (foundTarget != null) {
            val pokeId = foundTarget.id
            if (targetedPokemonId != pokeId) {
                targetedPokemonId = pokeId
                fetchPokemonDetails(pokeId)
            }
        } else {
            // Target lost, hide the stats card
            if (targetedPokemonId != null && !isFetching) {
                targetedPokemonId = null
                cardPokemonMetrics.animate().alpha(0f).setDuration(300)
                    .withEndAction { cardPokemonMetrics.visibility = View.GONE }.start()
            }
        }
    }

    private fun fetchPokemonDetails(id: Int) {
        isFetching = true
        
        tvArPokemonName.text = "Scanning..."
        tvArPokemonDescription.text = "Locking signatures..."
        tvArPokemonId.text = String.format("#%03d", id)
        tvTypePrimary.text = "???"
        tvTypePrimary.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(Color.GRAY)
        }
        
        cardPokemonMetrics.visibility = View.VISIBLE
        cardPokemonMetrics.alpha = 1f

        scope.launch {
            try {
                // Fetch dynamic real-time data from PokeAPI
                val response = withContext(Dispatchers.IO) {
                    URL("https://pokeapi.co/api/v2/pokemon/$id").readText()
                }
                val speciesResponse = withContext(Dispatchers.IO) {
                    URL("https://pokeapi.co/api/v2/pokemon-species/$id").readText()
                }

                val json = JSONObject(response)
                val speciesJson = JSONObject(speciesResponse)

                val name = json.getString("name").replaceFirstChar { it.uppercase() }
                
                val typesArray = json.getJSONArray("types")
                val primaryType = typesArray.getJSONObject(0).getJSONObject("type").getString("name").uppercase()
                
                val colorName = speciesJson.getJSONObject("color").getString("name")
                val hexColor = mapPokemonColorToHex(colorName)

                var description = "A mysterious Pokémon."
                val flavorTextEntries = speciesJson.getJSONArray("flavor_text_entries")
                for (i in 0 until flavorTextEntries.length()) {
                    val entry = flavorTextEntries.getJSONObject(i)
                    if (entry.getJSONObject("language").getString("name") == "en") {
                        // Clean up control characters from PokeAPI text
                        description = entry.getString("flavor_text").replace("\n", " ").replace("\u000c", " ")
                        break
                    }
                }

                val formattedId = String.format("#%03d", id)

                withContext(Dispatchers.Main) {
                    renderArEncounterSpecs(formattedId, name, primaryType, hexColor, description)
                    isFetching = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    tvArPokemonName.text = "Unknown Entity"
                    tvArPokemonDescription.text = "Failed to parse ecological parameters."
                    isFetching = false
                }
            }
        }
    }
    
    private fun mapPokemonColorToHex(color: String): String {
        return when(color.lowercase()) {
            "red" -> "#F08030"
            "blue" -> "#6890F0"
            "yellow" -> "#F8D030"
            "green" -> "#78C850"
            "black" -> "#705848"
            "brown" -> "#A040A0"
            "purple" -> "#A040A0"
            "gray" -> "#B8B8D0"
            "white" -> "#A8A878"
            "pink" -> "#F85888"
            else -> "#A8A878"
        }
    }

    // Implementing the CameraX Lifecycle provider logic
    private fun bindCameraXLifecycleEngine() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Initialize secure instance synchronization token
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Initialize structural configuration settings for the Viewfinder Preview object wrapper
            val preview = Preview.Builder()
                .build()
                .also {
                    // Feed streaming tracking buffers straight into the XML surface layer
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Bind configuration targeting paths to standard rear camera hardware module arrays
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Clear existing session registrations before executing re-attachment paths
                cameraProvider.unbindAll()

                // Register camera lifecycle bounds to this Activity host configuration layer
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to connect to hardware camera thread instance pipeline.", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this)) // Forces execution context safely onto the UI main thread
    }

    // Process and populate encounter layout panel components dynamically
    private fun renderArEncounterSpecs(
        id: String, name: String, type: String, colorHex: String, description: String
    ) {
        tvArPokemonId.text = id
        tvArPokemonName.text = name
        tvArPokemonDescription.text = description
        tvTypePrimary.text = type

        // Apply badge color styles programmatically via an updated shape layer drawable
        val dynamicColor = try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            Color.GRAY
        }
        val badgeBackgroundShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(dynamicColor)
        }
        tvTypePrimary.background = badgeBackgroundShape

        // Update Rarity Badge
        val rarityBadge = findViewById<TextView>(R.id.tvPokemonRarityBadge)
        val numericId = id.removePrefix("#").toIntOrNull() ?: 25
        val summary = com.pokemon.explorer.data.PokemonSummary(numericId, name, listOf(type))
        val rarity = summary.rarity
        rarityBadge.text = "${rarity.symbol} ${rarity.tierName}"
        val rarityBgShape = rarityBadge.background as? GradientDrawable
        rarityBgShape?.setColor(rarity.getColorInt())
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode) {
            if (hasCameraPermission()) {
                bindCameraXLifecycleEngine()
            } else {
                Toast.makeText(this, "Camera stream processing permissions are required for AR encounters.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingHandler.removeCallbacks(trackingRunnable)
        cameraExecutor.shutdown() // Tear down standalone asset executor allocations cleanly to eliminate tracking leak states
    }
}
