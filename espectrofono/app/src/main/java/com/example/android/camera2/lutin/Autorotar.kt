package com.example.calibrarlongituddeonda

import android.graphics.Bitmap
import android.graphics.Color
import org.apache.commons.math3.complex.Complex
import kotlin.math.pow

class Autorotar (myBitmap: Bitmap){

    private val thisBitmap: Bitmap = myBitmap
    private var alto = myBitmap.height
    private val ancho = myBitmap.width

    private fun pendiente(myBitmap: Bitmap ): Quadruple<DoubleArray,MutableList<Double>,MutableList<Double>, Int>? {
        //val maximosI = arrayListOf<Double>()
        val maximosX = arrayListOf<Double>()
        val maximosY = arrayListOf<Double>()

        /* BUSQUEDA POR PROMEDIOS
        for(x in 250 until (ancho-1)/5) {

            var iteradorI = 0
            var iteradorY = 0
            var intensidadTotal = 0
            var yPromedio = 0
            for (y in 0 until alto-1) { //Ahora mas rapido
                val argb = myBitmap.getPixel(5*x, y)
                intensidadTotal += (Color.red(argb) + Color.blue(argb) + Color.green(argb))
            }
            for (y in 0 until alto-1) {
                val argb = myBitmap.getPixel(5*x, y)
                var intensidad = Color.red(argb) + Color.blue(argb) + Color.green(argb)
                yPromedio += y*intensidad/intensidadTotal
            }
            maximosX.add(x.toDouble())
            maximosY.add(yPromedio.toDouble())
        }


//        println("Y=np.array($maximosYFiltrada)")
//        println("X=np.array($maximosXFiltrada)")
//        println("I=np.array($maximosIFiltrada)")
        if (maximosX.size > 0) {
            val m = Regresion().getPolynomialFitter(maximosX, maximosY,1)
            //println("m = ${m[1]}") //b ahora es m y m es b y ambas son m, tipico
            //println("b = ${m[0]}")
            return m

//            val list: MutableList<Double> = m.toMutableList()
//            list.add(maximosYFiltrada.first())
//            list.add((maximosYFiltrada.last()))
//            //println("last=${maximosY.last()}")
//            return list.toDoubleArray()
        } else {
            println("No se encontró ningún maximo")
            return DoubleArray(3)
        }

         */
        var yMax : Int
        var IMax : Int
        var intensidad : Int
        var listaIntAMediaAltura : MutableList<Int>
        var listasDeListasDeIntensidades = mutableListOf<MutableList<Int>>()
        var listaDePosicionesEIntensidadesMaximas = mutableListOf<List<Int>>()

        // Nuevo: almacenar información de regiones saturadas
        var regionesSaturadas = mutableListOf<Pair<Int, Int>>() // (posicion_x, cantidad_saturados)

        var longitudDeListasDeIntensidadesSaturadas : Int
        var posicionDeListasDeIntensidadesSaturadas = 0

        longitudDeListasDeIntensidadesSaturadas = 0
        posicionDeListasDeIntensidadesSaturadas = 0

        for (x in 0 until (ancho-1)/5) {
            yMax = 0
            IMax = 0
            var listaIntensidades = mutableListOf<Int>()
            var listaIntensidadesSaturadas = mutableListOf<Int>()
            for (y in 0 until alto-1) { //Ahora mas rapido   INE: Más rápido?
                val argb = myBitmap.getPixel(5*x, y)
                intensidad = (Color.red(argb) + Color.blue(argb) + Color.green(argb)) //INE: Suma RGB de los pixeles

                listaIntensidades.add(intensidad)
                if (intensidad >= 500 ) {
                    listaIntensidadesSaturadas.add(intensidad)  //Armamos una lista con intensidades saturadas para cada linea vertical. Luego vamos a ver
                }                                               // cual es la lista con más elementos para ubicar el centro de patron
                if (intensidad>IMax) {
                    IMax  = intensidad  //INE: Mira pixel a pixel los valores de intensidad maximos saturados
                    yMax = y            //va comparando los valores hasta quedarse con el maximo y su posición
                }
            }   //INE: Para un x fijo, mira todos los y, después va a iterar sobre los x

            listasDeListasDeIntensidades.add(listaIntensidades) //INE: Agrega la lista de intensidades para un x
            listaDePosicionesEIntensidadesMaximas.add(listOf(IMax,yMax,5*x)) //y la IMax y yMax

            // Almacenar información de regiones saturadas
            if (listaIntensidadesSaturadas.size > 0) {
                regionesSaturadas.add(Pair(5*x, listaIntensidadesSaturadas.size))
            }
        }

        // Nuevo algoritmo para filtrar reflexiones espurias
        posicionDeListasDeIntensidadesSaturadas = encontrarOrdenCeroReal(regionesSaturadas, ancho)

        val deltaIzq = listOf(kotlin.math.abs(posicionDeListasDeIntensidadesSaturadas),500).min()  //INE: Por qué el mínimo entre la posición en x donde están las últimas intensidades saturadas y 500?
        val deltaDer = listOf(kotlin.math.abs(posicionDeListasDeIntensidadesSaturadas-ancho),500).min()
        //Nos paramos en el centro del patron (region saturada). Vamos a recortar 500 en ambas direcciones para mejorar el ajuste de la recta.
        //val listaDeXFiltrada = (0..(((posicionDeListasDeIntensidadesSaturadas-deltaIzq)/5).toInt()))+(((posicionDeListasDeIntensidadesSaturadas+deltaDer)/5).toInt() until ancho/5)
        val listaDeXFiltrada = (0..((posicionDeListasDeIntensidadesSaturadas-deltaIzq)/5)).toList()

        println("LISTA X FILTRADA   " +listaDeXFiltrada.size)
        println("LISTA Y   "+listaDePosicionesEIntensidadesMaximas.size)
        val L = listaDeXFiltrada.size
        var x : Int
        var x1 : Int
        val lista = (0 until (ancho-1)/5).toList()
        for (n in 0 until (lista.size-1)) {
            x=lista[n]
            x1=lista[n+1]
            if (x !in listaDeXFiltrada) {
                continue
            }

            listaIntAMediaAltura= mutableListOf()
            IMax = listaDePosicionesEIntensidadesMaximas[n][0]
            if (IMax < 0) {
                continue //siga siga
            } else {
                for (m in 0 until listasDeListasDeIntensidades[n].size) {
                    intensidad = listasDeListasDeIntensidades[n][m]
                    if (intensidad >= IMax*0.6 ) {
                        listaIntAMediaAltura.add(m)
                    }
                }
                var y = listaIntAMediaAltura.average().toDouble()
                if (y<alto/3||y>2*alto/3) {
                    continue
                }
                maximosX.add((5*x).toDouble())
                maximosY.add(listaIntAMediaAltura.average().toDouble())
            }
        }
        //println("Estoy aca")
        println(maximosX.size)
        println(maximosY.size)
        val filtrado = filtroDePuntosFueraDeRecta(maximosX,maximosY,10f,10)
        //println("Estoy aca 2")

        if (filtrado != null)
        {
            val maximosXFiltrada = filtrado.first
            val maximosYFiltrada = filtrado.second

            maximosXFiltrada.removeAt(0)
            maximosXFiltrada.removeAt(0)
            maximosXFiltrada.removeAt(0)


            maximosXFiltrada.removeAt(maximosXFiltrada.lastIndex)
            maximosXFiltrada.removeAt(maximosXFiltrada.lastIndex)
            maximosXFiltrada.removeAt(maximosXFiltrada.lastIndex)

            maximosYFiltrada.removeAt(0)
            maximosYFiltrada.removeAt(0)
            maximosYFiltrada.removeAt(0)

            maximosYFiltrada.removeAt(maximosYFiltrada.lastIndex)
            maximosYFiltrada.removeAt(maximosYFiltrada.lastIndex)
            maximosYFiltrada.removeAt(maximosXFiltrada.lastIndex)

//        println("Y=np.array($maximosYFiltrada)")
//        println("X=np.array($maximosXFiltrada)")
//        println("I=np.array($maximosIFiltrada)")
            if (maximosXFiltrada.size > 0) {
                val m = Regresion().getPolynomialFitter(maximosXFiltrada, maximosYFiltrada,1)
                //println("m = ${m[1]}") //b ahora es m y m es b y ambas son m, tipico
                //println("b = ${m[0]}")
                return Quadruple(m,maximosXFiltrada,maximosYFiltrada,posicionDeListasDeIntensidadesSaturadas)

//            val list: MutableList<Double> = m.toMutableList()
//            list.add(maximosYFiltrada.first())
//            list.add((maximosYFiltrada.last()))
//            //println("last=${maximosY.last()}")
//            return list.toDoubleArray()
            } else {
                println("No se encontró ningún maximo")
                return Quadruple(DoubleArray(3),maximosXFiltrada,maximosYFiltrada,posicionDeListasDeIntensidadesSaturadas)
            }
        }
        else
        {
            return null
        }

    }

