import numpy as np
import matplotlib.pyplot as plt
from scipy.optimize import curve_fit
from scipy.signal import find_peaks, correlate
from scipy.interpolate import interp1d
import sympy as sp
import pandas as pd


def lineal(x, m, b):
    return m * x + b

def difraccion(p, a, b, p0):
    return 1 / ((a / (p0 - p)**2) + b)**0.5

def gaussiana(x, amplitud, media, sigma, offset):
    return amplitud * np.exp(-0.5 * ((x - media) / sigma)**2) + offset

def propagacion(funcion, x, popt, pcov):
    n_params = funcion.__code__.co_argcount
    params = sp.symbols(funcion.__code__.co_varnames[:n_params])

    funcion_sympy = funcion(*params)

    derivadas = []
    for i in range(1, n_params):
        derivada = sp.lambdify(params, sp.diff(funcion_sympy, params[i]).simplify())
        derivadas.append(derivada(x, *popt))

    error = np.zeros(len(x))
    for i in range(n_params-1):
        for j in range(i, n_params-1):
            error += derivadas[i]*derivadas[j]*pcov[i, j]*(2**(i!=j))

    return error

def encontrar_cruces(y_data, umbral):
    """
    Encuentra los índices donde los datos cruzan una línea horizontal,
    devolviendo el punto más cercano al umbral en cada cruce.
    
    Parameters:
    -----------
    y_data : array-like
        Serie de datos donde buscar los cruces
    umbral : float
        Valor de la línea horizontal
        
    Returns:
    --------
    list
        Lista de índices donde ocurren los cruces (punto más cercano al umbral)
    """
    y_array = np.array(y_data)
    cruces = []
    
    # Buscar cambios de signo en la diferencia con el umbral
    diff = y_array - umbral
    
    for i in range(len(diff) - 1):
        # Detectar cruce: cambio de signo entre puntos consecutivos
        if diff[i] * diff[i + 1] < 0:
            # Encontrar cuál de los dos puntos está más cerca del umbral
            dist_i = abs(diff[i])      # Distancia del punto i al umbral
            dist_i1 = abs(diff[i + 1]) # Distancia del punto i+1 al umbral
            
            # Elegir el índice del punto más cercano
            if dist_i <= dist_i1:
                cruces.append(i)
            else:
                cruces.append(i + 1)
    
    return cruces

def encontrar_picos(n_pixel, gris, umbral):
    """
    Finds spectral peaks in grayscale data for calibration purposes.
    
    This function identifies 5 specific spectral peaks from a known light source 
    by using threshold crossings to segment
    the data and then applying different peak detection methods for different
    spectral regions.
    
    The function expects a specific pattern of 8 threshold crossings that divide
    the spectrum into regions containing known spectral lines for calibration.
    
    Parameters:
    -----------
    n_pixel : array-like
        Pixel numbers along the sensor (x-axis coordinates)
    gris : array-like
        Grayscale intensity values corresponding to each pixel
    umbral : float
        Threshold value used to segment the spectrum into regions
        
    Returns:
    --------
    numpy.ndarray
        Array of 5 peak positions (in pixel coordinates) corresponding to
        known spectral calibration lines
    """
    n_pixel = np.array(n_pixel)
    gris = np.array(gris)
    
    picos = []
    
    cruces = encontrar_cruces(gris, umbral)
    
    primer_pico, _ = find_peaks(gris[:cruces[0]], threshold=0.001, distance=len(gris[:cruces[0]]))
    picos.append(n_pixel[primer_pico[0]])
    
    for i in [[0, 1], [2, 3]]:
        
        inicio = cruces[i[0]]
        final = cruces[i[1]] + 1
        
        x_cortado = n_pixel[inicio:final]
        y_cortado = gris[inicio:final]
        
        picos.append(n_pixel[np.argmax(y_cortado) + inicio])
    
    for i in [[4, 5], [6, 7]]:
        inicio = cruces[i[0]]
        final = cruces[i[1]] + 1

        x_cortado = n_pixel[inicio:final]
        y_cortado = gris[inicio:final]
        
        p0 = [np.max(y_cortado) - np.min(y_cortado),
              x_cortado[np.argmax(y_cortado)],
              (x_cortado[-1] - x_cortado[0]) / 4,
              np.min(y_cortado)]

        popt, pcov = curve_fit(gaussiana, x_cortado, y_cortado, p0=p0, maxfev=1000)
        
        picos.append(popt[1])
        
    return np.array(picos)

