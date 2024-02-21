package com.example.philippinecurrencyidentifier

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture

// Camera Library
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics


import android.media.Image
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Vibrator

// Text to Speech library
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener



import android.util.Log
import android.util.TypedValue
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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toRect

// Import of machine learning model files
import com.example.philippinecurrencyidentifier.ml.DetectMetadata
import com.example.philippinecurrencyidentifier.ml.Coin
import com.example.philippinecurrencyidentifier.ml.Paper
import com.example.philippinecurrencyidentifier.ml.Validator

// TensorFlow Library
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.label.Category

import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlin.math.abs
import kotlin.math.roundToInt

import android.hardware.camera2.CameraAccessException
import org.checkerframework.checker.units.qual.C
import kotlin.math.log

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Paint
import android.util.Size

class ScanActivity : AppCompatActivity(), GestureDetector.OnGestureListener,
    TextToSpeech.OnInitListener {

    // Model
    // lateinit var labels:List<String>
    lateinit var model:DetectMetadata
//    lateinit var imageModel: AllMoneyMetadata
    lateinit var handler:Handler

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

        // model
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

                        if (score > 0.7) {
                            if (category != guideCurrent) {
                                guideNeedsToChange = true
                                guideCurrent = category
                            }
                        }


                        // ------ Check if MONEY or COIN is detected in camera
                        if((guideCurrent == "money" && score > 0.93) || (guideCurrent == "coin" && score > 0.85)) {
//                            Log.d("predict", "onSurfaceTextureUpdated: $location")

                            // ------ Temporarily stops detecting object
                            isObjectDetected = true
                            shouldThreadRun = false

                            val left = location.left
                            val top = location.top
                            val right = location.right
                            val bottom = location.bottom

                            val width = right - left
                            val height = bottom - top

//                            Log.d("predict", "onSurfaceTextureUpdated: width $width")
//                            Log.d("predict", "onSurfaceTextureUpdated: height $height")

                            val object_center_x = (left + right) / 2
                            val object_center_y = (top + bottom) / 2

                            val frameCenterX = 320 / 2
                            val frameCenterY = 320 / 2

                            val distance = Math.sqrt(Math.pow((object_center_x - frameCenterX.toDouble()), 2.0) + Math.pow((object_center_y - frameCenterY.toDouble()), 2.0))
                            val threshold = 70.0

                            // Object is not near the center
                            if(distance >= threshold) {
                                val result = provideMovementInstructions(object_center_x.toDouble(), object_center_y.toDouble(), frameCenterX.toDouble(), frameCenterY.toDouble())
                                // Changes the UI to original
                                scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)

                                // ------ quick vibration
                                vibrator.vibrate(300)

                                // ------ Stops the scanning audio
                                pauseAudio()

                                var ttsMessage = "Object is not near the center. $result"

                                val params = Bundle()
                                val uniqueId = "farAway"

                                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                                val speechListener = object : UtteranceProgressListener() {
                                    override fun onDone(utteranceId: String?) {
                                        isObjectDetected = false
                                        shouldThreadRun = true
                                        isThreadReturned = true

                                        playAudio()

                                        scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)

                                    }

                                    override fun onError(utteranceId: String?) {
                                    }

                                    override fun onStart(utteranceId: String?) {
                                    }
                                }
                                tts.setOnUtteranceProgressListener(speechListener)
                                tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params,uniqueId)

//                                Log.d("predict", "xx")
                                return@Thread

                            }

                            // Paper bill is too far away
                            if(guideCurrent == "money" && width < 130 ) {
                                var ttsMessage = "Paper bill is too far away from the camera. Please place the paper bill near the camera"

                                // Changes the UI to original
                                scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)

                                // ------ quick vibration
                                vibrator.vibrate(300)

                                // ------ Stops the scanning audio
                                pauseAudio()

                                val params = Bundle()
                                val uniqueId = "farAway"

                                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                                val speechListener = object : UtteranceProgressListener() {
                                    override fun onDone(utteranceId: String?) {
                                        isObjectDetected = false
                                        shouldThreadRun = true
                                        isThreadReturned = true

                                        playAudio()

                                        scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)

                                    }

                                    override fun onError(utteranceId: String?) {
                                    }

                                    override fun onStart(utteranceId: String?) {
                                    }
                                }
                                tts.setOnUtteranceProgressListener(speechListener)
                                tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params,uniqueId)

