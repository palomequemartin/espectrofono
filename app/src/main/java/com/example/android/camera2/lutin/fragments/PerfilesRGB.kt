package com.example.android.camera2.lutin.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.*
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.OrientationLiveData
import com.luciocoro.lutin.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.*
import android.net.Uri
import android.widget.*
import androidx.core.content.FileProvider
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
//import kotlinx.android.synthetic.main.absorbancia_calib.*
//import kotlinx.android.synthetic.main.fragment_camera.capture_button
//import kotlinx.android.synthetic.main.fragment_medir_absorbancia_test.*
import kotlinx.coroutines.*
import java.io.*


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

//        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
//            observe(viewLifecycleOwner, Observer {
//                    orientation -> view.rotation = orientationFunction(orientation).toFloat()
//            })
//        }
        /** Inicia los botones de guardado y continuar */
        val botonGuardar : ImageButton = view.findViewById(R.id.exportarDatos)
        val botonContinuar : ImageButton = view.findViewById(R.id.botonContinuar)
        val botonGuardarGrises : ImageButton = view.findViewById(R.id.exportarGrises)
        val botonToFigures : ImageButton = view.findViewById(R.id.toFigures)

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


        val grisesSinMuestra = args.grisesSinMuestra.matrix
        val grisesConMuestra = args.grisesConMuestra.matrix


        super.onViewCreated(view, savedInstanceState)

        /** Asigna acciones a los botones */

        botonGuardar.setOnClickListener(){
            exportarDatos()
        }

        botonToFigures.setOnClickListener {
            // Use Safe Args to create the action and pass arguments
            Navigation.run {
                findNavController(requireActivity(), R.id.fragment_container).navigate(
                    PerfilesRGBDirections.actionPerfilesToFiguresFragment(
                        args.cameraId, args.blueOrder1, args.redOrder1,
                        args.greenOrder1,args.grisesSinMuestra, args.grisesConMuestra,
                        args.blueOrder2, args.redOrder2,args.greenOrder2,
                        args.listaIndices,args.posicionEnXOrden0,args.posicionEnXMaxBlue1,
                        args.numberOfPictures,args.exposureTime,args.sensitivity,
                        args.focalDistance
                    )
                )
            }
        }

        botonContinuar.setOnClickListener {
            findNavController().navigate(
                R.id.permissions_fragment,
                null,
                NavOptions.Builder()
                    .setPopUpTo(findNavController().graph.startDestinationId, true)
                    .build()
            )
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




//

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

        botonGuardarGrises.setOnClickListener(){
            exportarGrises(grisesSinMuestra,"SinMuestra")
            exportarGrises(grisesConMuestra,"ConMuestra")
        }
    }

    private fun exportarDatos() = lifecycleScope.launch(Dispatchers.IO) {
        var datos = StringBuilder()
        datos.append(StringBuilder("Nro. de pixel,R_blanco,G_blanco,B_blanco,R_muestra,G_muestra,B_muestra,Nro Fotos,Exposure Time,Sensitivity,Focal Distance"))
        datos.append("\n")

        for (i in args.listaIndices.indices) {
            var fila = StringBuilder()
            if (i == 0){
                fila.append(args.listaIndices[i].toString()+
                        ","+args.redOrder1[i].toString()+","+args.greenOrder1[i].toString()+","+args.blueOrder1[i].toString()+
                        ","+args.redOrder2[i].toString()+","+args.greenOrder2[i].toString()+","+args.blueOrder2[i].toString()+
                        ","+args.numberOfPictures.toString()+","+args.exposureTime.toString()+","+args.sensitivity.toString()+
                    ","+args.focalDistance.toString())
                datos.append(fila.append("\n"))
            }

            fila.append(args.listaIndices[i].toString()+
                    ","+args.redOrder1[i].toString()+","+args.greenOrder1[i].toString()+","+args.blueOrder1[i].toString()+
                    ","+args.redOrder2[i].toString()+","+args.greenOrder2[i].toString()+","+args.blueOrder2[i].toString())
            datos.append(fila.append("\n"))
        }

        var datazo = (datos.toString()).toByteArray()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val nombre = "androidAppData_$timestamp.csv"

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
                    "com.luciocoro.lutin.fileprovider",
                    filelocation
            )
            val fileIntent: Intent = Intent(Intent.ACTION_SEND)
            fileIntent.setType("text/csv")
            fileIntent.putExtra(Intent.EXTRA_SUBJECT, "androidAppData.csv")
            fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            fileIntent.putExtra(Intent.EXTRA_STREAM, path)
            startActivity(Intent.createChooser(fileIntent, "send mail"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
        }


    }

    private fun exportarGrises(matrix: MutableList<MutableList<Float>>, name : String) = lifecycleScope.launch(Dispatchers.IO) {
        try {
            // Create StringBuilder for CSV content
            val datos = StringBuilder()

            // Determine the number of rows in the CSV (max length of any matrix row)
            val maxRowLength = matrix.maxOfOrNull { it.size } ?: 0
            if (maxRowLength == 0) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Matrix is empty", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // Build CSV: each matrix row becomes a column
            for (i in 0 until maxRowLength) {
                val row = StringBuilder()
                // Iterate over each matrix row (CSV column)
                matrix.forEachIndexed { index, matrixRow ->
                    // Get the element at position i, or 0.0f if index i doesn't exist
                    val value = if (i < matrixRow.size) matrixRow[i].toString() else "0.0"
                    row.append(value)
                    // Add comma unless it's the last column
                    if (index < matrix.size - 1) row.append(",")
                }
                datos.append(row.append("\n"))
            }

            // Convert to ByteArray for file writing
            val datazo = datos.toString().toByteArray()

            // Generate timestamped filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val nombre = "matrixData$name$timestamp.csv"

            // Save file to internal storage
            val out: OutputStream? = context?.openFileOutput(nombre, Context.MODE_PRIVATE)
            out?.write(datazo)
            out?.close()

            // Prepare file for sharing
            val context: Context = requireContext().applicationContext
            val fileLocation: File = File(context.filesDir, nombre)
            val path: Uri = FileProvider.getUriForFile(
                context,
                "com.luciocoro.lutin.fileprovider",
                fileLocation
            )

            // Create sharing intent
            val fileIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "matrixData$name$timestamp.csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, path)
            }

            // Launch chooser on main thread
            withContext(Dispatchers.Main) {
                startActivity(Intent.createChooser(fileIntent, "Send CSV"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
