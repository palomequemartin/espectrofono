package com.example.android.camera2.lutin.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.luciocoro.lutin.R
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet


class FiguresFragment : Fragment() {

    // Navigation arguments
    private val args: FiguresFragmentArgs by navArgs()



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.figures_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val botonContinuar : ImageButton = view.findViewById(R.id.botonContinuar)
        val backButton : ImageButton = view.findViewById(R.id.backButton)

        botonContinuar.setOnClickListener {
            findNavController().navigate(
                R.id.permissions_fragment,
                null,
                NavOptions.Builder()
                    .setPopUpTo(findNavController().graph.startDestinationId, true)
                    .build()
            )
        }
        backButton.setOnClickListener {
            findNavController().navigate(
                FiguresFragmentDirections.actionFiguresFragmentToPerfiles(
                    args.cameraId, args.blueOrder1, args.redOrder1,
                    args.greenOrder1,args.grisesSinMuestra, args.grisesConMuestra,
                    args.blueOrder2, args.redOrder2,args.greenOrder2,
                    args.listaIndices,args.posicionEnXOrden0,args.posicionEnXMaxBlue1,
                    args.numberOfPictures,args.exposureTime,args.sensitivity,
                    args.focalDistance
                )
            )
        }

        val intensityChart: LineChart = view.findViewById(R.id.intensity_chart)

        // Plot intensity data (example for blueOrder1)
        args.blueOrder1.let { blue1 ->
            val entries = blue1.mapIndexed { index, value ->
                Entry(index.toFloat(), value)
            }
            val dataSet = LineDataSet(entries, "Blue Order 1 Intensity")
            dataSet.color = Color.BLUE
            dataSet.setDrawCircles(false)
            val lineData = LineData(dataSet)
            intensityChart.data = lineData
            intensityChart.description.text = "Intensity vs Pixel Index"
            intensityChart.invalidate() // Refresh chart
        }

        }
}