def encontrar_umbral_optimo(y_data, tolerancia=1):
    """
    Encuentra el umbral óptimo que produce cruces con diferencias cercanas a las objetivo.
    
    Parameters:
    -----------
    y_data : array-like
        Serie de datos donde buscar los cruces
    tolerancia : float
        Tolerancia relativa para considerar una diferencia como válida
        
    Returns:
    --------
    float
        Umbral óptimo encontrado
    """
    y_array = np.array(y_data)
    
    diferencias_objetivo = np.array([23, 14, 23, 68, 132, 52, 61])
    n_cruces_objetivo = len(diferencias_objetivo) + 1
    
    umbral_min = 0.01
    umbral_max = 0.8
    n_umbrales = 5000
    umbrales = np.linspace(umbral_min, umbral_max, n_umbrales)
    
    mejor_umbral = None
    mejor_puntuacion = float('inf')
    candidatos = []
    
    for umbral in umbrales:
        cruces = encontrar_cruces(y_array, umbral)
        
        if len(cruces) != n_cruces_objetivo:
            continue
        
        diferencias_actuales = np.diff(cruces)
        
        error_relativo = np.abs(diferencias_actuales - diferencias_objetivo) / diferencias_objetivo
        puntuacion = np.mean(error_relativo)
        
        candidatos.append({
            'umbral': umbral,
            'diferencias': diferencias_actuales,
            'error_relativo': error_relativo,
            'puntuacion': puntuacion
        })
        
        if np.all(error_relativo <= tolerancia) and puntuacion < mejor_puntuacion:
            mejor_puntuacion = puntuacion
            mejor_umbral = umbral
    
    if mejor_umbral is None and candidatos:
        mejor_candidato = min(candidatos, key=lambda x: x['puntuacion'])
        mejor_umbral = mejor_candidato['umbral']
        mejor_puntuacion = mejor_candidato['puntuacion']
    
    return mejor_umbral

def calibracion_ldo(n_pixel, gris):
    picos_thorlabs = np.array([453.6733242631141, 540.0395034709993, 615.1063232, 632.8643188, 649.3109741])[::-1]
    
    umbral = encontrar_umbral_optimo(gris)
    picos = encontrar_picos(n_pixel, gris, umbral)

    popt, pcov = curve_fit(difraccion, picos, picos_thorlabs, maxfev=1000000,
                           p0=[1.26777218, 6.78704522e-13, 2e3])
    
    return popt

def generar_espectro_thorlabs(corriente_azul, corriente_calido):
    df = pd.read_csv('./espectro_leds.csv')
    ldo = df['longitudes_de_onda'].to_numpy()
    intensidad = []
    for i in range(len(ldo)):
        intensidad_azul = lineal(corriente_azul, df['pendiente_azul'][i], df['ordenada_azul'][i])
        intensidad_calido = lineal(corriente_calido, df['pendiente_calido'][i], df['ordenada_calido'][i])
        intensidad.append(intensidad_azul + intensidad_calido)

    intensidad = np.array(intensidad)
    return ldo, intensidad

def calibracion_ldo_pixel(nro_pixel,gris,full_output=False):
    if full_output:
        ldo, popt, pcov = calibracion_ldo(nro_pixel,gris,full_output=full_output)
        return ldo, popt, pcov
    ldo = calibracion_ldo(nro_pixel,gris)
    return ldo

