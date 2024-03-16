package com.example.philippinecurrencyidentifier

// Camera Library


// Text to Speech library


// Import of machine learning model files

// TensorFlow Library

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.example.philippinecurrencyidentifier.ml.Coin
import com.example.philippinecurrencyidentifier.ml.DetectMetadata
import com.example.philippinecurrencyidentifier.ml.Paper
import com.example.philippinecurrencyidentifier.ml.Validator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.label.Category
import java.text.DecimalFormat
import java.util.Locale
import java.util.Timer
import kotlin.math.abs
import kotlin.math.roundToInt

class ScanActivity : AppCompatActivity(), GestureDetector.OnGestureListener,
    TextToSpeech.OnInitListener {

    // Models
    lateinit var model:DetectMetadata
    // lateinit var imageModel: AllMoneyMetadata // Model for test purposes
    lateinit var handler:Handler

    // Image classification models
    lateinit var paperModel: Paper
    lateinit var coinModel: Coin
    lateinit var validatorModel: Validator

    // Camera
    lateinit var cameraManager: CameraManager
    lateinit var textureView:TextureView
    lateinit var cameraDevice: CameraDevice
    lateinit var imageProcessor: ImageProcessor
    lateinit var imageProcessorImage: ImageProcessor
    lateinit var bitmap: Bitmap

    // Timer
    val scanningTutTimer = Timer()

    // Gesture
    lateinit var gestureDetector: GestureDetector
    var x2:Float = 0.0f
    var x1:Float = 0.0f
    var y2:Float = 0.0f
    var y1:Float = 0.0f

    // Vibrator
    lateinit var vibrator: Vibrator

    // Text to Speech
    lateinit var tts: TextToSpeech

    // User variables
    var isObjectDetected:Boolean = false

    // for thread returned
    var isThreadReturned:Boolean = true

    // Checker
    var isGoodToRUn:Boolean = true

    // touch event, to avoid multiple gesture input
    var toRunOnceInTouchEvent = false
    var detectedMoney = 0

    @Volatile
    private var shouldThreadRun = true

    // Scan guide
    var guideNeedsToChange = false
    var guideCurrent = "money"

    // Element
    lateinit var tvTotalMoney:TextView
    lateinit var tvScanResult:TextView
    lateinit var cvGestureResult: CardView
    lateinit var tvGestureProgress: TextView
    lateinit var cvDetectResult: CardView
    lateinit var ivScanGuide: ImageView
    lateinit var scanLayout: ConstraintLayout
    lateinit var mediaPlayer: MediaPlayer

    // TEMPORARY -- FOR TESTING PURPOSES
    // TEMPORARY -- FOR TESTING PURPOSES
    // TEMPORARY -- FOR TESTING PURPOSES
    // TEMPORARY -- FOR TESTING PURPOSES
    lateinit var cvTestBitmap : ImageView
    lateinit var imageTest: Bitmap
    var runOnceTest = false

    var colorNormal = Color.argb(255, 255, 255, 200)
    var colorInvalid = Color.argb(255, 255, 200, 200)
    var colorValid = Color.argb(255, 153, 255, 153)

    // ---------
    companion object {
        const val MIN_DISTANCE = 150
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        get_permission()

        // Initialize
        gestureDetector = GestureDetector(this, this)
        tts = TextToSpeech(this, this)
        mediaPlayer = MediaPlayer.create(this, R.raw.background_scanning)
        vibrator = applicationContext.getSystemService(VIBRATOR_SERVICE) as Vibrator

        // Element
        tvTotalMoney = findViewById(R.id.tvTotalMoney)
        textureView = findViewById(R.id.textureView)
        tvScanResult = findViewById(R.id.tvScanResult)
        cvGestureResult = findViewById(R.id.cvGestureResult)
        tvGestureProgress = findViewById(R.id.tvGestureProgress)
        scanLayout = findViewById(R.id.scanLayout)
        cvDetectResult = findViewById(R.id.cvDetectResult)
        ivScanGuide = findViewById(R.id.ivScanGuide)

        // TEMPORARY -- FOR TESTING PURPOSES
        // TEMPORARY -- FOR TESTING PURPOSES
        // TEMPORARY -- FOR TESTING PURPOSES
        // TEMPORARY -- FOR TESTING PURPOSES
//        cvTestBitmap = findViewById(R.id.cvTestBitmap)
        // TEMPORARY -- FOR TESTING PURPOSES

        // Model
        // Object detection pixel
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(320, 320, ResizeOp.ResizeMethod.BILINEAR)).build()
        // Image classification pixel
        imageProcessorImage = ImageProcessor.Builder().add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR)).build()

        // models
        model = DetectMetadata.newInstance(this)
        paperModel = Paper.newInstance(this)
        coinModel = Coin.newInstance(this)
        validatorModel = Validator.newInstance((this))

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        // Camera
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCameraWithFlashlight()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            // Runs everytime camera moves
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

                // If there is no object detected, run the IF
                // if object is not detected and thread-returned
                if (!isObjectDetected && isThreadReturned && isGoodToRUn && shouldThreadRun) {
                    Thread {

                        isThreadReturned = false

                        if(isObjectDetected) {
                            scanningTutTimer.cancel();  // Terminates this timer, discarding any currently scheduled tasks.
                            scanningTutTimer.purge();   // Removes all cancelled tasks from this timer's task queue.
                            return@Thread
                        }

                        // ------ Bitmap is from the camera == IMAGE
                        bitmap = textureView.bitmap!!
                        var image = TensorImage.fromBitmap(bitmap)
                        image = imageProcessor.process(image)

                        // ------ Runs model inference and gets result.
                        val outputs = model.process(image)
                        val detectionResult = outputs.detectionResultList[0]

                        // ------ Gets result from DetectionResult.
                        val score = detectionResult.scoreAsFloat;
                        val category = detectionResult.categoryAsString;
                        val location = detectionResult.locationAsRectF;

                        // Changes the UI (guideline) weather to show coin or paper template
                        if (score > 0.7) {
                            if (category != guideCurrent) {
                                guideNeedsToChange = true
                                guideCurrent = category
                            }
                        }

                        // ------ Check if MONEY or COIN is detected in camera + conditions
                        if((guideCurrent == "money" && score > 0.93) || (guideCurrent == "coin" && score > 0.85)) {
                            // Log.d("predict", "onSurfaceTextureUpdated: $location")

                            // ------ Temporarily stops detecting object
                            isObjectDetected = true
                            shouldThreadRun = false

                            val width = location.right - location.left
                            val object_center_x = (location.left + location.right) / 2
                            val object_center_y = (location.top + location.bottom) / 2

                            val frameCenter = 320 / 2

                            val distance = Math.sqrt(Math.pow((object_center_x - frameCenter.toDouble()), 2.0) + Math.pow((object_center_y - frameCenter.toDouble()), 2.0))
                            val threshold = 70.0

                            // Object is not near the center
                            if(distance >= threshold) {
                                val result = provideMovementInstructions(object_center_x.toDouble(), object_center_y.toDouble(), frameCenter.toDouble(), frameCenter.toDouble())
                                announceMessage("Object is not near the center. $result", "farAway")
                                return@Thread
                            }

                            // Currency proximity validation
                            when (guideCurrent) {
                                "money" -> if (width < 130) {
                                    announceMessage("Paper bill is too far away from the camera. Please place the paper bill near the camera", "farAway")
                                    return@Thread
                                }
                                "coin" -> if (width < 160 || width > 215) {

                                    val coinMessage = if (width < 160) {
                                        "Coin is too far away from the camera. Please place the coin near the camera"
                                    } else {
                                        "Coin is too far away from the camera. Please place the coin near the camera"
                                    }

                                    announceMessage(coinMessage, "farAway")
                                    return@Thread
                                }
                            }

                            // Runs validation model whether the currency is valid or not
                            var imageClassification = TensorImage.fromBitmap(bitmap)
                            imageClassification = imageProcessorImage.process(imageClassification)

                            val validatorOutput = validatorModel.process(imageClassification)
                            val validityProbability = validatorOutput.probabilityAsCategoryList.toTypedArray()

                            var maxProbability = 0.0
                            var predictedCategory = ""

                            for (item in validityProbability) {
                                if (item.score > maxProbability) {
                                    maxProbability = item.score.toDouble()
                                    predictedCategory = category
                                }
                            }
//
                            if(guideCurrent == "money") {

                                for (item in validityProbability) {
                                    var label = item.label
                                    var score = (item.score * 10000).roundToInt().toDouble() / 10000

                                    // Log.d("predict", ": $label ${item.score}")

                                    if(label == "money" && score > 0.9 && score <= 1.0) {
                                        predictedCategory = label
                                        break
                                    } else {
                                        if (label != "money") {
                                            maxProbability = score
                                            predictedCategory = label
                                        }
                                    }
                                }

                                // Log.d("predict", ": out $predictedCategory")

                                when (predictedCategory) {
                                    "invalid" -> {
                                        announceMessage("Unrecognizable, try again.", "farAway")
                                        return@Thread
                                    }
                                    "foreign" -> {
                                        announceMessage("Detected foreign money. Try again", "farAway")
                                        return@Thread
                                    }
                                }
                            }

                            // To run PAPER or Coin model
                            var paperOutput: Paper.Outputs? = null
                            var coinOutput: Coin.Outputs? = null
                            var probability: Array<Category>? = null

                            // To choose which image classification model to use based on guidecurrent variable value
                            // paper model or coin model
                            if (guideCurrent == "money") {
                                paperOutput = paperModel.process(imageClassification)
                                probability = paperOutput.probabilityAsCategoryList.toTypedArray()
                            } else {
                                coinOutput = coinModel.process(imageClassification)
                                probability = coinOutput.probabilityAsCategoryList.toTypedArray()
                            }

                            var imageClassifierSuccess = false


                            for (item in probability) {
                                // label and scoring
                                var label = item.label
                                var score = (item.score * 10000).roundToInt().toDouble() / 10000

//                                Log.d("predict", ": $label ${item.score}")

                                if (score > 0.90 && score < 1.2) {

                                    imageClassifierSuccess = true

                                    // TEMPORARY -- FOR TESTING PURPOSES
                                    // TEMPORARY -- FOR TESTING PURPOSES
                                    // TEMPORARY -- FOR TESTING PURPOSES
                                    // TEMPORARY -- FOR TESTING PURPOSES
//                                    runOnceTest = true
//                                    imageTest = bitmap
                                    // TEMPORARY -- FOR TESTING PURPOSES
                                    // TEMPORARY -- FOR TESTING PURPOSES
                                    // TEMPORARY -- FOR TESTING PURPOSES
                                    // TEMPORARY -- FOR TESTING PURPOSES


                                    // ------ quick vibration
                                    vibrator.vibrate(300)

                                    // ------ Stops the scanning audio
                                    pauseAudio()

                                    // ------ Changes the UI when money is detected
                                    // Change background based on android version
                                    if (Build.VERSION.SDK_INT >= 30) {
                                        scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradrient_detectedmoney)
                                    } else {
                                        scanLayout.setBackgroundColor(colorValid);
                                    }

                                    var billVersion = ""

                                    when (label) {
                                        "twenty" -> {
                                            detectedMoney = 20
                                        }
                                        "fifty" -> {
                                            detectedMoney = 50
                                        }
                                        "onehundred" -> {
                                            detectedMoney = 100
                                        }
                                        "twohundred" -> {
                                            detectedMoney = 200
                                        }
                                        "fivehundred" -> {
                                            detectedMoney = 500
                                        }
                                        "onethousand" -> {
                                            billVersion = "Old version of"
                                            detectedMoney = 1000
                                        }
                                        "onethousandnew" -> {
                                            billVersion = "New version of"
                                            detectedMoney = 1000
                                        }
                                        "onecoinnew" -> {
                                            billVersion = "New version of"
                                            detectedMoney = 1
                                        }
                                        "onecoinold" -> {
                                            billVersion = "Old version of"
                                            detectedMoney = 1
                                        }
                                        "fivecoinold" -> {
                                            billVersion = "Old version of"
                                            detectedMoney = 5
                                        }
                                        "fivecoinnew" -> {
                                            billVersion = "New version of"
                                            detectedMoney = 5
                                        }
                                        "tencoinold" -> {
                                            billVersion = "Old version of"
                                            detectedMoney = 10
                                        }
                                        "tencoinnew" -> {
                                            billVersion = "New version of"
                                            detectedMoney = 10
                                        }
                                        "twentycoinnew" -> {
                                            detectedMoney = 20
                                        }
                                    }

                                    var ttsMessage = ""

                                    if (label != "invalid" && label != "cointail") {
                                        ttsMessage = "$billVersion $detectedMoney pesos detected..."
                                        tts.speak(ttsMessage, TextToSpeech.QUEUE_ADD, null, null)
                                        tts.speak("Swipe left to skip, or swipe right to add.", TextToSpeech.QUEUE_ADD, null, null)

                                        runOnUiThread {
                                            // ------ Show detect box
                                            cvDetectResult.visibility = View.VISIBLE
                                            tvScanResult.text = "${detectedMoney.toString()}"
                                        }

                                        toRunOnceInTouchEvent = true

                                        // ------ Stops the loop
                                    }
                                    else {
                                        // Change background based on android version
                                        if (Build.VERSION.SDK_INT >= 30) {
                                            scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)
                                        } else {
                                            scanLayout.setBackgroundColor(colorInvalid);
                                        }

                                        if (label == "cointail") {
                                            ttsMessage = "Flip the coin that you are currently holding."
                                        } else {
                                            ttsMessage = "Detected invalid currency. Please try again."
                                        }

                                        val params = Bundle()
                                        val uniqueId = "invalidDetected"

                                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                                        val speechListener = object : UtteranceProgressListener() {
                                            override fun onDone(utteranceId: String?) {
                                                isObjectDetected = false
                                                shouldThreadRun = true
                                                playAudio()

                                                // Change background based on android version
                                                if (Build.VERSION.SDK_INT >= 30) {
                                                    scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)
                                                } else {
                                                    scanLayout.setBackgroundColor(colorNormal);
                                                }
                                            }

                                            override fun onError(utteranceId: String?) {
                                            }

                                            override fun onStart(utteranceId: String?) {
                                            }
                                        }
                                        tts.setOnUtteranceProgressListener(speechListener)
                                        tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params,uniqueId)
                                    }

                                    break
                                }
                            }

                            if (!imageClassifierSuccess) {
                                shouldThreadRun = true
                                isObjectDetected = false
                            }
                        }

                        isThreadReturned = true
                    }.start()
                }

                // Changing the GUIDELINE "Coin" or "Paper" ui
                if (guideNeedsToChange) {
                    if (guideCurrent == "money") {
                        ivScanGuide.setImageResource(R.drawable.paper_guideline);
                    } else {
                        ivScanGuide.setImageResource(R.drawable.coin_guideline);
                    }
                    guideNeedsToChange = false
                }

                // TEMPORARY -- FOR TESTING PURPOSES
                // TEMPORARY -- FOR TESTING PURPOSES
                // TEMPORARY -- FOR TESTING PURPOSES
                // TEMPORARY -- FOR TESTING PURPOSES
