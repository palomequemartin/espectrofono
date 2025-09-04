package com.example.android.camera2.lutin.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Surface
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.luciocoro.lutin.R
import com.example.android.camera.utils.OrientationLiveData
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.text.method.LinkMovementMethod
import androidx.core.view.WindowInsetsCompat

class MenuFragment : Fragment() {

    private val args: MenuFragmentArgs by navArgs()

    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    private lateinit var relativeOrientation: OrientationLiveData

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.menu_inicial, container, false)
        val titulo = view.findViewById<Button>(R.id.Titulo)
        val valoresPermitidos = view.findViewById<TextView>(R.id.valoresPermitidos)
        val medirAbsTest = view.findViewById<Button>(R.id.MedirAbsorbanciaTest)
        val numberOfPicturesTextView = view.findViewById<EditText>(R.id.nrofotos)
        val exposureTimeTextView = view.findViewById<EditText>(R.id.texposicion2)
        val sensitivityTextView = view.findViewById<EditText>(R.id.sensibilidad)
        val focalDistanceTextView = view.findViewById<EditText>(R.id.distfocal)
        val rangoS = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val tmin = rangoS?.lower
        val tmax = rangoS?.upper
        val dimensions = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(args.pixelFormat)
        val h = dimensions[0].height
        val w = dimensions[0].width
        val sensMin = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.lower ?: 0
        val sensMax = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.upper ?: 100000
        val minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 1f

        val linkLOFTview = view.findViewById<TextView>(R.id.linkLOFT)
        linkLOFTview.movementMethod = LinkMovementMethod.getInstance()

        // Apply rotation only to camera-related elements (if any)
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner) { orientation ->
                val displayRotation = requireActivity().windowManager.defaultDisplay.rotation
                val adjustedRotation = adjustRotationForDisplay(orientation, displayRotation)
                // Apply rotation to camera preview (if present) or specific views, not the entire view
                // view.rotation = adjustedRotation.toFloat() // Avoid rotating entire view
            }
        }

        medirAbsTest.setOnClickListener {
            if (numberOfPicturesTextView.text.isNotEmpty() && exposureTimeTextView.text.isNotEmpty() &&
                sensitivityTextView.text.isNotEmpty() && focalDistanceTextView.text.isNotEmpty()
            ) {
                val numberOfPictures = numberOfPicturesTextView.text.toString().toInt()
                val exposureTime = (exposureTimeTextView.text.toString().toFloat() * 1000000f).toLong()
                val sensitivity = sensitivityTextView.text.toString().toInt()
                val focalDistance = focalDistanceTextView.text.toString().toFloat()

                if (numberOfPictures < 0) {
                    Toast.makeText(activity, "El número de fotos es negativo", Toast.LENGTH_SHORT).show()
                } else if (exposureTime < (tmin ?: 0L) || exposureTime > (tmax ?: (10000L * 1000000L))) {
                    Toast.makeText(activity, "El tiempo de exposición no está en rango", Toast.LENGTH_SHORT).show()
                } else if (sensitivity < sensMin || sensitivity > sensMax) {
                    Toast.makeText(activity, "La sensibilidad no está en rango", Toast.LENGTH_SHORT).show()
                } else if (focalDistance < 1f / minimumFocusDistance * 100f) {
                    Toast.makeText(activity, "Aumentar distancia focal", Toast.LENGTH_SHORT).show()
                } else {
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(
                            MenuFragmentDirections.actionMenuFragmentToMedirAbsorbanciaTest(
                                args.cameraId, args.pixelFormat, numberOfPictures, exposureTime,
                                sensitivity, focalDistance
                            )
                        )
                }
            } else if (numberOfPicturesTextView.text.isNotEmpty() && exposureTimeTextView.text.isNotEmpty() &&
                sensitivityTextView.text.isNotEmpty() && focalDistanceTextView.text.isEmpty()
            ) {
                val numberOfPictures = numberOfPicturesTextView.text.toString().toInt()
                val exposureTime = (exposureTimeTextView.text.toString().toFloat() * 1000000f).toLong()
                val sensitivity = sensitivityTextView.text.toString().toInt()
                val focalDistance = 1f / minimumFocusDistance * 100f

                if (numberOfPictures < 0) {
                    Toast.makeText(activity, "El número de fotos es negativo", Toast.LENGTH_SHORT).show()
                } else if (exposureTime < (tmin ?: 0L) || exposureTime > (tmax ?: (10000L * 1000000L))) {
                    Toast.makeText(activity, "El tiempo de exposición no está en rango", Toast.LENGTH_SHORT).show()
                } else if (sensitivity < sensMin || sensitivity > sensMax) {
                    Toast.makeText(activity, "La sensibilidad no está en rango", Toast.LENGTH_SHORT).show()
                } else {
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(
                            MenuFragmentDirections.actionMenuFragmentToMedirAbsorbanciaTest(
                                args.cameraId, args.pixelFormat, numberOfPictures, exposureTime,
                                sensitivity, focalDistance
                            )
                        )
                }
            } else {
                Toast.makeText(activity, "Completar número de fotos, tiempo de exposición y sensibilidad", Toast.LENGTH_SHORT).show()
            }
        }

        titulo.setOnClickListener {
            val dialog = VentanaInfo()
            dialog.show(childFragmentManager, "VentanaInfo")
        }

        if (tmin != null && tmax != null) {
            valoresPermitidos.text = "Características\nDimensiones de la imagen: ${w}x${h} píxeles\nRango de tiempo de " +
                    "exposición: ${tmin.toFloat() / 1000000f}-${tmax.toFloat() / 1000000f} ms" +
                    "\nRango de sensibilidad: $sensMin-$sensMax (ISO 12232:2006)" +
                    "\nDistancia focal mínima: ${1f / minimumFocusDistance * 100f} cm"
        } else {
            Toast.makeText(activity, "Error en la adquisición de rango de tiempo de exposición de la cámara", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Handle system bars and keyboard insets
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left, systemBars.top,
                systemBars.right, maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("nrofotos", view?.findViewById<EditText>(R.id.nrofotos)?.text?.toString())
        outState.putString("texposicion2", view?.findViewById<EditText>(R.id.texposicion2)?.text?.toString())
        outState.putString("sensibilidad", view?.findViewById<EditText>(R.id.sensibilidad)?.text?.toString())
        outState.putString("distfocal", view?.findViewById<EditText>(R.id.distfocal)?.text?.toString())
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let {
            view?.findViewById<EditText>(R.id.nrofotos)?.setText(it.getString("nrofotos"))
            view?.findViewById<EditText>(R.id.texposicion2)?.setText(it.getString("texposicion2"))
            view?.findViewById<EditText>(R.id.sensibilidad)?.setText(it.getString("sensibilidad"))
            view?.findViewById<EditText>(R.id.distfocal)?.setText(it.getString("distfocal"))
        }
    }

    private fun adjustRotationForDisplay(sensorOrientation: Int, displayRotation: Int): Int {
        val displayDegrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensorDegrees = orientationFunction(sensorOrientation)
        return (sensorDegrees - displayDegrees + 360) % 360
    }

    // Placeholder for your orientationFunction (replace with actual implementation)
    private fun orientationFunction(orientation: Int): Int {
        // Replace with your actual logic from OrientationLiveData
        return orientation
    }
}