    // Nueva función para encontrar el orden cero real filtrando reflexiones espurias
    private fun encontrarOrdenCeroReal(regionesSaturadas: MutableList<Pair<Int, Int>>, ancho: Int): Int {
        if (regionesSaturadas.isEmpty()) return ancho/2

        // Filtrar regiones que están muy cerca de los bordes (probables reflexiones)
        val margenBorde = ancho * 0.1 // 10% del ancho desde cada borde
        val regionesFiltradas = regionesSaturadas.filter {
            it.first > margenBorde && it.first < ancho - margenBorde
        }

        if (regionesFiltradas.isEmpty()) {
            // Si no hay regiones centrales, usar la región más grande
            return regionesSaturadas.maxByOrNull { it.second }?.first ?: ancho/2
        }

        // Buscar grupos de regiones saturadas contiguas
        val gruposRegiones = mutableListOf<MutableList<Pair<Int, Int>>>()
        var grupoActual = mutableListOf<Pair<Int, Int>>()

        val regionesOrdenadas = regionesFiltradas.sortedBy { it.first }
        val toleranciaDistancia = ancho * 0.05 // 5% del ancho para considerar regiones contiguas

        for (i in regionesOrdenadas.indices) {
            if (grupoActual.isEmpty()) {
                grupoActual.add(regionesOrdenadas[i])
            } else {
                val distancia = kotlin.math.abs(regionesOrdenadas[i].first - grupoActual.last().first)
                if (distancia <= toleranciaDistancia) {
                    grupoActual.add(regionesOrdenadas[i])
                } else {
                    if (grupoActual.size >= 3) { // Solo considerar grupos con al menos 3 regiones contiguas
                        gruposRegiones.add(grupoActual.toMutableList())
                    }
                    grupoActual.clear()
                    grupoActual.add(regionesOrdenadas[i])
                }
            }
        }
        if (grupoActual.size >= 3) {
            gruposRegiones.add(grupoActual)
        }

        if (gruposRegiones.isEmpty()) {
            // Si no hay grupos coherentes, usar la región más central con más píxeles saturados
            val centroImagen = ancho / 2
            return regionesFiltradas.minByOrNull {
                kotlin.math.abs(it.first - centroImagen) - it.second * 0.1
            }?.first ?: centroImagen
        }

        // Seleccionar el grupo más central y con mayor densidad de píxeles saturados
        val mejorGrupo = gruposRegiones.maxByOrNull { grupo ->
            val centroImagen = ancho / 2
            val centroGrupo = grupo.map { it.first }.average()
            val totalSaturados = grupo.sumOf { it.second }
            val distanciaAlCentro = kotlin.math.abs(centroGrupo - centroImagen)

            // Función de puntuación que favorece grupos centrales con muchos píxeles saturados
            totalSaturados * 1.0 - distanciaAlCentro * 0.5
        }

        // Retornar la posición central del mejor grupo
        return mejorGrupo?.let { grupo ->
            grupo.maxByOrNull { it.second }?.first ?: ancho/2
        } ?: ancho/2
    }