//                                Log.d("predict", "xx")
                                return@Thread
                            }

                            // Coin bill is too far away
                            if(guideCurrent == "coin" && (width < 160 || width > 215)) {
                                var ttsMessage = ""

                                if(width <130) {
                                    ttsMessage = "Coin is too far away from the camera. Please place the coin near the camera"
                                } else {
                                    ttsMessage = "Coin is too near from the camera. Please place the coin a little bit far from the camera"
                                }

                                // Changes the UI to original
                                scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)

                                // ------ quick vibration
                                vibrator.vibrate(300)

                                // ------ Stops the scanning audio
                                pauseAudio()

                                val params = Bundle()
                                val uniqueId = "farAway"

                                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                                val speechListener = object : UtteranceProgressListener() {
                                        override fun onDone(utteranceId: String?) {
                                            isObjectDetected = false
                                            shouldThreadRun = true
                                            isThreadReturned = true

                                            playAudio()

                                            scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)

                                        }

                                        override fun onError(utteranceId: String?) {
                                        }

                                        override fun onStart(utteranceId: String?) {
                                        }
                                    }
                                tts.setOnUtteranceProgressListener(speechListener)
                                tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params,uniqueId)

//                                Log.d("predict", "xx")
                                return@Thread
                            }
//                            Log.d("predict", "bleng")

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

                                    Log.d("predict", ": $label ${item.score}")

                                    if(label == "money" && score > 0.9 && score <= 1.0) {
                                        Log.d("predict", ": tite")
                                        predictedCategory = label
                                        break
                                    } else {
                                        if (label != "money") {
                                            maxProbability = score
                                            predictedCategory = label
                                        }
                                    }
                                }

                                Log.d("predict", ": out $predictedCategory")

                                when (predictedCategory) {
                                    "invalid" -> {
                                        // Changes the UI to original
                                        scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)

                                        // ------ quick vibration
                                        vibrator.vibrate(300)

                                        // ------ Stops the scanning audio
                                        pauseAudio()

                                        var ttsMessage = "Unrecognizable, try again."

                                        val params = Bundle()
                                        val uniqueId = "farAway"

                                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                                        val speechListener = object : UtteranceProgressListener() {
                                            override fun onDone(utteranceId: String?) {
                                                isObjectDetected = false
                                                shouldThreadRun = true
                                                isThreadReturned = true
                                                playAudio()

                                                scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)
                                            }

                                            override fun onError(utteranceId: String?) {
                                            }

                                            override fun onStart(utteranceId: String?) {
                                            }
                                        }
                                        tts.setOnUtteranceProgressListener(speechListener)
                                        tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params,uniqueId)

//                                            Log.d("predict", "xx")
                                        return@Thread
                                    }
                                    "foreign" -> {
                                        // Changes the UI to original
                                        scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)

                                        // ------ quick vibration
                                        vibrator.vibrate(300)

                                        // ------ Stops the scanning audio
                                        pauseAudio()

                                        var ttsMessage = "Detected foreign money. Try again"

                                        val params = Bundle()
                                        val uniqueId = "farAway"

                                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                                        val speechListener = object : UtteranceProgressListener() {
                                            override fun onDone(utteranceId: String?) {
                                                isObjectDetected = false
                                                shouldThreadRun = true
                                                isThreadReturned = true
                                                playAudio()

                                                scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)
                                            }

                                            override fun onError(utteranceId: String?) {
                                            }

                                            override fun onStart(utteranceId: String?) {
                                            }
                                        }
                                        tts.setOnUtteranceProgressListener(speechListener)
                                        tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params,uniqueId)

//                                            Log.d("predict", "xx")
                                        return@Thread
                                    }
//                                    "money" -> {
//                                        isObjectDetected = false
//                                        shouldThreadRun = true
//                                        isThreadReturned = true
//
//                                        return@Thread
//                                    }
                                }
                            }

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
                                    scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradrient_detectedmoney)

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
                                        // Changes the UI to original
                                        scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)

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

                                                scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)
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
                        scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)
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

        // causes error
        // model.close()

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

    private fun scanTutorial() {
        scanningTutTimer.schedule(object : TimerTask() {
            override fun run() {
                if(!isObjectDetected) {
                    isGoodToRUn = false

                    // Changes the UI to original
                    scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient_invalid)

                    // Stops the scanning audio
                    pauseAudio()

                    val params = Bundle()
                    val uniqueId = "scanProperly"
                    val ttsMessage = "To scan properly, " +
                            "1, This application can only detect 1 currency at a time. " +
                            "2, Ensure that the paper bill is unfolded and laid out flat vertically. " +
                            "3, Position the currency in the center of the surface. " +
                            "4, Keep the camera lens clean for optimal performance. Scanning in 3, 2, 1."

                    // Speech
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueId)
                    val speechListener = object : UtteranceProgressListener() {
                        override fun onDone(utteranceId: String?) {
                            // Plays the scanning audio
                            playAudio()

                            // Changes the UI to original
                            scanLayout.background = ContextCompat.getDrawable(this@ScanActivity, R.drawable.bg_gradient)

                            isGoodToRUn = true
                            scanTutorial()
                        }

                        override fun onError(utteranceId: String?) {
                        }

                        override fun onStart(utteranceId: String?) {
                        }
                    }
                    tts.setOnUtteranceProgressListener(speechListener)
                    tts.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, params, uniqueId)
                }

            }
        }, 60000)
    }
}