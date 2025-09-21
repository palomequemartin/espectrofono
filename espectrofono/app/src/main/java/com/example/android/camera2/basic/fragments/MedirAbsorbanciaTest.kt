package com.example.android.camera2.basic.fragments


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Camera.Parameters.FOCUS_MODE_FIXED
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.rotationMatrix
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.computeExifOrientation
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.basic.CameraActivity
import com.example.android.camera2.basic.R
import com.example.android.camera2.basic.CalibrationData
import com.example.calibrarlongituddeonda.Autorotar
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*
import kotlin.properties.Delegates
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.*
import android.net.Uri
import android.text.Layout
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toAdaptiveIcon
import androidx.core.graphics.red
import androidx.core.view.isVisible
//import kotlinx.android.synthetic.main.absorbancia_calib.*
//import kotlinx.android.synthetic.main.fragment_camera.capture_button
//import kotlinx.android.synthetic.main.fragment_medir_absorbancia_test.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import java.io.*
import java.lang.Math.pow
import java.lang.Runnable
import java.nio.ByteBuffer

class MedirAbsorbanciaTest : Fragment() {

    /** AndroidX navigation arguments */
    private val args: MedirAbsorbanciaTestArgs by navArgs()

    // Variables para el sistema de calibración
    private var isCalibrationMode: Boolean = false

    private lateinit var canvasParaEditar: Canvas //para enchular los bitmaps hay que hacer esto
    private lateinit var poneleColor: Paint //para ponerle colores
    private lateinit var  myBitmap: Bitmap
    private lateinit var  prueba: Bitmap
    private var n by Delegates.notNull<Int>()
    private var m by Delegates.notNull<Int>()
    private var activarBotonContinuar : Boolean = false

    private val listaConMetrica = mutableListOf<Float>()
    lateinit var listaFinalIntensidadesRed : MutableList<Int>
    lateinit var listaFinalIntensidadesGreen : MutableList<Int>
    lateinit var listaFinalIntensidadesBlue : MutableList<Int>

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            overlay.postDelayed({
                // Remove white flash animation
                overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** Where the camera preview is displayed */
    private lateinit var viewFinder: AutoFitSurfaceView

    /** Overlay on top of the camera preview */
    private lateinit var overlay: View

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var picturesSession: CameraCaptureSession
    private lateinit var previewSession: CameraCaptureSession

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    private lateinit var captureRequestStillCapture: CaptureRequest.Builder

    private lateinit var captureRequestPreview: CaptureRequest.Builder

    lateinit var buffer : ByteBuffer
    lateinit var bytes : ByteArray

    private var progreso : Int = 0
    private var mostrarProgreso : Boolean = false
    private val testMode = false


    private var blueOrder1 = mutableListOf<Float>()
    private lateinit var blueOrder1IndexList : IntArray
    private var blueOrder2 = mutableListOf<Float>()

    private var redOrder1 = mutableListOf<Float>()
    private lateinit var redOrder1IndexList : IntArray
    private var redOrder2 = mutableListOf<Float>()


    private var greenOrder1 = mutableListOf<Float>()
    private lateinit var greenOrder1IndexList : IntArray
    private var greenOrder2 = mutableListOf<Float>()


    private var listaIndices = listOf<Int>()

    private var posicionEnXOrdenCero by Delegates.notNull<Int>()

    private var blueX1 : Int = 0

    private var modoMonocromatico = false

    private var previewExposureTime by Delegates.notNull<Long>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_medir_absorbancia_test, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val exposureTime: Long = args.exposureTime
        val numberOfPictures: Int = args.numberOfPictures
        val sensitivity: Int = args.sensitivity
        val focalDistance: Float = args.focalDistance // Modificación 06/05/24

        // Detectar si estamos en modo calibración - obtener del Bundle
        isCalibrationMode = arguments?.getBoolean("isCalibrationMode", false) ?: false

        // Debug: Mostrar qué modo se detectó
        android.util.Log.d("DEBUG_CALIBRACION", "isCalibrationMode detectado: $isCalibrationMode")
        android.util.Log.d("DEBUG_CALIBRACION", "Bundle completo: ${arguments?.toString()}")

        // Cargar datos de calibración existentes
        CalibrationData.loadCalibrationData(requireContext())

        /** --------- Interface elements -----------**/
        super.onViewCreated(view, savedInstanceState)
        overlay = view.findViewById(R.id.overlay)
        viewFinder = view.findViewById(R.id.view_finder)
        var botonContinuar : ImageButton = view.findViewById(R.id.botonContinuar)
        val barraProgreso : ProgressBar = view.findViewById(R.id.progressBar)
        barraProgreso.isVisible = false
        val textoProgreso : TextView = view.findViewById(R.id.textoProgreso)
        textoProgreso.isVisible=false
        val captureButton : ImageButton = view.findViewById(R.id.capture_button)
        /**--------------------------------------**/


        /** --------- Set preview size -----------**/
        val previewSize = getPreviewOutputSize(viewFinder.display, characteristics, SurfaceHolder::class.java)
        viewFinder.setAspectRatio(previewSize.width, previewSize.height)
        /**--------------------------------------**/

        /** --------- Set layout orientation -----------**/
        val layoutmedirabstest : View = view.findViewById(R.id.layoutmedirabstest)
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer {
                    orientation -> layoutmedirabstest.rotation = orientationFunction(orientation).toFloat()
            })
        }
        /**-------------Open camera and first preview session-------------**/
        //var previewExposureTime = 12*1e6.toLong()
        previewExposureTime = exposureTime //tmax
        openDevice(previewExposureTime,sensitivity, focalDistance) // Modificación 06/05/24

