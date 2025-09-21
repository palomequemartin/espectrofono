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
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toAdaptiveIcon
//import kotlinx.android.synthetic.main.absorbancia_calib.*
//import kotlinx.android.synthetic.main.captura.*
//import kotlinx.android.synthetic.main.fragment_camera.capture_button
//import kotlinx.android.synthetic.main.fragment_medir_absorbancia_test.*
import java.io.*
import java.nio.ByteBuffer
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.LegendRenderer
import com.jjoe64.graphview.series.BarGraphSeries
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.jjoe64.graphview.series.PointsGraphSeries


class captura : Fragment() {

    private val args : capturaArgs by navArgs()

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {


//        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
//            observe(viewLifecycleOwner, Observer {
//                    orientation -> layoutcaptura.rotation = orientationFunction(orientation).toFloat()
//            })
//        }

        val bitmap = args.bitmap
        //Roto el mapa
        var matrizRotTita = rotationMatrix(-90f)
        var bitmapRotado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrizRotTita, true).toDrawable(resources)
        val view = inflater.inflate(R.layout.captura, container, false)
        val captura : ImageView = view.findViewById(R.id.imageView)
        captura.setImageDrawable(bitmapRotado)

        val botonContinuar : ImageButton = view.findViewById(R.id.botonContinuar)
//        println("ULTIMO AZUL "+args.blueOrder1IndexList.last())
        botonContinuar.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                capturaDirections.actionCapturaToPerfiles(
                    args.cameraId, args.blueOrder1, args.redOrder1,
                    args.greenOrder1, args.blueOrder2, args.redOrder2,
                        args.greenOrder2,args.listaIndices,args.posicionEnXOrden0,
                        args.posicionEnXMaxBlue1
                )
            )
        }

        return view

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer {
                    orientation -> view.rotation = orientationFunction(orientation).toFloat()
            })
        }

    }


    fun orientationFunction (orientation : Int) : Int {
        if (orientation == 0 || orientation == 90) {
            return 0
        } else {
            return 180
        }
    }
}

