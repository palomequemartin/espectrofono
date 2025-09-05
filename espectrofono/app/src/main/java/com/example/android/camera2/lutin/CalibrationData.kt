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
}
