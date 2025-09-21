package com.example.android.camera2.basic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import android.text.method.LinkMovementMethod

import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera2.basic.R
import com.example.android.camera2.basic.CalibrationData
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.*
import com.example.android.camera.utils.OrientationLiveData
//import kotlinx.android.synthetic.main.fragment_medir_absorbancia_test.*
//import kotlinx.android.synthetic.main.menu_inicial.*

/* Este es el fragmento principal. Una vez que diste los permisos y elegiste la camera se abres te menu.
*  Descubri algo buenisimo. En nav_graph.xml podes ver como hacer se relacionan los fragmentos de manera visual.

* */
class MenuFragment: Fragment(){

    /** AndroidX navigation arguments */
    // Se reciben las variables definidas en otros fragmentos (declarados en el panel de navegación)
    private val args: MenuFragmentArgs by navArgs()

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    // Comando necesario para la detección, caracterización y conexión a la cámara:
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    // Comando para obtener las características de  la cámara, dado un ID de la misma:
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /** Live data listener for changes in the device orientation relative to the camera */
    // Comando asociado a la verificación en vivo de la orientación relativa del celular
    // respecto a la orientación de la cámara:
    private lateinit var relativeOrientation: OrientationLiveData

    @SuppressLint("SetTextI18n")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.menu_inicial, container, false)
        val titulo = view.findViewById<Button>(R.id.Titulo)
        val valoresPermitidos = view.findViewById<TextView>(R.id.valoresPermitidos)
        val medirAbsTest : Button = view.findViewById(R.id.MedirAbsorbanciaTest)
        val botonCalibracion : Button = view.findViewById(R.id.BotonCalibracion)
        val numberOfPicturesTextView : TextView = view.findViewById((R.id.nrofotos))
        val exposureTimeTextView : TextView = view.findViewById((R.id.texposicion2))
        val sensitivityTextView : TextView = view.findViewById((R.id.sensibilidad))
        val focalDistanceTextView : TextView = view.findViewById((R.id.distfocal)) // Modificación 06/05/24
        val rangoS = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val tmin = rangoS?.lower
        val tmax = rangoS?.upper
        val dimensions = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(args.pixelFormat)
        val h = dimensions[0].height
        val w = dimensions[0].width
        val sensMin = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.lower ?: 0
        val sensMax = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.upper ?: 100000
        val minimumFocusDistance : Float = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 1f

        val linkLOFTview= view.findViewById<TextView>(R.id.linkLOFT)
        linkLOFTview.movementMethod = LinkMovementMethod.getInstance()

        val layoutMenuFragment : View = view.findViewById(R.id.layoutMenuFragment)

        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, androidx.lifecycle.Observer {
                    orientation -> view.rotation = orientationFunction(orientation).toFloat()
            })
        }

        medirAbsTest.setOnClickListener {
            if (numberOfPicturesTextView.text.isNotEmpty() && exposureTimeTextView.text.isNotEmpty() && sensitivityTextView.text.isNotEmpty() && focalDistanceTextView.text.isNotEmpty())
            {
                val numberOfPictures: Int = numberOfPicturesTextView.text.toString().toInt()
                val exposureTime: Long = (exposureTimeTextView.text.toString().toFloat()*1000000f).toLong()
                val sensitivity: Int = sensitivityTextView.text.toString().toInt()
                val focalDistance: Float = focalDistanceTextView.text.toString().toFloat() // Modificación 06/05/24

                if (numberOfPictures<0)
                {
                    Toast.makeText(activity,"El número de fotos es negativo",Toast.LENGTH_SHORT).show()
                }
                else if (exposureTime < (tmin?: 0L) || exposureTime > (tmax?: (10000L*1000000L)))
                {
                    Toast.makeText(activity,"El tiempo de exposición no está en rango",Toast.LENGTH_SHORT).show()
                }
                else if (sensitivity<sensMin || sensitivity>sensMax)
                {
                    Toast.makeText(activity,"La sensibilidad no está en rango",Toast.LENGTH_SHORT).show()
                }
                else if (focalDistance < 1f/minimumFocusDistance*100f) //CHEQUEAR, CAMBIE ESTO 06/05
                {
                    Toast.makeText(activity,"Aumentar distancia focal",Toast.LENGTH_SHORT).show()
                }
                else
                {
                    val action = MenuFragmentDirections.actionMenuFragmentToMedirAbsorbanciaTest(
                        args.cameraId,
                        args.pixelFormat,
                        numberOfPictures,
                        exposureTime,
                        sensitivity,
                        focalDistance // Modificación 06/05/24
                    )
                    // Agregar parámetro adicional directamente al bundle
                    val bundle = action.arguments
                    bundle.putBoolean("isCalibrationMode", false)

                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(R.id.MedirAbsorbanciaTest, bundle)
                }

            }
            // Comienzo de modificación al código 13/05/24
            else if (numberOfPicturesTextView.text.isNotEmpty() && exposureTimeTextView.text.isNotEmpty() && sensitivityTextView.text.isNotEmpty() && focalDistanceTextView.text.isEmpty())
            {
                val numberOfPictures: Int = numberOfPicturesTextView.text.toString().toInt()
                val exposureTime: Long = (exposureTimeTextView.text.toString().toFloat()*1000000f).toLong()
                val sensitivity: Int = sensitivityTextView.text.toString().toInt()
                val focalDistance: Float = 1f/minimumFocusDistance*100f

                if (numberOfPictures<0)
                {
                    Toast.makeText(activity,"El número de fotos es negativo",Toast.LENGTH_SHORT).show()
                }
                else if (exposureTime < (tmin?: 0L) || exposureTime > (tmax?: (10000L*1000000L)))
                {
                    Toast.makeText(activity,"El tiempo de exposición no está en rango",Toast.LENGTH_SHORT).show()
                }
                else if (sensitivity<sensMin || sensitivity>sensMax)
                {
                    Toast.makeText(activity,"La sensibilidad no está en rango",Toast.LENGTH_SHORT).show()
                }
                else
                {
                    val action = MenuFragmentDirections.actionMenuFragmentToMedirAbsorbanciaTest(
                        args.cameraId,
                        args.pixelFormat,
                        numberOfPictures,
                        exposureTime,
                        sensitivity,
                        focalDistance
                    )
                    // Agregar parámetro adicional directamente al bundle
                    val bundle = action.arguments
                    bundle.putBoolean("isCalibrationMode", false)

                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(R.id.MedirAbsorbanciaTest, bundle)
                }

            }
            // Fin de modificación - Se hizo con el objetivo de no tener que configurar la distancia
            // focal cada vez, sino que se configura como default la distancia focal mínima (lo que
            // estaba configurado anteriormente por los chicos)
            else
            {
                Toast.makeText(activity,"Completar número de fotos, tiempo de exposición y sensibilidad",Toast.LENGTH_SHORT).show()
            }
        }

        // Botón de Calibración - Funcionalidad agregada para calibrar la recta
        botonCalibracion.setOnClickListener {
            if (numberOfPicturesTextView.text.isNotEmpty() && exposureTimeTextView.text.isNotEmpty() && sensitivityTextView.text.isNotEmpty() && focalDistanceTextView.text.isNotEmpty())
            {
                val numberOfPictures: Int = numberOfPicturesTextView.text.toString().toInt()
                val exposureTime: Long = (exposureTimeTextView.text.toString().toFloat()*1000000f).toLong()
                val sensitivity: Int = sensitivityTextView.text.toString().toInt()
                val focalDistance: Float = focalDistanceTextView.text.toString().toFloat()

                if (numberOfPictures<0)
                {
                    Toast.makeText(activity,"El número de fotos es negativo",Toast.LENGTH_SHORT).show()
                }
                else if (exposureTime < (tmin?: 0L) || exposureTime > (tmax?: (10000L*1000000L)))
                {
                    Toast.makeText(activity,"El tiempo de exposición no está en rango",Toast.LENGTH_SHORT).show()
                }
                else if (sensitivity<sensMin || sensitivity>sensMax)
                {
                    Toast.makeText(activity,"La sensibilidad no está en rango",Toast.LENGTH_SHORT).show()
                }
                else if (focalDistance < 1f/minimumFocusDistance*100f)
                {
                    Toast.makeText(activity,"Aumentar distancia focal",Toast.LENGTH_SHORT).show()
                }
                else
                {
                    // Navegar al fragment de calibración con modo calibración activado
                    val action = MenuFragmentDirections.actionMenuFragmentToMedirAbsorbanciaTest(
                        args.cameraId,
                        args.pixelFormat,
                        1, // Solo una foto para calibración
                        exposureTime,
                        sensitivity,
                        focalDistance
                    )
                    // Agregar parámetro adicional directamente al bundle
                    val bundle = action.arguments
                    bundle.putBoolean("isCalibrationMode", true)

                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(R.id.MedirAbsorbanciaTest, bundle)
                }
            }
            else if (numberOfPicturesTextView.text.isNotEmpty() && exposureTimeTextView.text.isNotEmpty() && sensitivityTextView.text.isNotEmpty() && focalDistanceTextView.text.isEmpty())
            {
                val numberOfPictures: Int = numberOfPicturesTextView.text.toString().toInt()
                val exposureTime: Long = (exposureTimeTextView.text.toString().toFloat()*1000000f).toLong()
                val sensitivity: Int = sensitivityTextView.text.toString().toInt()
                val focalDistance: Float = 1f/minimumFocusDistance*100f

                if (numberOfPictures<0)
                {
                    Toast.makeText(activity,"El número de fotos es negativo",Toast.LENGTH_SHORT).show()
                }
                else if (exposureTime < (tmin?: 0L) || exposureTime > (tmax?: (10000L*1000000L)))
                {
                    Toast.makeText(activity,"El tiempo de exposición no está en rango",Toast.LENGTH_SHORT).show()
                }
                else if (sensitivity<sensMin || sensitivity>sensMax)
                {
                    Toast.makeText(activity,"La sensibilidad no está en rango",Toast.LENGTH_SHORT).show()
                }
                else
                {
                    // Navegar al fragment de calibración con modo calibración activado
                    val action = MenuFragmentDirections.actionMenuFragmentToMedirAbsorbanciaTest(
                        args.cameraId,
                        args.pixelFormat,
                        1, // Solo una foto para calibración
                        exposureTime,
                        sensitivity,
                        focalDistance
                    )
                    // Agregar parámetro adicional directamente al bundle
                    val bundle = action.arguments
                    bundle.putBoolean("isCalibrationMode", true)

                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(R.id.MedirAbsorbanciaTest, bundle)
                }
            }
            else
            {
                Toast.makeText(activity,"Completar número de fotos, tiempo de exposición y sensibilidad",Toast.LENGTH_SHORT).show()
            }
        }

        titulo.setOnClickListener {
            val dialog = VentanaInfo()
            val fragmentManager = getChildFragmentManager()

            dialog.show(fragmentManager, "VentanaInfo")
        }

        if (tmin!=null&&tmax!=null){
            valoresPermitidos.text = "Características\nDimensiones de la imagen: ${w}x${h} píxeles\nRango de tiempo de " +
                    " exposición: ${tmin.toFloat() /1000000f}-${tmax.toFloat()  /1000000f} ms" +
                    "\nRango de sensibilidad: $sensMin-$sensMax (ISO 12232:2006)" +
                    "\nDistancia focal mínima: ${1f/minimumFocusDistance*100f} cm"
        } else {
            Toast.makeText(activity,"Error en la adquisición de rango de tiempo de exposición de la cámara",Toast.LENGTH_SHORT).show()
        }

        return view
    }
}