def cargar_datos(path,delimiter = '\t'):
    """
    Loads raw spectral data from a CSV file and calculates average grayscale values.
    
    Parameters:
    -----------
    path : str
        File path to the CSV file containing spectral measurement data
        
    Returns:
    --------
    tuple
        - nro_pixel: pandas Series with pixel numbers (x-axis data)
        - gris promedio: pandas Series with averaged grayscale values (y-axis data)
    """
    # Load the CSV file into a pandas DataFrame
    # Expected format: columns include 'Nro. de pixel' and multiple grayscale measurement columns
    df = pd.read_csv(path, delimiter='\t')
    
    # Extract the pixel number column - this serves as the x-coordinate
    # for plotting spectral data (pixel position on the sensor)
    nro_pixel = df['Nro. de pixel'].to_numpy()
    
    # Calculate the average grayscale value across multiple measurement columns
    # df.iloc[:, 1:7] selects columns 1 through 6 (0-indexed), which typically contain
    # grayscale values from different color channels or repeated measurements
    # .mean(axis=1) calculates the row-wise average (across columns for each pixel)
    df['gris promedio'] = df.iloc[:, 1:7].mean(axis=1).to_numpy()

    # Return the pixel numbers and their corresponding average grayscale intensities
    # This data can be used for spectral analysis, calibration, or plotting
    return nro_pixel, df['gris promedio']


def gris_celular_bineado(path,ldo_min,ldo_max,step,popt_calibracion_ldo,version_vieja=False):
    """Datafframe: Datos guardados del celular. Binea los datos entre las ldo y step dados. La intensidad 
        de cada bin es el promedio de los valores de gris en ese bin y está normalizada."""
    if version_vieja:
        bins = np.arange(ldo_min, ldo_max + step, step)

        df = pd.read_csv(path,skiprows=1, delimiter= '\t',
                        names=['Nro. de pixel','R_blanco','G_blanco','B_blanco','R_muestra','G_muestra','B_muestra'])
        
        df['gris promedio'] = df.iloc[:, 1:].mean(axis=1)        

        df['ldo'] = difraccion(df['Nro. de pixel'].to_numpy(), *popt_calibracion_ldo)
        df = df.sort_values(by='ldo')
        df = df[(df['ldo'] >= ldo_min) & (df['ldo'] <= ldo_max)]

        intensidad_por_bin = []
        for l in range(len(bins)-1):
            intensidad_por_bin.append((df[(df['ldo'] >= bins[l]) & (df['ldo'] < bins[l+1])]['gris promedio'].mean()))

        intensidad_por_bin = np.array(intensidad_por_bin)


        return intensidad_por_bin

    bins = np.arange(ldo_min, ldo_max + step, step)

    df = pd.read_csv(path)
    
    df['gris promedio'] = df.iloc[:, 1:7].mean(axis=1)        

    df['ldo'] = difraccion(df['Nro. de pixel'].to_numpy(), *popt_calibracion_ldo)
    df = df.sort_values(by='ldo')
    df = df[(df['ldo'] >= ldo_min) & (df['ldo'] <= ldo_max)]

    intensidad_por_bin = []
    for l in range(len(bins)-1):
        intensidad_por_bin.append((df[(df['ldo'] >= bins[l]) & (df['ldo'] < bins[l+1])]['gris promedio'].mean()))

    intensidad_por_bin = np.array(intensidad_por_bin)


    return intensidad_por_bin

