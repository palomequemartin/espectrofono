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
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toAdaptiveIcon
import androidx.core.view.isVisible
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
//import kotlinx.android.synthetic.main.absorbancia_calib.*
//import kotlinx.android.synthetic.main.fragment_camera.capture_button
//import kotlinx.android.synthetic.main.fragment_medir_absorbancia_test.*
import kotlinx.coroutines.*
import java.io.*
import java.lang.Math.pow
import java.lang.Runnable
import java.nio.ByteBuffer


class PerfilesRGB : Fragment() {

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    private val args: PerfilesRGBArgs by navArgs()

    lateinit var graphview: GraphView

    val seriesBlue = LineGraphSeries<DataPoint>()
    val seriesRed = LineGraphSeries<DataPoint>()
    val seriesGreen = LineGraphSeries<DataPoint>()
    val seriesGray = LineGraphSeries<DataPoint>()
    val grayOrder1WaveLengthList = mutableListOf<Float>()
    val grayOrder1 = mutableListOf<Float>()
    val grayOrder2WaveLengthList = mutableListOf<Float>()
    val grayOrder2 = mutableListOf<Float>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_perfiles_r_g_b, container, false)


    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer {
                    orientation -> view.rotation = orientationFunction(orientation).toFloat()
            })
        }

        val botonGuardar : ImageButton = view.findViewById(R.id.exportarDatos)

        val switchRojo: Switch = view.findViewById(R.id.switchRojo)
        val switchVerde: Switch = view.findViewById(R.id.switchVerde)
        val switchAzul: Switch = view.findViewById(R.id.switchAzul)
        val switchGris: Switch = view.findViewById(R.id.switchGris)

        switchRojo.isChecked = true
        switchVerde.isChecked = true
        switchAzul.isChecked = true
        switchGris.isChecked = false

        var redSeriesDone = true
        var greenSeriesDone = true
        var blueSeriesDone = true
        var graySeriesDone = false

        super.onViewCreated(view, savedInstanceState)

        botonGuardar.setOnClickListener(){
            exportarDatos()
        }

        graphview = view.findViewById(R.id.graph)

        lifecycleScope.launch(Dispatchers.IO) {
            lifecycleScope.launch {

                val listaIndices = args.listaIndices
                val blueOrder1 = args.blueOrder1
                val redOrder1 = args.redOrder1
                val greenOrder1 = args.greenOrder1
                val blueOrder2 = args.blueOrder2
                val redOrder2 = args.redOrder2
                val greenOrder2 = args.greenOrder2
                val posicionEnXOrden0 = args.posicionEnXOrden0
                val blueX1 = args.posicionEnXMaxBlue1

                println("POS MAX BLUE 1 = "+blueX1)

                val latticeFreq = 500e-6.toFloat()
                val beta1 = beta((posicionEnXOrden0-blueX1).toFloat(),450f,latticeFreq,1)

                println("BETA  =  "+beta1.toString())
                println("LAMBDA FRIO = "+waveLength(1f, latticeFreq, 0f,
                    (posicionEnXOrden0 - blueX1).toFloat(), beta1).toString())
                // val beta2 = beta((posicionEnXOrden0-blueX1).toFloat(),450f,latticeFreq,1)

                val blueOrder1WaveLengthList = mutableListOf<Float>()
                val redOrder1WaveLengthList = mutableListOf<Float>()
                val greenOrder1WaveLengthList = mutableListOf<Float>()

                val gamma = 1f

                for (n in 0 until listaIndices.size) {
                    grayOrder1.add(gris(redOrder1[n],greenOrder1[n],blueOrder1[n],gamma))
                    grayOrder2.add(gris(redOrder2[n],greenOrder2[n],blueOrder2[n],gamma))
                }

                lifecycleScope.launch {
                    lifecycleScope.launch {
                        for (i in listaIndices) {
                            blueOrder1WaveLengthList.add(waveLength(1f, latticeFreq, 0f,
                                    (posicionEnXOrden0 - i).toFloat(), beta1))
                        }
                    }
                    lifecycleScope.launch {
                        for (i in listaIndices) {
                            redOrder1WaveLengthList.add(waveLength(1f, latticeFreq, 0f,
                                (posicionEnXOrden0 - i).toFloat(), beta1))
                        }
                    }
                    lifecycleScope.launch {
                        for (i in listaIndices) {
                            greenOrder1WaveLengthList.add(waveLength(1f, latticeFreq, 0f,
                                (posicionEnXOrden0 - i).toFloat(), beta1))
                        }
                    }
                    lifecycleScope.launch {
                        for (i in listaIndices) {
                            grayOrder1WaveLengthList.add(waveLength(1f, latticeFreq, 0f,
                                (posicionEnXOrden0 - i).toFloat(), beta1))
                        }
                    }
                }


                val job1 = launch {
                    for (i in 0 until listaIndices.lastIndex) {
                        seriesBlue.appendData(DataPoint(listaIndices[i].toDouble(), blueOrder1[i].toDouble()), true, 10000)
                    }
                }

                val job2 = launch {
                    for (i in 0 until listaIndices.lastIndex) {
                        seriesRed.appendData(DataPoint(listaIndices[i].toDouble(), redOrder1[i].toDouble()), true, 10000)
                    }
                }

                val job3 = launch {
                    for (i in 0 until listaIndices.lastIndex) {
                        seriesGreen.appendData(DataPoint(listaIndices[i].toDouble(), greenOrder1[i].toDouble()), true, 10000)
                    }

                }

                val job4 = launch {
                    for (i in 0 until listaIndices.lastIndex) {
                        seriesGray.appendData(DataPoint(listaIndices[i].toDouble(), grayOrder1[i].toDouble()), true, 5000)
                    }
                }

                joinAll(job1, job2, job3, job4)

                seriesBlue.color = Color.BLUE
                seriesRed.color = Color.RED
                seriesGreen.color = Color.GREEN
                seriesGray.color = Color.GRAY

                graphview.viewport.isXAxisBoundsManual = true
                graphview.viewport.isYAxisBoundsManual = true

                graphview.viewport.setMinX((listaIndices.first()).toDouble())

                graphview.viewport.setMaxX((listaIndices.last()).toDouble())
                graphview.viewport.setMinY(-0.1)
                graphview.viewport.setMaxY(1.0)

                graphview.gridLabelRenderer.horizontalAxisTitle = "Número de pixel"
                graphview.gridLabelRenderer.verticalAxisTitle = "Intensidad"
                graphview.addSeries(seriesBlue)
                graphview.addSeries(seriesRed)
                graphview.addSeries(seriesGreen)

            }.join()

            lifecycleScope.launch(Dispatchers.Default) {
                launch {
                    while (true) {
                        if (switchRojo.isChecked) {
                            if (!redSeriesDone) {
                                makeGraph(graphview, seriesRed, "a")
                                redSeriesDone = true
                            }
                        } else {
                            if (redSeriesDone) {
                                makeGraph(graphview, seriesRed, "r")
                                redSeriesDone = false
                            }
                        }
                    }
                }
                launch {
                    while (true) {
                        if (switchVerde.isChecked) {
                            if (!greenSeriesDone) {
                                makeGraph(graphview, seriesGreen, "a")
                                greenSeriesDone = true
                            }
                        } else {
                            if (greenSeriesDone) {
                                makeGraph(graphview, seriesGreen, "r")
                                greenSeriesDone = false
                            }
                        }
                    }
                }
                launch {
                    while (true) {
                        if (switchAzul.isChecked) {
                            if (!blueSeriesDone) {
                                makeGraph(graphview, seriesBlue, "a")
                                blueSeriesDone = true
                            }
                        } else {
                            if (blueSeriesDone) {
                                makeGraph(graphview, seriesBlue, "r")
                                blueSeriesDone = false
                            }
                        }
                    }
                }
                launch {
                    while (true) {
                        if (switchGris.isChecked) {
                            if (!graySeriesDone) {
                                makeGraph(graphview, seriesGray, "a")
                                graySeriesDone = true
                            }
                        } else {
                            if (graySeriesDone) {
                                makeGraph(graphview, seriesGray, "r")
                                graySeriesDone = false
                            }
                        }
                    }
                }
            }
        }
    }

    fun exportarDatos() = lifecycleScope.launch(Dispatchers.IO) {
        var datos = StringBuilder()
        datos.append(StringBuilder("Nro. de pixel\tR_blanco\tG_blanco\tB_blanco\tR_muestra\tG_muestra\tB_muestra\n"))
        for (i in args.listaIndices.indices) {
            var fila = StringBuilder()
            fila.append(args.listaIndices[i].toString()+
                    "\t"+args.redOrder1[i].toString()+"\t"+args.greenOrder1[i].toString()+"\t"+args.blueOrder1[i].toString()+
                    "\t"+args.redOrder2[i].toString()+"\t"+args.greenOrder2[i].toString()+"\t"+args.blueOrder2[i].toString())
            datos.append(fila.append("\n"))
        }
        var datazo = (datos.toString()).toByteArray()
        var nombre = "androidAppData.txt"
        try {
            //guardo el archivo pero no puedo acceder
            val out: OutputStream? = context?.openFileOutput(nombre, Context.MODE_PRIVATE)
            out?.write(datazo)
            out?.close()
            //ahora lo trato de exportar
            val context: Context = requireContext().applicationContext
            val filelocation: File = File(context.filesDir, nombre)
            val path: Uri = FileProvider.getUriForFile(
                    context,
                    "com.example.android.camera2.basic.fileprovider",
                    filelocation
            )
            val fileIntent: Intent = Intent(Intent.ACTION_SEND)
            fileIntent.setType("text/txt")
            fileIntent.putExtra(Intent.EXTRA_SUBJECT, "androidAppData.txt")
            fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            fileIntent.putExtra(Intent.EXTRA_STREAM, path)
            startActivity(Intent.createChooser(fileIntent, "send mail"))
        } catch (e: Exception) {
            e.printStackTrace()
        }


    }

    private fun makeGraph(graph : GraphView, series : LineGraphSeries<DataPoint>,action : String) = lifecycleScope.launch(Dispatchers.IO){
        if (action == "a") {
            graph.addSeries(series)
        } else {
            graph.removeSeries(series)
        }
        withContext(Dispatchers.Main){
            graph.refreshDrawableState()
        }

    }

    fun orientationFunction (orientation : Int) : Int {
        if (orientation == 0 || orientation == 90) {
            return 0
        } else {
            return 180
        }
    }

//    private fun calibracionLongitudDeOnda(xs : List<Int>) : List<Float> {
//
//    }
}

fun waveLength(n : Float, f : Float, theta : Float, p : Float, beta : Float) : Float{
    return (1/(n*f)*((p/beta+tan(theta))/ sqrt(1+(p/beta+sin(theta))*(p/beta+sin(theta)))-sin(theta))).toFloat()
}

fun beta(p0: Float ,lambda0: Float,f: Float,n : Int): Float {
    return p0/(n*lambda0*f/ sqrt(1-(n*lambda0*f)*(lambda0*f)))
}

fun gris(R: Float, G: Float, B: Float, gamma: Float): Float{
   return (0.3333f*R+0.3333f*G+0.3333f*B)
}