        /** --------- Botones -----------**/
        botonContinuar.setOnClickListener {
            if (activarBotonContinuar) {
                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    MedirAbsorbanciaTestDirections.actionMedirAbsorbanciaTestToCaptura(
                        prueba,args.cameraId,blueOrder1.toFloatArray(),
                        redOrder1.toFloatArray(), greenOrder1.toFloatArray(),
                            blueOrder2.toFloatArray(), redOrder2.toFloatArray(),
                            greenOrder2.toFloatArray(),listaConMetrica.toFloatArray(),
                            posicionEnXOrdenCero,blueX1
                    )
                )
            }
            else {
                Toast.makeText(activity,"Tome las fotos primero",Toast.LENGTH_SHORT).show()
            }
        }

        captureButton.setOnClickListener {
                script(barraProgreso,textoProgreso,captureButton,exposureTime,numberOfPictures,sensitivity,focalDistance) // Modificación 06/05/24
        }

    }
    // Modificación 06/05/24 :
    private fun script(barraProgreso:ProgressBar,textoProgreso:TextView,captureButton : ImageButton,exposureTime: Long,numberOfPictures: Int,sensitivity: Int, focalDistance: Float) = lifecycleScope.launch(Dispatchers.IO){

        withContext (Dispatchers.Main) {
            captureButton.isEnabled = false
            barraProgreso.isVisible = true
            barraProgreso.isIndeterminate = true
            textoProgreso.isVisible = true
        }

        val tmax = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: 0
        val tmin = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.lower ?: 0
        val dimensions = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(args.pixelFormat)
        val h = dimensions[0].height
        val w = dimensions[0].width

        var autorotar : Autorotar
        var m : DoubleArray = DoubleArray(0)
        var listaMaximosX : MutableList<Double> = MutableList<Double>(0,{i: Int-> 0.0})
        var listaMaximosY : MutableList<Double> = MutableList<Double>(0,{i: Int-> 0.0})
        var tita = 0f
        var titaRad : Double

        // Modificación 06/05/24 :
        val focalDistanceCm: Float = 1f/focalDistance*100f //Paso el valor en cm al que lee el programa
        //
        var listaXRectaAjuste = mutableListOf<Int>()
        val listaYRectaAjuste = mutableListOf<Int>()

        //val radioPromedios = 5f*h.toFloat()*w.toFloat()/12e6f
        val radioPromedios = 0


        /**--------------------------------------------------------------------------**/
        /** LÓGICA DE CALIBRACIÓN VS MODO NORMAL    **/
        /**--------------------------------------------------------------------------**/

        if (isCalibrationMode) {
            // =============== MODO CALIBRACIÓN ===============
            withContext(Dispatchers.Main){
                textoProgreso.text = "MODO CALIBRACIÓN\nDetectando recta"
            }

            // Encontrar la recta como siempre en modo calibración
            imageReader = ImageReader.newInstance(w, h, args.pixelFormat, 1)

            previewSession.close()
            delay(500L)
            makePreviewSession(exposureTime,sensitivity,focalDistance)
            delay(500L)
            previewSession.close()
            delay(500L)
            picturesSession = createCaptureSession(camera, listOf(imageReader.surface), cameraHandler)

            takePhoto(tmax,sensitivity,focalDistanceCm,picturesSession).use{ result ->
                buffer = result.image.planes[0].buffer
                bytes = ByteArray(buffer.remaining()).apply {buffer.get(this)}
                myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                result.image.close()
                picturesSession.close()
                makePreviewSession(exposureTime,sensitivity,focalDistance)

                if (testMode==true) {
                    m = doubleArrayOf((h/2f).toDouble(),0.toDouble())
                }
                autorotar = Autorotar(myBitmap)

                try {
                    autorotar.encontrarRecta()
                    m = autorotar.m!!
                    listaMaximosX = autorotar.listaMaximosX!!
                    listaMaximosY = autorotar.listaMaximosY!!
                    posicionEnXOrdenCero = autorotar.posicionEnXOrdenCero?: 0
                    tita = autorotar.tita!!.toFloat()
                    titaRad = tita / 180 * kotlin.math.PI

                    // Construir listaXRectaAjuste y listaYRectaAjuste
                    for (k in radioPromedios.toInt() until posicionEnXOrdenCero-200) {
                        listaXRectaAjuste.add(k)
                        listaYRectaAjuste.add((k * m[1] + m[0]).toInt())
                    }

                    // Crear bitmap de prueba con la recta dibujada
                    prueba = myBitmap.copy(myBitmap.getConfig(), true)
                    for (i in 0 until prueba.width) {
                        var j = (m[1] * i + m[0]).toInt()
                        prueba.setPixel(i, j, Color.rgb(255, 255, 255))
                    }
                    for (maxIdx in 0 until listaMaximosX.size) {
                        var i = listaMaximosX[maxIdx].toInt()
                        var j = listaMaximosY[maxIdx].toInt()
                        for (k in -5..5) {
                            for (l in -5..5) {
                                prueba.setPixel(i + k, j + l, Color.rgb(255, 0, 0))
                            }
                        }
                    }
                    var i = posicionEnXOrdenCero
                    var j = (m[1] * i + m[0]).toInt()
                    for (k in -10..10) {
                        for (l in -10..10) {
                            prueba.setPixel(i + k, j + l, Color.rgb(255, 0, 255))
                        }
                    }

                    // Encontrar blueX1 para calibración
                    withContext(Dispatchers.Main){
                        textoProgreso.text = "CALIBRACIÓN\nBuscando orden difractivo"
                    }

                    // Buscar primer orden de difracción para calibración
                    var n0 = 1
                    val tolerancia = 1
                    val normalizacion = (2*radioPromedios + 1).toDouble().pow(2.0).toFloat()
                    var nB = 0
                    var Q = 0

                    while (n0 <= tolerancia) {
                        var blueTest = mutableListOf<Int>()
                        previewSession.close()
                        delay(500L)
                        picturesSession = createCaptureSession(camera, listOf(imageReader.surface), cameraHandler)
                        takePhoto(previewExposureTime,sensitivity,focalDistanceCm,picturesSession).use{ result ->
                            buffer = result.image.planes[0].buffer
                            bytes = ByteArray(buffer.remaining()).apply {buffer.get(this)}
                            myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            result.image.close()
                            picturesSession.close()
                            makePreviewSession(previewExposureTime,sensitivity,focalDistance)

                            for (n in 0 until listaXRectaAjuste.lastIndex) {
                                var i0 = listaXRectaAjuste[n]
                                var j0 = listaYRectaAjuste[n]
                                var b = 0f
                                for (i in i0 - radioPromedios.toInt()..i0 + radioPromedios.toInt()) {
                                    for (j in j0 - radioPromedios.toInt()..j0 + radioPromedios.toInt()) {
                                        var aargb = myBitmap.getPixel(i, j)
                                        b += Color.blue(aargb).toFloat() / normalizacion
                                    }
                                }
                                blueTest.add(b.toInt())
                            }
                            encontrarMaximo(blueTest).run{
                                Q=this[0]
                                nB=listaXRectaAjuste[this[1]]
                            }
                        }
                        n0 += 1
                    }

                    blueX1 = nB.toInt()

                    // *** GUARDAR DATOS DE CALIBRACIÓN ***
                    CalibrationData.saveCalibrationData(
                        requireContext(),
                        m, // recta (intercepto y pendiente)
                        posicionEnXOrdenCero, // posición orden cero
                        blueX1, // posición blueX1
                        tita.toDouble() // ángulo tita
                    )

                    withContext(Dispatchers.Main){
                        textoProgreso.isVisible = false
                        barraProgreso.isVisible = false
                        captureButton.isEnabled = true
                        activarBotonContinuar = false // No permite continuar en modo calibración
                        Toast.makeText(activity, "✅ CALIBRACIÓN COMPLETADA\nRecta guardada exitosamente", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main){
                        textoProgreso.isVisible = false
                        activarBotonContinuar = false
                        captureButton.isEnabled = true
                        barraProgreso.isVisible = false
                        Toast.makeText(activity,"❌ Error en calibración: ${e.message}",Toast.LENGTH_LONG).show()
                    }
                }
            }

        } else {
            // =============== MODO NORMAL (USAR CALIBRACIÓN) ===============

            // Verificar si hay calibración disponible
            if (!CalibrationData.isCalibrated(requireContext())) {
                withContext(Dispatchers.Main){
                    textoProgreso.isVisible = false
                    barraProgreso.isVisible = false
                    captureButton.isEnabled = true
                    Toast.makeText(activity, "⚠️ NO HAY CALIBRACIÓN\nPor favor, ejecute primero una calibración", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // Cargar datos de calibración
            m = CalibrationData.getCalibrationLine(requireContext())!!
            posicionEnXOrdenCero = CalibrationData.getPosicionOrdenCero(requireContext())!!
            blueX1 = CalibrationData.getBlueX1(requireContext())!!
            tita = CalibrationData.getTita(requireContext())!!.toFloat()

            withContext(Dispatchers.Main){
                textoProgreso.text = "USANDO CALIBRACIÓN\nRecta cargada ✅"
            }

            // Construir listaXRectaAjuste y listaYRectaAjuste con datos calibrados
            for (k in radioPromedios.toInt() until posicionEnXOrdenCero-200) {
                listaXRectaAjuste.add(k)
                listaYRectaAjuste.add((k * m[1] + m[0]).toInt())
            }

            // Crear bitmap de visualización con recta calibrada
            imageReader = ImageReader.newInstance(w, h, args.pixelFormat, 1)
            previewSession.close()
            delay(500L)
            makePreviewSession(exposureTime,sensitivity,focalDistance)
            delay(500L)
            previewSession.close()
            delay(500L)
            picturesSession = createCaptureSession(camera, listOf(imageReader.surface), cameraHandler)

            takePhoto(exposureTime,sensitivity,focalDistanceCm,picturesSession).use{ result ->
                buffer = result.image.planes[0].buffer
                bytes = ByteArray(buffer.remaining()).apply {buffer.get(this)}
                myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                result.image.close()
                picturesSession.close()
                makePreviewSession(exposureTime,sensitivity,focalDistance)

                // Crear bitmap de prueba con recta calibrada
                prueba = myBitmap.copy(myBitmap.getConfig(), true)
                for (i in 0 until prueba.width) {
                    var j = (m[1] * i + m[0]).toInt()
                    prueba.setPixel(i, j, Color.rgb(255, 255, 255))
                }
                // Marcar posición de orden cero
                var i = posicionEnXOrdenCero
                var j = (m[1] * i + m[0]).toInt()
                for (k in -10..10) {
                    for (l in -10..10) {
                        prueba.setPixel(i + k, j + l, Color.rgb(255, 0, 255))
                    }
                }
                // Marcar posición de blueX1
                i = blueX1
                for (jMark in (h.toFloat()/3f).toInt() until (2f*h.toFloat()/3f).toInt()) {
                    for (k in -10..10) {
                        for (l in -10..10) {
                            prueba.setPixel(i + k, jMark + l, Color.rgb(255, 255, 255))
                        }
                    }
                }
            }

            // CONTINUAR CON MEDICIÓN NORMAL usando datos calibrados
            withContext(Dispatchers.Main){
                textoProgreso.text = "Generando posiciones\ncon calibración"
            }

            // *** MODIFICACIÓN: Medir toda la región del orden 1 incluyendo todos los colores ***
            // El problema es que blueX1 solo marca la posición del pico azul, pero el rojo está más lejos
            if (posicionEnXOrdenCero > 0 && blueX1 > 0) {
                // Calcular la distancia entre orden 0 y primer orden (basado en azul)
                val distanciaOrdenAzul = abs(posicionEnXOrdenCero - blueX1)

                // El espectro visible va aproximadamente de 380nm a 750nm
                // El rojo (650-750nm) está más lejos del orden 0 que el azul (450-490nm)
                // Expandir la región para incluir todo el espectro visible del orden 1

                // Usar una región más amplia que cubra desde el violeta hasta el rojo
                // El rojo puede estar hasta 1.6 veces más lejos que el azul del orden 0
                val factorExpansion = 1.8f // Factor para asegurar que incluimos todo el rojo
                val distanciaMaximaEspectro = (distanciaOrdenAzul * factorExpansion).toInt()

                // La región del orden 1 va desde una posición cercana al orden 0 hasta el extremo rojo
                val inicioOrden1 = maxOf(posicionEnXOrdenCero - (distanciaOrdenAzul * 0.3).toInt(), radioPromedios.toInt())
                val finOrden1 = minOf(posicionEnXOrdenCero - distanciaMaximaEspectro, w - 50)

                // Asegurar que el rango sea válido (inicioOrden1 > finOrden1 porque vamos hacia la izquierda)
                if (inicioOrden1 > finOrden1) {
                    listaIndices = (finOrden1 until inicioOrden1).toList()
                } else {
                    // Fallback: usar región centrada en blueX1 pero más amplia
                    val anchoOrden1Amplio = (distanciaOrdenAzul * 1.5).toInt()
                    val leftLimit = maxOf(blueX1 - anchoOrden1Amplio, radioPromedios.toInt())
                    val rightLimit = minOf(posicionEnXOrdenCero - (distanciaOrdenAzul * 0.2).toInt(), w - 50)
                    listaIndices = (leftLimit until rightLimit).toList()
                }

                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Medición de todo el orden 1:")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Posición orden 0: $posicionEnXOrdenCero")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Posición pico azul (blueX1): $blueX1")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Distancia orden azul: $distanciaOrdenAzul")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Distancia máxima espectro: $distanciaMaximaEspectro")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Inicio orden 1: $inicioOrden1")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Fin orden 1: $finOrden1")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Rango final: ${listaIndices.first()} a ${listaIndices.last()}")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Total píxeles a medir: ${listaIndices.size}")
            } else {
                // Fallback: si no hay datos de calibración válidos, usar un rango amplio
                val centroEstimado = w / 3 // Estimar el centro en el primer tercio de la imagen
                val anchoFallback = w / 5 // Usar 20% del ancho como fallback (más amplio)
                listaIndices = (centroEstimado - anchoFallback until centroEstimado + anchoFallback/2).toList()

                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Usando fallback amplio - datos de calibración no válidos")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Centro estimado: $centroEstimado")
                android.util.Log.d("DEBUG_ORDEN1_COMPLETO", "Total píxeles a medir: ${listaIndices.size}")
            }

            // Debug: mostrar información del rango
            android.util.Log.d("DEBUG_CALIBRACION", "Rango de medición:")
            android.util.Log.d("DEBUG_CALIBRACION", "leftLimit: ${listaIndices.first()}")
            android.util.Log.d("DEBUG_CALIBRACION", "rightLimit: ${listaIndices.last()}")
            android.util.Log.d("DEBUG_CALIBRACION", "Total píxeles a medir: ${listaIndices.size}")
            android.util.Log.d("DEBUG_CALIBRACION", "Ancho total imagen: $w")

            launch {
                val metric = sqrt(1 + pow(m[1], 2.0))
                for (i in 0 until listaIndices.size) {
                    listaConMetrica.add((i * metric + listaIndices[0]).toFloat())
                }
            }

            var L = listaIndices.size
            redOrder1 = zeros(L)
            greenOrder1 = zeros(L)
            blueOrder1 = zeros(L)
            redOrder2 = zeros(L)
            greenOrder2 = zeros(L)
            blueOrder2 = zeros(L)

            /**--------------------------------------------------------------------------**/
            /** Tomando fotos en vacío **/
            /**--------------------------------------------------------------------------**/

            withContext(Dispatchers.Main){
                textoProgreso.text="Fotos en vacío"
            }

            previewSession.close()
            makePreviewSession(exposureTime,sensitivity,focalDistance)
            delay(500L)
            previewSession.close()
            delay(500L)
            var progress = 0f
            repeat(numberOfPictures) {
                picturesSession = createCaptureSession(camera, listOf(imageReader.surface), cameraHandler)
                takePhoto(exposureTime, sensitivity, focalDistanceCm, picturesSession).use { result ->
                    buffer = result.image.planes[0].buffer
                    bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    result.image.close()
                    picturesSession.close()

                    for (n in listaIndices.indices) {
                        var i0 = listaIndices[n].toInt()
                        var j0 = (listaIndices[n] * m[1] + m[0]).toInt()
                        var r = 0f
                        var g = 0f
                        var b = 0f
                        for (i in i0 - radioPromedios.toInt()..i0 + radioPromedios.toInt()) {
                            for (j in j0 - radioPromedios.toInt()..j0 + radioPromedios.toInt()) {
                                var aargb = myBitmap.getPixel(i, j)
                                r += Color.red(aargb).toFloat() / (2*radioPromedios + 1).toDouble().pow(2.0).toFloat()
                                g += Color.green(aargb).toFloat() / (2*radioPromedios + 1).toDouble().pow(2.0).toFloat()
                                b += Color.blue(aargb).toFloat() / (2*radioPromedios + 1).toDouble().pow(2.0).toFloat()
                            }
                        }
                        redOrder1[n]+= r/255f/numberOfPictures.toFloat()
                        greenOrder1[n]+= g/255f/numberOfPictures.toFloat()
                        blueOrder1[n]+= b/255f/numberOfPictures.toFloat()
                    }

                    progress+=1f/numberOfPictures*100f
                    withContext(Dispatchers.Main){
                        textoProgreso.text="Progreso "+String.format("%.1f", progress)+"%"
                    }
                }
            }
            makePreviewSession(exposureTime,sensitivity,focalDistance)

            withContext(Dispatchers.Main){
                textoProgreso.text="Poner muestra"
            }

            delay(7000L)

            /**--------------------------------------------------------------------------**/
            /** Tomando fotos con muestra **/
            /**--------------------------------------------------------------------------**/

            withContext(Dispatchers.Main){
                textoProgreso.text="Foto con muestra"
            }

            previewSession.close()

            delay(500L)
            progress = 0f
            repeat(numberOfPictures) {
                picturesSession = createCaptureSession(camera, listOf(imageReader.surface), cameraHandler)
                takePhoto(exposureTime, sensitivity, focalDistanceCm, picturesSession).use { result ->
                    buffer = result.image.planes[0].buffer
                    bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                    myBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    result.image.close()
                    picturesSession.close()

                    for (n in listaIndices.indices) {
                        var i0 = listaIndices[n].toInt()
                        var j0 = (listaIndices[n] * m[1] + m[0]).toInt()
                        var r = 0f
                        var g = 0f
                        var b = 0f
                        for (i in i0 - radioPromedios.toInt()..i0 + radioPromedios.toInt()) {
                            for (j in j0 - radioPromedios.toInt()..j0 + radioPromedios.toInt()) {
                                var aargb = myBitmap.getPixel(i, j)
                                r += Color.red(aargb).toFloat() / (2*radioPromedios + 1).toDouble().pow(2.0).toFloat()
                                g += Color.green(aargb).toFloat() / (2*radioPromedios + 1).toDouble().pow(2.0).toFloat()
                                b += Color.blue(aargb).toFloat() / (2*radioPromedios + 1).toDouble().pow(2.0).toFloat()
                            }
                        }
                        redOrder2[n]+= r/255f/numberOfPictures.toFloat()
                        greenOrder2[n]+= g/255f/numberOfPictures.toFloat()
                        blueOrder2[n]+= b/255f/numberOfPictures.toFloat()
                    }

                    progress+=1f/numberOfPictures*100f
                    withContext(Dispatchers.Main){
                        textoProgreso.text="Progreso "+String.format("%.1f", progress)+"%"
                    }
                }
            }

            makePreviewSession(previewExposureTime,sensitivity,focalDistance)

            withContext(Dispatchers.Main){
                textoProgreso.isVisible = false
                activarBotonContinuar = true
                captureButton.isEnabled = true
                barraProgreso.isVisible = false
                Toast.makeText(activity, "✅ Medición completada\nUsando calibración guardada", Toast.LENGTH_SHORT).show()
            }
        }

    }


    @SuppressLint("MissingPermission")
    // Modificación 06/05/24 :
    private fun openDevice(exposureTime: Long, sensitivity: Int, focalDistance: Float) = lifecycleScope.launch(Dispatchers.Main) {
    //
        camera = openCamera(cameraManager, args.cameraId, cameraHandler)
        previewSession = createCaptureSession(camera, listOf(viewFinder.holder.surface), cameraHandler)
        captureRequestPreview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(viewFinder.holder.surface) }

        captureRequestPreview.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.BLACK_LEVEL_LOCK, true)
        captureRequestPreview.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CONTROL_AE_ANTIBANDING_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_AWB_MODE, CONTROL_AWB_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_EFFECT_MODE, CONTROL_EFFECT_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_DISABLED)
        captureRequestPreview.set(CaptureRequest.DISTORTION_CORRECTION_MODE, DISTORTION_CORRECTION_MODE_OFF)

        captureRequestPreview.set(CaptureRequest.LENS_FOCUS_DISTANCE, 1f/focalDistance*100f)  // Modificación 06/05/24
        captureRequestPreview.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
        captureRequestPreview.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity)
        previewSession.setRepeatingRequest(captureRequestPreview.build(), null, cameraHandler)

    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when(error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }
    // Modificación 06/05/24
    private suspend fun makePreviewSession(exposureTime: Long, sensitivity: Int, focalDistance: Float) = lifecycleScope.launch(Dispatchers.Main) {
    //
        previewSession = createCaptureSession(camera, listOf(viewFinder.holder.surface), cameraHandler)

        captureRequestPreview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)  // HIZO UN CAMBIO ACA TEMPLATE_PREVIW A TEMPLATE_MANUAL
            .apply { addTarget(viewFinder.holder.surface) }

        captureRequestPreview.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.BLACK_LEVEL_LOCK, true)
        captureRequestPreview.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CONTROL_AE_ANTIBANDING_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_AWB_MODE, CONTROL_AWB_MODE_OFF) // CAMBIE OFF A AUTO
        captureRequestPreview.set(CaptureRequest.CONTROL_EFFECT_MODE, CONTROL_EFFECT_MODE_OFF)
        captureRequestPreview.set(CaptureRequest.CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_DISABLED)
        captureRequestPreview.set(CaptureRequest.DISTORTION_CORRECTION_MODE, DISTORTION_CORRECTION_MODE_OFF)

        captureRequestPreview.set(CaptureRequest.LENS_FOCUS_DISTANCE, 1f/focalDistance*100f) // Modificación 06/05/24
        captureRequestPreview.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
        captureRequestPreview.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity)

        previewSession.setRepeatingRequest(captureRequestPreview.build(), null, cameraHandler)

    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        println("CaptureSessionCreated")
        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object: CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    private suspend fun takePhoto(exposureTime: Long,sensitivity : Int, focusDistance: Float, session : CameraCaptureSession):
            CombinedCaptureResult = suspendCoroutine { cont ->

        captureRequestStillCapture = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {addTarget(imageReader.surface)}

        captureRequestStillCapture.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        captureRequestStillCapture.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        captureRequestStillCapture.set(CaptureRequest.BLACK_LEVEL_LOCK, true)
        captureRequestStillCapture.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
        captureRequestStillCapture.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CONTROL_AE_ANTIBANDING_MODE_OFF)
        captureRequestStillCapture.set(CaptureRequest.CONTROL_AWB_MODE, CONTROL_AWB_MODE_OFF)
        captureRequestStillCapture.set(CaptureRequest.CONTROL_EFFECT_MODE, CONTROL_EFFECT_MODE_OFF)
        captureRequestStillCapture.set(CaptureRequest.CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_DISABLED)
        captureRequestStillCapture.set(CaptureRequest.DISTORTION_CORRECTION_MODE, DISTORTION_CORRECTION_MODE_OFF)

        captureRequestStillCapture.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
        captureRequestStillCapture.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
        captureRequestStillCapture.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity)

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {}

        // Start a new image queue
        var imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            var image = reader.acquireNextImage()
            imageQueue.add(image)
        }, imageReaderHandler)

        session.capture(captureRequestStillCapture.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                viewFinder.post(animationTask)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                var resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                // Set a timeout in case image captured is dropped from the pipeline
                var exc = TimeoutException("Image dequeuing took too long")
                var timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                //  the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {
                        // Dequeue images while timestamps don't match
                        var image = imageQueue.take()
                        // TODO(owahltinez): b/142011420
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            image.format != ImageFormat.DEPTH_JPEG &&
                            image.timestamp != resultTimestamp) continue
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        // Compute EXIF orientation metadata
                        //val rotation = relativeOrientation.value ?: 0
                        var rotation = 0
                        //val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        var mirrored = false
                        var exifOrientation = computeExifOrientation(rotation, mirrored)

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(
                            image, result, exifOrientation, imageReader.imageFormat))
                        break
                    }
                }
            }
        }, cameraHandler)
    }




    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    companion object {
        val TAG = CameraFragment::class.java.simpleName

        /** Maximum number of images that will be held in the reader's buffer */
        const val IMAGE_BUFFER_SIZE: Int = 30

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
            val image: Image,
            val metadata: CaptureResult,
            val orientation: Int,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        /**
         * Create a [File] named a using formatted timestamp with the current date and time.
         *
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
        }
    }


}

fun maxFinder(intensidades: IntArray) : List<Int> {
    /** Declaracion de variables a utilizar */
    var posicionesMaximos = ArrayList<Int>()
    val intensidadesEnMaximos = mutableListOf<Int>()
    val ancho = 100 // Ancho con el que defino que un punto es un maximo o minimo

    for (i in ancho+1..intensidades.size - (ancho+1)) {
        /** Si cumplen estas condiciones es un máximo */
        if (intensidades[i] >= intensidades.sliceArray(i-ancho..i-1).maxOrNull()!! &&
            intensidades[i] >= intensidades.sliceArray(i+1..i+ancho).maxOrNull()!! && intensidades[i] > 30) // antes 50
        {

            posicionesMaximos.add(i) // Adjunta la posicion del maximo al vector de maximos
            intensidadesEnMaximos.add(intensidades[i])
        }
    }

    val ordenCero = encontrarMaximo(intensidadesEnMaximos)[0]
    var ordenUnoLista = encontrarOrdenUno(intensidadesEnMaximos,ordenCero)
    var ordenUno = ordenUnoLista[0]
    var posOrdenUno = posicionesMaximos[ordenUnoLista[1]]

    return listOf(ordenUno,posOrdenUno)
}