def intensidad_celular_calibrada(path,path_calibracion_intensidad,ldo_min,ldo_max,step,popt_calibracion_ldo,version_vieja):
    """Datafframe: Datos guardados del celular. Binea los datos entre las ldo y step dados. La intensidad 
        de cada bin es el promedio de los valores de gris en ese bin y está normalizada. Ajusta la intensidad de
        cada bin según los parametros obtenidos."""
    if version_vieja:
        bins = np.arange(ldo_min, ldo_max + step, step)

        df = pd.read_csv(path,skiprows=1, delimiter= '\t',
                        names=['Nro. de pixel','R_blanco','G_blanco','B_blanco','R_muestra','G_muestra','B_muestra'])
        
        df['gris promedio'] = df.iloc[:, 1:].mean(axis=1)        

        df['ldo'] = difraccion(df['Nro. de pixel'].to_numpy(), *popt_calibracion_ldo)
        df = df.sort_values(by='ldo')
        df = df[(df['ldo'] >= ldo_min) & (df['ldo'] <= ldo_max)]

        intensidad_por_bin = []
        for l in range(len(bins)-1):
            intensidad_por_bin.append((df[(df['ldo'] >= bins[l]) & (df['ldo'] < bins[l+1])]['gris promedio'].mean()))

        intensidad_por_bin = np.array(intensidad_por_bin)


        return intensidad_por_bin
    bins = np.arange(ldo_min, ldo_max + step, step)
    df_calibracion_intensidad = pd.read_csv(path_calibracion_intensidad)
    df = pd.read_csv(path,skiprows=3,
                     names=['Nro. de pixel','R_blanco','G_blanco','B_blanco','R_muestra','G_muestra','B_muestra'])

    df['gris promedio'] = df.iloc[:, 1:].mean(axis=1)        

    df['ldo'] = difraccion(df['Nro. de pixel'].to_numpy(), *popt_calibracion_ldo)
    df = df.sort_values(by='ldo')
    df = df[(df['ldo'] >= ldo_min) & (df['ldo'] <= ldo_max)]

    popts = []
    for k in range(len(bins[:-1])):
        ldo = bins[k]
        popt_array = df_calibracion_intensidad.loc[df_calibracion_intensidad['Longitud de onda'] == ldo].values.flatten()[2:]
        parameters = np.array(popt_array)
        popts.append(parameters)

    intensidad_por_bin = []
    for l in range(len(bins)-1):
        intensidad_por_bin.append((df[(df['ldo'] >= bins[l]) & (df['ldo'] < bins[l+1])]['Gris'].mean())/255)

    intensidad_por_bin = np.array(intensidad_por_bin)

    intensidad_por_bin_calibrada = []
    for m in range(len(bins)-1):
        poly = np.polynomial.Polynomial(popts[m])
        intensidad_por_bin_calibrada.append(poly(intensidad_por_bin[m]))
        
    intensidad_por_bin_calibrada = np.array(intensidad_por_bin_calibrada)

    return intensidad_por_bin_calibrada

def intensidad_thorlabs_binned(path,ldo_min, ldo_max, step):
    """Binea los datos del espectrómetro Thorlabs entre las ldo y step dados.
     La intensidad de cada bin es el promedio de los valores de intensidad en ese bin."""
    intensidad_por_bin = []
    bins = np.arange(ldo_min, ldo_max + step, step)

    ldo_e, i_e = np.loadtxt(f'{path}',delimiter=',', unpack=True)
    i_e = i_e[(ldo_e >= ldo_min) & (ldo_e <= ldo_max)]
    ldo_e = ldo_e[(ldo_e >= ldo_min) & (ldo_e <= ldo_max)]
    df_e  = pd.DataFrame({'ldo': ldo_e, 'i': i_e}) 

    for l in range(len(bins)-1):
        intensidad_por_bin.append((df_e[(df_e['ldo'] >= bins[l]) & (df_e['ldo'] < bins[l+1])]['i'].mean()))
    intensidad_por_bin = np.array(intensidad_por_bin)
    return intensidad_por_bin

def plot_thorlabs_data(path, ldo_min, ldo_max,step):
    """Grafica los datos del espectrómetro Thorlabs entre las ldo dadas, binneando los datos."""
    data = intensidad_thorlabs_binned(path,ldo_min,ldo_max,step)
    bins = np.arange(ldo_min, ldo_max, step)
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(bins,data)
    ax.set_xlabel('Longitud de onda (nm)')
    ax.set_ylabel('Intensidad (u.a)')
    ax.set_title(f'{path}')

def plot_thorlabs(path,ldo_min,ldo_max):
    """Grafica los datos del espectrómetro Thorlabs entre las ldo dadas."""
    ldo_e, i_e = np.loadtxt(path,delimiter=',', unpack=True)
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(ldo_e,i_e)
    ax.set_xlabel('Longitud de onda (nm)')
    ax.set_ylabel('Intensidad (u.a)')
    ax.set_title(f'{path}')

