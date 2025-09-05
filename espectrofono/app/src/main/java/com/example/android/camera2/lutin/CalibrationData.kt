package com.example.android.camera2.lutin

import android.content.Context
import android.content.SharedPreferences

/**
 * Clase para manejar los datos de calibración de la recta del espectrómetro
 */
object CalibrationData {
    private const val PREF_NAME = "calibration_prefs"
    private const val KEY_IS_CALIBRATED = "is_calibrated"
    private const val KEY_M_0 = "m_0" // b (intercepto)
    private const val KEY_M_1 = "m_1" // m (pendiente)
    private const val KEY_POSICION_ORDEN_CERO = "posicion_orden_cero"
    private const val KEY_BLUE_X1 = "blue_x1"
    private const val KEY_TITA = "tita"

    // Nuevas claves para parámetros de medición
    private const val KEY_NUMBER_OF_PICTURES = "number_of_pictures"
    private const val KEY_EXPOSURE_TIME = "exposure_time"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val KEY_FOCAL_DISTANCE = "focal_distance"

    private var isCalibrated = false
    private var m: DoubleArray? = null
    private var posicionEnXOrdenCero: Int? = null
    private var blueX1: Int? = null
    private var tita: Double? = null

    /**
     * Guarda los datos de calibración
     */
    fun saveCalibrationData(
        context: Context,
        mParam: DoubleArray,
        posicionOrdenCero: Int,
        blueX1Param: Int,
        titaParam: Double
    ) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putBoolean(KEY_IS_CALIBRATED, true)
        editor.putFloat(KEY_M_0, mParam[0].toFloat())
        editor.putFloat(KEY_M_1, mParam[1].toFloat())
        editor.putInt(KEY_POSICION_ORDEN_CERO, posicionOrdenCero)
        editor.putInt(KEY_BLUE_X1, blueX1Param)
        editor.putFloat(KEY_TITA, titaParam.toFloat())

        editor.apply()

        // Actualizar variables en memoria
        this.isCalibrated = true
        this.m = mParam.clone()
        this.posicionEnXOrdenCero = posicionOrdenCero
        this.blueX1 = blueX1Param
        this.tita = titaParam
    }

    /**
     * Carga los datos de calibración desde SharedPreferences
     */
    fun loadCalibrationData(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        isCalibrated = prefs.getBoolean(KEY_IS_CALIBRATED, false)

        if (isCalibrated) {
            m = doubleArrayOf(
                prefs.getFloat(KEY_M_0, 0f).toDouble(),
                prefs.getFloat(KEY_M_1, 0f).toDouble()
            )
            posicionEnXOrdenCero = prefs.getInt(KEY_POSICION_ORDEN_CERO, 0)
            blueX1 = prefs.getInt(KEY_BLUE_X1, 0)
            tita = prefs.getFloat(KEY_TITA, 0f).toDouble()
        }
    }

    /**
     * Verifica si hay datos de calibración guardados
     */
    fun isCalibrated(context: Context): Boolean {
        if (!isCalibrated) {
            loadCalibrationData(context)
        }
        return isCalibrated
    }

    /**
     * Obtiene los datos de la recta calibrada
     */
    fun getCalibrationLine(context: Context): DoubleArray? {
        if (!isCalibrated(context)) {
            return null
        }
        return m
    }

    /**
     * Obtiene la posición del orden cero calibrado
     */
    fun getPosicionOrdenCero(context: Context): Int? {
        if (!isCalibrated(context)) {
            return null
        }
        return posicionEnXOrdenCero
    }

    /**
     * Obtiene la posición BlueX1 calibrada
     */
    fun getBlueX1(context: Context): Int? {
        if (!isCalibrated(context)) {
            return null
        }
        return blueX1
    }

    /**
     * Obtiene el ángulo tita calibrado
     */
    fun getTita(context: Context): Double? {
        if (!isCalibrated(context)) {
            return null
        }
        return tita
    }

    /**
     * Guarda los parámetros de medición
     */
    fun saveMeasurementParameters(
        context: Context,
        numberOfPictures: Int,
        exposureTime: Float, // en ms
        sensitivity: Int,
        focalDistance: Float
    ) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putInt(KEY_NUMBER_OF_PICTURES, numberOfPictures)
        editor.putFloat(KEY_EXPOSURE_TIME, exposureTime)
        editor.putInt(KEY_SENSITIVITY, sensitivity)
        editor.putFloat(KEY_FOCAL_DISTANCE, focalDistance)

        editor.apply()
    }

    /**
     * Carga los parámetros de medición guardados
     */
    fun loadMeasurementParameters(context: Context): MeasurementParameters? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Verificar si existen parámetros guardados
        if (!prefs.contains(KEY_NUMBER_OF_PICTURES)) {
            return null
        }

        return MeasurementParameters(
            numberOfPictures = prefs.getInt(KEY_NUMBER_OF_PICTURES, 10),
            exposureTime = prefs.getFloat(KEY_EXPOSURE_TIME, 100f),
            sensitivity = prefs.getInt(KEY_SENSITIVITY, 600),
            focalDistance = prefs.getFloat(KEY_FOCAL_DISTANCE, 15f)
        )
    }

    /**
     * Limpia los datos de calibración
     */
    fun clearCalibration(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()
        editor.apply()

        // Limpiar variables en memoria
        isCalibrated = false
        m = null
        posicionEnXOrdenCero = null
        blueX1 = null
        tita = null
    }

    /**
     * Data class para los parámetros de medición
     */
    data class MeasurementParameters(
        val numberOfPictures: Int,
        val exposureTime: Float, // en ms
        val sensitivity: Int,
        val focalDistance: Float
    )
}