fun firstIndexOf(xs : MutableList<Int>, x : Number ) : Number {
    var n = 0
    for (k in 0 until xs.lastIndex) {
        if (xs[k].toFloat()<x.toFloat()){
            continue
        } else if (xs[k].toFloat()==x.toFloat()) {
            n = k
            break
        } else {
            n = k-1
            break
        }
    }
    return n
}

fun encontrarMaximo (xs : MutableList<Int>) : List<Int> {
    val l = xs.size
    if (l==0) {
        return listOf<Int>(0,0)
    } else {
        var x0 = xs[0]
        var n = 0
        if (l >= 2 ) {
            for (k in 1..(l-1)) {
                if (xs[k] <= x0) {
                    continue
                } else {
                    x0 = xs[k]
                    n = k
                }
            }
        }
        return listOf<Int>(x0,n)
    }
}

fun encontrarMinimo (xs : MutableList<Int>) : List<Int> {
    val l = xs.size
    if (l==0) {
        return listOf<Int>()
    } else {
        var x0 = xs[0]
        var n = 0
        if (l >= 2 ) {
            for (k in 1..(l-1)) {
                if (xs[k] >= x0) {
                    continue
                } else {
                    x0 = xs[k]
                    n = k
                }
            }
        }
        return listOf<Int>(x0,n)
    }
}

