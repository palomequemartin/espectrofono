import numpy as np
from scipy.optimize import curve_fit
from scipy.signal import find_peaks
import sympy as sp
import pandas as pd
import matplotlib.pyplot as plt

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
    # Convert inputs to numpy arrays for consistent handling
    n_pixel = np.array(n_pixel)
    gris = np.array(gris)
    
    # Initialize list to store found peak positions
    picos = []
    
    # Step 1: Find threshold crossings to segment the spectrum
    # These crossings divide the spectrum into regions containing different spectral lines
    cruces = encontrar_cruces(gris, umbral)
    
    # Step 2: Find the first peak (before the first threshold crossing)
    # This region typically contains a single, well-defined peak
    # Uses scipy's find_peaks with basic parameters for automatic detection
    primer_pico, _ = find_peaks(gris[:cruces[0]], threshold=0.001, distance=len(gris[:cruces[0]]))
    picos.append(primer_pico[0])
    
    # Step 3: Find red spectral line peaks (regions 1-2 and 3-4)
    # These are typically sharp, well-defined peaks in the red part of the spectrum
    # Uses simple maximum finding since these peaks are usually prominent
    for i in [[0, 1], [2, 3]]:  # Process regions [cruces[0]:cruces[1]] and [cruces[2]:cruces[3]]
        # Define the boundaries of the current spectral region
        inicio = cruces[i[0]]  # Start index of the region
        final = cruces[i[1]] + 1  # End index of the region (inclusive)
        
        # Extract the pixel numbers and intensity values for this region
        x_cortado = n_pixel[inicio:final]
        y_cortado = gris[inicio:final]
        
        # Find the peak as the simple maximum in this region
        # Add the starting index to convert back to original pixel coordinates
        picos.append(np.argmax(y_cortado) + inicio)
    
    # Step 4: Find green and blue spectral line peaks (regions 5-6 and 7-8)
    # These peaks are often broader and may require Gaussian fitting for accurate positioning
    for i in [[4, 5], [6, 7]]:  # Process regions [cruces[4]:cruces[5]] and [cruces[6]:cruces[7]]
        # Define the boundaries of the current spectral region
        inicio = cruces[i[0]]  # Start index of the region
        final = cruces[i[1]] + 1  # End index of the region (inclusive)
        
        # Extract the pixel numbers and intensity values for this region
        x_cortado = n_pixel[inicio:final]
        y_cortado = gris[inicio:final]

        # Set up initial parameters for Gaussian fitting
        # These provide reasonable starting guesses for the optimization
        p0 = [np.max(y_cortado) - np.min(y_cortado),  # Amplitude: peak height above baseline
              x_cortado[np.argmax(y_cortado)],          # Mean: x-position of maximum (initial guess)
              (x_cortado[-1] - x_cortado[0]) / 4,       # Sigma: width estimate (1/4 of region width)
              np.min(y_cortado)]                        # Offset: baseline level

        # Perform Gaussian curve fitting to find precise peak position
        # This is more accurate than simple maximum finding for broader peaks
        popt, pcov = curve_fit(gaussiana, x_cortado, y_cortado, p0=p0, maxfev=1000)
        
        # Extract the fitted mean (peak center position) from the optimized parameters
        # popt[1] corresponds to the 'media' parameter of the Gaussian function
        picos.append(popt[1])  # Guardar la media (posición del pico)

    # Return the array of 5 peak positions for calibration
    # These correspond to known wavelengths and will be used to establish
    # the pixel-to-wavelength relationship
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
    n_cruces_objetivo = len(diferencias_objetivo) + 1  # +1 porque n diferencias = n-1 cruces
    
    umbral_min = 0.01
    umbral_max = 0.8
    n_umbrales = 5000
    umbrales = np.linspace(umbral_min, umbral_max, n_umbrales)
    
    mejor_umbral = None
    mejor_puntuacion = float('inf')
    candidatos = []
    
    for umbral in umbrales:
        cruces = encontrar_cruces(y_array, umbral)
        
        # Verificar que tenemos el número correcto de cruces
        if len(cruces) != n_cruces_objetivo:
            continue
            
        # Calcular diferencias entre cruces consecutivos
        diferencias_actuales = np.diff(cruces)
        
        # Calcular la puntuación basada en qué tan cerca están las diferencias del objetivo
        # Usamos error cuadrático medio normalizado
        error_relativo = np.abs(diferencias_actuales - diferencias_objetivo) / diferencias_objetivo
        puntuacion = np.mean(error_relativo)
        
        candidatos.append({
            'umbral': umbral,
            'diferencias': diferencias_actuales,
            'error_relativo': error_relativo,
            'puntuacion': puntuacion
        })
        
        # Si todas las diferencias están dentro de la tolerancia, consideramos este umbral
        if np.all(error_relativo <= tolerancia) and puntuacion < mejor_puntuacion:
            mejor_puntuacion = puntuacion
            mejor_umbral = umbral
    
    if mejor_umbral is None and candidatos:
        # Si no encontramos un umbral perfecto, devolvemos el mejor candidato
        mejor_candidato = min(candidatos, key=lambda x: x['puntuacion'])
        mejor_umbral = mejor_candidato['umbral']
        mejor_puntuacion = mejor_candidato['puntuacion']
    
    return mejor_umbral


def calibracion_ldo(n_pixel, gris, full_output=False):
    picos_thorlabs = np.array([453.6733242631141, 540.0395034709993, 615.1063232, 632.8643188, 649.3109741])[::-1]
    
    umbral = encontrar_umbral_optimo(gris)
    picos = encontrar_picos(n_pixel, gris, umbral)

    popt, pcov = curve_fit(difraccion, picos, picos_thorlabs, maxfev=1000000,
                           p0=[1.26777218, 6.78704522e-13, 2e3])
    
    ldo = difraccion(n_pixel, *popt)
    
    if full_output:
        return ldo, popt, pcov
    return ldo

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
    df = pd.read_csv(path,delimiter = delimiter)
    
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
        poly = Polynomial(popts[m])
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
    """Grafica los datos del espectrómetro Thorlabs entre las ldo dadas."""
    data = intensidad_thorlabs_binned(path,ldo_min,ldo_max,step)
    bins = np.arange(ldo_min, ldo_max, step)
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(bins,data)
    ax.set_xlabel('Longitud de onda (nm)')
    ax.set_ylabel('Intensidad (u.a)')
    ax.set_title(f'{path}')