def open_thorlabs_measurement(path,delimiter = ','):
    """Abre los datos del espectrómetro Thorlabs."""
    ldo_e, i_e = np.loadtxt(path,delimiter=delimiter, unpack=True)
    return ldo_e, i_e

def load_LED_measurements(folder_path,filter_1,filter_2):
    """Carga los datos de las mediciones de los LEDs. Toma como input el path de la
    carpeta donde se encuentran las mediciones y devuelve una lista con tuplas en cada elemento.
    Cada tupla contiene el nombre del archivo y el valor de corriente del LED en mA.
    Filter_1 y filter_2 son strings que indican el texto que está antes y después del valor de corriente"""
    import os

    neutral_led_currents = []
    file_with_values = []
    for filename in os.listdir(folder_path): #os.listdir() lista todos los archivos en el path dado
    #De esta forma lee el archivo y guarda el valor de corriente sin importar el orden en el que acceda
        value = filename.split(f'{filter_1}')[1].split(f'{filter_2}')[0]
        neutral_led_currents.append(value)
        file_with_values.append((filename,value))

    return file_with_values

def pixel_to_ldo(nro_pixel,popt_calibracion_ldo):
    """Convierte de número de pixel a longitud de onda usando los parámetros de calibración dados."""
    ldo = difraccion(nro_pixel, *popt_calibracion_ldo)
    return ldo

def binear_intensidad(ldo, intensidad, bins):
    intensidad_bineada = np.zeros(len(bins) - 1)
    for i in range(len(bins) - 1):
        intensidad_bineada[i] = np.mean(intensidad[(ldo >= bins[i]) & (ldo < bins[i + 1])])
    return intensidad_bineada

def generar_archivo_calibracion_intensidad(corrientes_azul, corrientes_calido, ldos, grises, step, deg, ldo_min=350, ldo_max=700):
    
    corr_i_list = []
    corr_ldo_list = []
    for i in range(len(corrientes_azul)):
        ldo = ldos[i]
        gris = grises[i]
        
        ldo_thorlabs, intensidad_thorlabs = generar_espectro_thorlabs(corrientes_azul[i], corrientes_calido[i])
        corr_i = np.mean(gris[ldo >= ldo_min][:20]) - np.mean(intensidad_thorlabs[ldo_thorlabs >= ldo_min][:20])
        corr_i_list.append(corr_i)
        
        corr_ldo = np.max(ldo[(ldo >= 400) & (ldo <= 500)]) - np.max(ldo_thorlabs[(ldo_thorlabs >= 425) & (ldo_thorlabs <= 475)])
        corr_ldo_list.append(corr_ldo)
    corr_i = np.mean(corr_i_list)
    corr_ldo = np.mean(corr_ldo_list)
    # print(corr_ldo)
    corr_i = 0
    corr_ldo = 0
    
    bins = np.arange(ldo_min, ldo_max + step, step)
    
    intensidades_thorlabs = []
    intensidades_celular = []
    for i in range(len(corrientes_azul)):
        ldo_thorlabs, intensidad_thorlabs = generar_espectro_thorlabs(corrientes_azul[i], corrientes_calido[i])
        # intensidad_thorlabs /= np.max(intensidad_thorlabs)

        intensidad_thorlabs_bineada = binear_intensidad(ldo_thorlabs, intensidad_thorlabs, bins)
        intensidades_thorlabs.append(intensidad_thorlabs_bineada)
        
        intensidad_celular_bineada = binear_intensidad(ldos[i], grises[i] - corr_i, bins)
        intensidades_celular.append(intensidad_celular_bineada)
        
    intensidades_thorlabs = np.array(intensidades_thorlabs).T
    intensidades_celular = np.array(intensidades_celular).T
    
    popts = []
    # popts_grafico = []
    # i_celu_grafico = []   
    # i_thorlabs_grafico = []
    for i in range(len(bins) - 1):
        popt, pcov = np.polyfit(intensidades_celular[i], intensidades_thorlabs[i],
                                deg=deg, cov=True)
        popts.append(popt)
        
        # esto es re cualquiera, me lo pidio gaby
    #     if bins[i] == 420 or bins[i] == 520 or bins[i] == 620:
    #         popts_grafico.append(popt)
    #         i_celu_grafico.append(intensidades_celular[i])
    #         i_thorlabs_grafico.append(intensidades_thorlabs[i])
    
    # fig, ax = plt.subplots(figsize=(12, 6))
    # for i in range(len(i_celu_grafico)):
    #     ax.plot(i_celu_grafico[i], i_thorlabs_grafico[i], marker='o', ls='', color=['r', 'g', 'b'][i], label=f'Datos {[420,520,620][i]} nm')
    #     x_fit = np.linspace(np.min(i_celu_grafico[i]), np.max(i_celu_grafico[i]), 100)
    #     y_fit = np.poly1d(popts_grafico[i])(x_fit)
    #     ax.plot(x_fit, y_fit, color=['r', 'g', 'b'][i], label=f'Ajuste {[420,520,620][i]} nm')
    # ax.set_xlabel('Nivel de gris (u.a.)')
    # ax.set_ylabel('Intensidad Thorlabs (u.a.)')
    # ax.grid(ls=':')
    # ax.legend()
    # plt.show()
        

    df = pd.DataFrame({'longitudes_de_onda': bins[:-1]})
    for i, ps in enumerate(np.array(popts).T):
        df[f'coeficiente_{i}'] = ps
    df.to_csv(f'./CALIBRACION_INTENSIDAD.csv', index=False)