fun encontrarOrdenUno (xs : MutableList<Int>, I : Int) : List<Int> {
    val l = xs.size
    if (l==0) {
        return listOf<Int>()
    } else {
        var x0 = xs[0]
        var n = 0
        if (l >= 2 ) {
            for (k in 1..(l-1)) {
                if (xs[k] <= x0 || xs[k]>=I) {
                    continue
                } else {
                    x0 = xs[k]
                    n = k
                }
            }
        }
        return listOf<Int>(x0,n)
    }
}

fun maxFinderPorIndice(intensidades: IntArray, j : Int, tol : Int) : List<Int> {
    /** Declaracion de variables a utilizar */
    var posicionesMaximos = ArrayList<Int>()
    val intensidadesEnMaximos = mutableListOf<Int>()
    val ancho = 100 // Ancho con el que defino que un punto es un maximo o minimo

    for (i in ancho+1..intensidades.size - (ancho+1)) {
        /** Si cumplen estas condiciones es un máximo */
        if (intensidades[i] >= intensidades.sliceArray(i-ancho..i-1).maxOrNull()!! &&
            intensidades[i] >= intensidades.sliceArray(i+1..i+ancho).maxOrNull()!! &&
            abs(i-j)<=tol &&
            intensidades[i] > 30) // antes 50
        {
            posicionesMaximos.add(i) // Adjunta la posicion del maximo al vector de maximos
            intensidadesEnMaximos.add(intensidades[i])
        }
    }

    var ordenUnoLista = encontrarMaximo(intensidadesEnMaximos)
    var ordenUno = ordenUnoLista[0]
    var posOrdenUno = posicionesMaximos[ordenUnoLista[1]]

    return listOf(ordenUno,posOrdenUno)
}

fun orientationFunction (orientation : Int) : Int {
    if (orientation == 0 || orientation == 90) {
        return 0
    } else {
        return 180
    }
}

fun doubleList(start : Int, final : Int) : MutableList<Double> {
    val xs = mutableListOf<Double>()
    var n0 = start.toDouble()
    while (n0<=final){
        xs.add(n0)
        n0+=1.toDouble()
    }
    return xs
}

fun heavisideTheta (x : Number) : Float {
    var y = x.toFloat()
    if (y<0){
        return 0f
    } else {
        return 1f
    }
}

fun calculateSD(numArray: DoubleArray): Double {
    var sum = 0.0
    var standardDeviation = 0.0

    for (num in numArray) {
        sum += num
    }

    val mean = sum / numArray.size

    for (num in numArray) {
        standardDeviation += Math.pow(num - mean, 2.0)
    }

    return Math.sqrt(standardDeviation / numArray.size)
}

fun zeros(n: Int) : MutableList<Float> {
    val xs : MutableList<Float> = mutableListOf()
    repeat(n){
        xs.add(0f)
    }
    return xs
}