package com.example.android.camera2.lutin.fragments

import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.os.Bundle
import android.view.*
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.rotationMatrix
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.OrientationLiveData
import com.luciocoro.lutin.R
import java.util.*
import kotlin.math.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
//import kotlinx.android.synthetic.main.absorbancia_calib.*
//import kotlinx.android.synthetic.main.captura.*
//import kotlinx.android.synthetic.main.fragment_camera.capture_button
//import kotlinx.android.synthetic.main.fragment_medir_absorbancia_test.*
import java.io.*


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
        val botonRetroceder : ImageButton = view.findViewById(R.id.botonRetroceder)

        botonRetroceder.setOnClickListener {
            findNavController().navigate(
                capturaDirections.actionCapturaToMedirAbsorbanciaTest(
                    args.cameraId,args.pixelFormat,args.numberOfPictures,
                    args.exposureTime,args.sensitivity,args.focalDistance
                )
            )
        }
//        println("ULTIMO AZUL "+args.blueOrder1IndexList.last())
        botonContinuar.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                capturaDirections.actionCapturaToPerfiles(
                    args.cameraId, args.blueOrder1, args.redOrder1,
                    args.greenOrder1,args.grisesSinMuestra, args.grisesConMuestra, args.blueOrder2, args.redOrder2,
                        args.greenOrder2,args.listaIndices,args.posicionEnXOrden0,
                        args.posicionEnXMaxBlue1,args.numberOfPictures,args.exposureTime,args.sensitivity,args.focalDistance
                )
            )
        }

        return view

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

//        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
//            observe(viewLifecycleOwner, Observer {
//                    orientation -> view.rotation = orientationFunction(orientation).toFloat()
//            })
//        }
//
    }


    fun orientationFunction (orientation : Int) : Int {
        if (orientation == 0 || orientation == 90) {
            return 0
        } else {
            return 180
        }
    }
}