    var m: DoubleArray? = null
    var tita: Double? = null
    var listaMaximosX: MutableList<Double>? = null
    var listaMaximosY: MutableList<Double>? = null
    var posicionEnXOrdenCero: Int? = null

    public fun encontrarRecta()
    {
        val resultado = pendiente(thisBitmap)
        if (resultado!=null)
        {
            m  = resultado.first
            tita = 180*kotlin.math.atan(m!![1])/kotlin.math.PI
            listaMaximosX = resultado.second
            listaMaximosY = resultado.third
            posicionEnXOrdenCero = resultado.fourth
        }
    }
}

fun filtroDePuntosFueraDeRecta(listaX : MutableList<Double>, listaY : MutableList<Double>, tolerancia : Float, iteraciones : Int) : Pair<MutableList<Double>,MutableList<Double>>? {

    var P1 : List<Double>
    var P2 : List<Double>
    var P3 : List<Double>
    var P4 : List<Double>
    var P5 : List<Double>

    var V1 : MutableList<Float>
    var V2 : MutableList<Float>
    var V3 : MutableList<Float>
    var V4 : MutableList<Float>

    var norm1 : Float
    var norm2 : Float
    var norm3 : Float
    var norm4 : Float

    var listaX0 = listaX.toMutableList()
    var listaY0 = listaY.toMutableList()
    val listaParalelaX = listaX.toMutableList()
    val listaParalelaY = listaY.toMutableList()

    var theta1 : Double
    var theta2 : Double
    var theta3 : Double

    var L : Int

    var n = 0
    var indexCorrector : Int
    var flag = true

    while (n < 10 && flag) {

        flag = false

        indexCorrector = 0

        L = listaX0.size

        if (L < 5) {
            return null
        }


        for (k in 2 until L-2) {
            //println(k.toString()+"  de  "+L)

            P1 = listOf(listaX0[k-2],listaY0[k-2])
            P2 = listOf(listaX0[k-1],listaY0[k-1])
            P3 = listOf(listaX0[k],listaY0[k])
            P4 = listOf(listaX0[k+1],listaY0[k+1])
            P5 = listOf(listaX0[k+2],listaY0[k+2])

            V1 = mutableListOf((P2[0]-P1[0]).toFloat(),(P2[1]-P1[1]).toFloat())
            V2 = mutableListOf((P3[0]-P1[0]).toFloat(),(P3[1]-P1[1]).toFloat())
            V3 = mutableListOf((P4[0]-P1[0]).toFloat(),(P4[1]-P1[1]).toFloat())
            V4 = mutableListOf((P5[0]-P1[0]).toFloat(),(P5[1]-P1[1]).toFloat())

            norm1 = kotlin.math.sqrt((V1[0]).pow(2)+V1[1].pow(2))
            norm2 = kotlin.math.sqrt((V2[0]).pow(2)+V2[1].pow(2))
            norm3 = kotlin.math.sqrt((V3[0]).pow(2)+V3[1].pow(2))
            norm4 = kotlin.math.sqrt((V4[0]).pow(2)+V4[1].pow(2))

            V1[0]/=norm1
            V1[1]/=norm1
            V2[0]/=norm2
            V2[1]/=norm2
            V3[0]/=norm3
            V3[1]/=norm3
            V4[0]/=norm4
            V4[1]/=norm4

            theta1 = kotlin.math.acos(V1[0]*V2[0]+V1[1]*V2[1])/kotlin.math.PI*180
            theta2 = kotlin.math.acos(V1[0]*V3[0]+V1[1]*V3[1])/kotlin.math.PI*180
            theta3 = kotlin.math.acos(V1[0]*V4[0]+V1[1]*V4[1])/kotlin.math.PI*180

            if (!(theta1 < tolerancia && theta2 < tolerancia && theta3 < tolerancia)) {
                listaParalelaX.removeAt(k-indexCorrector)
                listaParalelaY.removeAt(k-indexCorrector)
                indexCorrector += 1
                flag = true
            }

        }

        listaX0 = listaParalelaX.toMutableList()
        listaY0 = listaParalelaY.toMutableList()


        n+=1

    }

    return Pair(listaX0,listaY0)

}

public open class Quadruple<A,B,C,D> (first: A,second: B, third: C, fourth: D)
{
    public var first: A = first
    public var second: B = second
    public var third: C = third
    public var fourth: D = fourth
}