//                if (runOnceTest) {
//                    cvTestBitmap.setImageBitmap(imageTest);
//                }
                // TEMPORARY -- FOR TESTING PURPOSES
                // TEMPORARY -- FOR TESTING PURPOSES
                // TEMPORARY -- FOR TESTING PURPOSES
                // TEMPORARY -- FOR TESTING PURPOSES

            }
        }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        // When CAMERA cannot detect anything within 60 seconds
        // scanTutorial()
    }

    // Function to handle the common operations for speaking text and updating UI.
    private fun announceMessage(ttsMessage: String, uniqueId: String) {

//        Log.d("tite", Build.VERSION.SDK_INT.toString())
//        Log.d("tite", Build.VERSION_CODES.JELLY_BEAN.toString())

        // Change background based on android version
        if (Build.VERSION.SDK_INT >= 30) {
            scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)
        } else {
            scanLayout.setBackgroundColor(colorInvalid);
        }

        vibrator.vibrate(300) // Quick vibration
        pauseAudio() // Stops the scanning audio

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                isObjectDetected = false
                shouldThreadRun = true
                isThreadReturned = true
                playAudio()

                // Change background based on android version
                if (Build.VERSION.SDK_INT >= 30) {
                    scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)
                } else {
                    scanLayout.setBackgroundColor(colorNormal);
                }
            }

            override fun onError(utteranceId: String?) {}
            override fun onStart(utteranceId: String?) {}
        })

        tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params, uniqueId)
    }

    fun provideMovementInstructions(objectCenterX: Double, objectCenterY: Double, frameCenterX: Double, frameCenterY: Double): String {
        val xOffset = objectCenterX - frameCenterX
        val yOffset = objectCenterY - frameCenterY

        var result = ""

        if (Math.abs(xOffset) > Math.abs(yOffset)) {
            // Horizontal movement is more significant
            if (xOffset > 0) {
                result = "Move the object to the left."
            } else {
                result = "Move the object to the right."
            }
        } else {
            // Vertical movement is more significant
            if (yOffset > 0) {
                result = "Move the object upward."
            } else {
                result = "Move the object downward."
            }
        }

        return result
    }

    var cameraCaptureSession: CameraCaptureSession? = null

    // CAMERA
    @SuppressLint("MissingPermission")
    fun openCameraWithFlashlight() {
        //var cameraDevice: CameraDevice? = null
        //var cameraCaptureSession: CameraCaptureSession? = null

        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                val surfaceTexture = textureView.surfaceTexture

                // Get the camera characteristics
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(camera.id)

                // Get the preview size supported by the camera
                val previewSize = getOptimalPreviewSize(cameraCharacteristics, textureView.width, textureView.height)

                // Set the default buffer size of the SurfaceTexture based on the preview size
                if (surfaceTexture != null) {
                    surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                }

                val surface = Surface(surfaceTexture)

                // Create a capture request with FLASH_MODE set to TORCH
                val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                captureRequestBuilder.addTarget(surface)

                camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        // Set the repeating request with the flashlight enabled
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // Handle configuration failure
                    }
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraDevice?.close()
                cameraCaptureSession?.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraDevice?.close()
                cameraCaptureSession?.close()
            }
        }, handler)
    }

    private fun closeCamera() {
        // Check if the camera is initialized
        if (cameraDevice != null) {
            // Stop the repeating request (turn off the flashlight)
            cameraCaptureSession?.stopRepeating()
            cameraCaptureSession?.abortCaptures()

            // Close the camera and capture session
            cameraDevice?.close()
            cameraCaptureSession?.close()

            // Reset references
            //cameraDevice = null
            cameraCaptureSession = null
        }
    }

    private fun getOptimalPreviewSize(characteristics: CameraCharacteristics, width: Int, height: Int): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val choices = map?.getOutputSizes(SurfaceTexture::class.java)
        val surfaceLonger = Math.max(width, height)
        val surfaceShorter = Math.min(width, height)
        val aspectRatio = surfaceLonger.toDouble() / surfaceShorter.toDouble()

        var bestSize: Size = choices?.get(0) ?: Size(width, height)
        var currentBestDiff = Double.MAX_VALUE

        for (size in choices.orEmpty()) {
            val sizeLonger = Math.max(size.width, size.height)
            val sizeShorter = Math.min(size.width, size.height)
            val sizeAspectRatio = sizeLonger.toDouble() / sizeShorter.toDouble()

            val score = Math.abs(sizeAspectRatio - aspectRatio)
            if (score < currentBestDiff) {
                currentBestDiff = score
                bestSize = size
            }
        }

        return bestSize
    }



    fun get_permission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        }
    }

    // GESTURE
    override fun onTouchEvent(event: MotionEvent?): Boolean {

        if(isObjectDetected and toRunOnceInTouchEvent) {

            if (event != null) {
                gestureDetector.onTouchEvent(event)
            }

            when (event?.action) {

                // start of swipe
                0 -> {
                    x1 = event.x
                    y1 = event.y
                }
                // end ef swipe
                1 -> {
                    x2 = event.x
                    y2 = event.y

                    // Calculation
                    val valueX:Float = x2-x1
                    val valueY:Float = y2-y1

                    if(abs(valueX) > MIN_DISTANCE) {

                        val params = Bundle()
                        var ttsMessage = ""
                        var uniqueId = ""

                        // Right
                        if(x2 > x1) {
                            toRunOnceInTouchEvent = false

                            tvGestureProgress.text = "ADDING"
                            cvGestureResult.visibility = View.VISIBLE

                            // run gesture
                            val totalMoney = calculateTotalMoney(removeDecimal(tvTotalMoney.text.toString()), detectedMoney)
                            changeTotalMoney(totalMoney)

                            ttsMessage = "Swipe right initiated. Adding $detectedMoney pesos. Total money is $totalMoney pesos"
                            uniqueId = "uniqueAdding"

                            Toast.makeText(this, "Adding", Toast.LENGTH_SHORT).show()
                        }
                        // left
                        else {
                            toRunOnceInTouchEvent = false

                            tvGestureProgress.text = "SKIPPING"
                            cvGestureResult.visibility = View.VISIBLE

                            val totalMoney = tvTotalMoney.text.toString()

                            ttsMessage = "Swipe left initiated. Skipping $detectedMoney pesos. Total money is $totalMoney pesos"
                            uniqueId = "uniqueSkipping"

                            Toast.makeText(this, "Skipping", Toast.LENGTH_SHORT).show()
                        }

                        // Speech
                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                        val speechListener = object : UtteranceProgressListener() {
                            override fun onDone(utteranceId: String?) {
                                isObjectDetected = false
                                shouldThreadRun = true
                                cvGestureResult.visibility = View.INVISIBLE

                                // Plays the scanning audio
                                playAudio()

                                // When CAMERA cannot detect anything within 60 seconds
                                // scanTutorial()
                            }

                            override fun onError(utteranceId: String?) {
                            }

                            override fun onStart(utteranceId: String?) {
                            }
                        }
                        tts.setOnUtteranceProgressListener(speechListener)
                        tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params,uniqueId)

                        // SET TO ITS ORIGINAL UI
                        // FOR SCANNING
                        cvDetectResult.visibility = View.INVISIBLE
                        tvScanResult.text = "0"

                        // Changes the UI to original
                        // Change background based on android version
                        if (Build.VERSION.SDK_INT >= 30) {
                            scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)
                        } else {
                            scanLayout.setBackgroundColor(colorNormal);
                        }
                    }
                }
            }
        }

        return super.onTouchEvent(event)
    }

    // not needed
    override fun onDown(p0: MotionEvent): Boolean {
        //TODO("Not yet implemented")
        return false
    }

    override fun onShowPress(p0: MotionEvent) {
        //TODO("Not yet implemented")
    }

    override fun onSingleTapUp(p0: MotionEvent): Boolean {
        // TODO("Not yet implemented")
        return false
    }

    override fun onScroll(p0: MotionEvent, p1: MotionEvent, p2: Float, p3: Float): Boolean {
        // TODO("Not yet implemented")
        return false
    }

    override fun onLongPress(p0: MotionEvent) {
        // TODO("Not yet implemented")
    }

    override fun onFling(p0: MotionEvent, p1: MotionEvent, p2: Float, p3: Float): Boolean {
        // TODO("Not yet implemented")
        return false
    }

    override fun onDestroy() {
        super.onDestroy()

        closeCamera()

        scanningTutTimer.cancel();  // Terminates this timer, discarding any currently scheduled tasks.
        scanningTutTimer.purge();

        // Shut down TTS
        if(tts != null){
            tts.stop()
            tts.shutdown();
        }

        // cameraDevice.close()
        // cameraDevice?.close()

        if(this::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
    }

    override fun onStop() {
        super.onStop()

        closeCamera()

        if(mediaPlayer.isPlaying) {
            pauseAudio()
        }
    }

    override fun onResume() {
        super.onResume()

        // Plays the scanning audio
        playAudio()
    }

    override fun onInit(p0: Int) {
        if (p0 == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(1.0f)
        }
    }

    // PERSONAL FUNCTION
    // PERSONAL FUNCTION
    // PERSONAL FUNCTION
    // PERSONAL FUNCTION
    // PERSONAL FUNCTION

    private fun calculateTotalMoney(currentMoney: Int, detectedMoney: Int): Int {
        return currentMoney + detectedMoney
    }

    private fun changeTotalMoney(money: Int) {
        tvTotalMoney.text = addDecimal(money)
    }

    private fun removeDecimal(number: String):Int {
        return number.replace(",", "").toInt()
    }

    private fun addDecimal(number: Int):String {
        val numberFormat  = DecimalFormat("#,###")
        return numberFormat.format(number)
    }

    private fun playAudio() {
        // Plays the scanning audio
        mediaPlayer.isLooping = true
        mediaPlayer.start()
    }

    private fun pauseAudio() {
        // Pauses audio and reset to 0
        mediaPlayer.isLooping = false
        mediaPlayer.pause()
        mediaPlayer.seekTo(0)
    }
}