def cargar_calibracion_intensidad(n_pixel, gris, popt_ldo, path='./CALIBRACION_INTENSIDAD.csv'):
    ldo = difraccion(n_pixel, *popt_ldo)
    
    df_intensidad = pd.read_csv(path)
    bins = df_intensidad['longitudes_de_onda'].to_numpy()
    
    gris_bineado = binear_intensidad(ldo, gris, bins)
    
    intensidad_calibrada = []
    for k in range(len(bins) - 1):
        intensidad_calibrada.append(np.poly1d(df_intensidad.iloc[k, 1:].to_numpy())(gris_bineado[k]))
    
    intensidad_calibrada = promediar(intensidad_calibrada, 2, 2)
    
    return bins[:-1], np.array(intensidad_calibrada)

def promediar(datos, n_promediar=2, n_repeticiones=2):
    
    n_datos = len(datos)
    datos_promediados_1 = datos.copy()
    for _ in range(n_repeticiones):
        datos_promediados_2 = []
        for i in range(n_datos):
            inicio = max(0, i - n_promediar)
            fin = min(n_datos, i + n_promediar + 1)
            datos_promediados_2.append(np.mean(datos_promediados_1[inicio:fin]))
        datos_promediados_1 = np.array(datos_promediados_2)

    return np.array(datos_promediados_1)

def filtrar_ruido_fft(x, y, cutoff_freq=0.1):
    """
    Filtra ruido de alta frecuencia usando transformada de Fourier.
    
    Parameters:
    -----------
    x : array_like
        Array de coordenadas x (píxeles o longitud de onda)
    y : array_like  
        Array de intensidades con ruido
    cutoff_freq : float, optional
        Frecuencia de corte normalizada (0-1), default 0.1
        
    Returns:
    --------
    y_filtered : ndarray
        Array de intensidades filtradas
    """
    # Transformada de Fourier
    fft_y = np.fft.fft(y)
    frequencies = np.fft.fftfreq(len(y))
    
    # Crear filtro pasa-bajos
    filter_mask = np.abs(frequencies) <= cutoff_freq
    
    # Aplicar filtro
    fft_filtered = fft_y * filter_mask
    
    # Transformada inversa
    y_filtered = np.real(np.fft.ifft(fft_filtered))
    
    return y_filtered