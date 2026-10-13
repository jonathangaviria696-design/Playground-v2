import re
import requests


# ============================================================
# CONFIGURACIÓN
# ============================================================

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) "
        "AppleWebKit/537.36 "
        "(KHTML, like Gecko) "
        "Chrome/154.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json",
}


# ============================================================
# NORMALIZAR NOMBRE
# ============================================================

def normalizar_nombre(nombre):
    """
    Convierte nombres diferentes en una forma comparable.

    Ejemplo:

    The Witcher 3: Wild Hunt — Remastered
    The Witcher 3: Wild Hunt - Remastered

    terminan siendo prácticamente iguales.
    """

    nombre = nombre.lower()

    # Convertir diferentes tipos de guiones a uno normal
    nombre = nombre.replace("—", "-")
    nombre = nombre.replace("–", "-")

    # Quitar puntuación
    nombre = re.sub(r"[^a-z0-9áéíóúüñ\s-]", " ", nombre)

    # Normalizar espacios
    nombre = re.sub(r"\s+", " ", nombre)

    return nombre.strip()


# ============================================================
# EXTRAER PRECIO
# ============================================================

def extraer_precio_numerico(precio_raw):
    """
    Convierte precios de Steam a número.

    Ejemplos:

    $59.900       -> 59900
    COL$59.900    -> 59900
    $59.99        -> 59.99
    29,99         -> 29.99
    """

    if precio_raw is None:
        return None

    if isinstance(precio_raw, (int, float)):
        return float(precio_raw)

    precio = str(precio_raw).strip()

    # Juegos gratuitos
    gratuitos = [
        "free",
        "gratis",
        "gratuito",
        "free to play"
    ]

    if precio.lower() in gratuitos:
        return 0.0

    # Quitar códigos y símbolos de moneda
    precio = re.sub(
        r"(COL\$|COP|USD|EUR|GBP|ARS|BRL|MXN|CAD|AUD)",
        "",
        precio,
        flags=re.IGNORECASE
    )

    precio = precio.replace("$", "")
    precio = precio.replace("€", "")
    precio = precio.replace("£", "")
    precio = precio.replace(" ", "")

    # --------------------------------------------------------
    # Determinar formato decimal
    # --------------------------------------------------------

    if "," in precio and "." in precio:

        ultima_coma = precio.rfind(",")
        ultimo_punto = precio.rfind(".")

        if ultima_coma > ultimo_punto:
            # Ejemplo: 59.900,99
            precio = precio.replace(".", "")
            precio = precio.replace(",", ".")
        else:
            # Ejemplo: 59,900.99
            precio = precio.replace(",", "")

    elif "," in precio:

        partes = precio.split(",")

        if len(partes[-1]) == 2:
            # 29,99
            precio = precio.replace(",", ".")
        else:
            # 59,900
            precio = precio.replace(",", "")

    elif "." in precio:

        partes = precio.split(".")

        if len(partes[-1]) == 3:
            # 59.900
            precio = precio.replace(".", "")

    try:
        return float(precio)

    except ValueError:
        return None


# ============================================================
# OBTENER DETALLES DE STEAM
# ============================================================

def obtener_steam(app_id):

    url = "https://store.steampowered.com/api/appdetails"

    params = {
        "appids": app_id,
        "l": "spanish",
        "cc": "co"
    }

    try:

        respuesta = requests.get(
            url,
            params=params,
            headers=HEADERS,
            timeout=15
        )

        respuesta.raise_for_status()

        datos = respuesta.json()

        juego = datos.get(str(app_id), {})

        if not juego.get("success"):
            return None

        data = juego.get("data", {})

        # ----------------------------------------------------
        # Verificar que realmente sea un juego
        # ----------------------------------------------------

        if data.get("type") != "game":
            return None

        # ----------------------------------------------------
        # Precio
        # ----------------------------------------------------

        precio = 0.0
        moneda = "COP"
        es_gratis = False

        if data.get("is_free"):
            precio = 0.0
            es_gratis = True

        elif data.get("price_overview"):

            precio_info = data["price_overview"]

            precio_raw = precio_info.get("final_formatted")

            precio = extraer_precio_numerico(precio_raw)

            if precio is None:
                precio = 0.0

            moneda = precio_info.get(
                "currency",
                "COP"
            )

        # ----------------------------------------------------
        # Géneros
        # ----------------------------------------------------

        generos = []

        for genero in data.get("genres", []):
            nombre_genero = genero.get("description")

            if nombre_genero:
                generos.append(nombre_genero)

        # ----------------------------------------------------
        # Desarrolladores
        # ----------------------------------------------------

        desarrolladores = data.get(
            "developers",
            []
        )

        # ----------------------------------------------------
        # Imagen
        # ----------------------------------------------------

        imagen = data.get(
            "header_image",
            ""
        )

        # ----------------------------------------------------
        # Resultado
        # ----------------------------------------------------

        return {
    "app_id": app_id,
    "nombre": data.get("name", ""),
    "nombre_normalizado": normalizar_nombre(
        data.get("name", "")
    ),
    "precio": precio,
    "moneda": moneda,
    "esGratis": es_gratis,
    "desarrolladora": (
        desarrolladores[0]
        if desarrolladores
        else ""
    ),
    "genero": generos,
    "imagen": imagen
}

    except requests.RequestException as e:

        print(f"❌ Error conectando con Steam: {e}")

        return None

    except Exception as e:

        print(f"❌ Error procesando Steam: {e}")

        return None


# ============================================================
# BUSCAR JUEGO EN STEAM
# ============================================================

def buscar_juego_steam(nombre):

    url = "https://store.steampowered.com/api/storesearch/"

    variantes = [
        nombre,
        nombre.replace("—", "-"),
        nombre.replace("—", ""),
        nombre.replace("–", "-"),
    ]

    # Eliminar variantes repetidas
    variantes = list(dict.fromkeys(variantes))

    resultados_totales = []

    # ========================================================
    # HACER LAS BÚSQUEDAS
    # ========================================================

    for termino in variantes:

        params = {
            "term": termino,
            "l": "spanish",
            "cc": "co"
        }

        try:

            respuesta = requests.get(
                url,
                params=params,
                headers=HEADERS,
                timeout=15
            )

            respuesta.raise_for_status()

            datos = respuesta.json()

            resultados = datos.get(
                "items",
                []
            )

            for resultado in resultados:

                app_id = resultado.get("id")

                if not app_id:
                    continue

                # Evitar duplicados
                if any(
                    r.get("id") == app_id
                    for r in resultados_totales
                ):
                    continue

                resultados_totales.append(resultado)

        except requests.RequestException as e:

            print(
                f"⚠️ Error buscando "
                f"'{termino}': {e}"
            )

    # ========================================================
    # NO HAY RESULTADOS
    # ========================================================

    if not resultados_totales:

        return None

    nombre_buscado = normalizar_nombre(nombre)

    # ========================================================
    # 1. COINCIDENCIA EXACTA
    # ========================================================

    for resultado in resultados_totales:

        nombre_resultado = resultado.get(
            "name",
            ""
        )

        if (
            normalizar_nombre(nombre_resultado)
            == nombre_buscado
        ):

            juego = obtener_steam(
                resultado["id"]
            )

            if juego:
                return juego

    # ========================================================
    # 2. NOMBRE BUSCADO CONTIENE EL RESULTADO
    # ========================================================

    for resultado in resultados_totales:

        nombre_resultado = resultado.get(
            "name",
            ""
        )

        normalizado = normalizar_nombre(
            nombre_resultado
        )

        if (
            normalizado
            and normalizado in nombre_buscado
        ):

            juego = obtener_steam(
                resultado["id"]
            )

            if juego:
                return juego

    # ========================================================
    # 3. RESULTADO CONTIENE EL NOMBRE BUSCADO
    # ========================================================

    for resultado in resultados_totales:

        nombre_resultado = resultado.get(
            "name",
            ""
        )

        normalizado = normalizar_nombre(
            nombre_resultado
        )

        if (
            nombre_buscado
            and nombre_buscado in normalizado
        ):

            juego = obtener_steam(
                resultado["id"]
            )

            if juego:
                return juego

    # ========================================================
    # 4. COINCIDENCIA POR PALABRAS
    # ========================================================

    palabras_buscadas = set(
        nombre_buscado.split()
    )

    mejor_resultado = None
    mejor_puntuacion = 0

    for resultado in resultados_totales:

        nombre_resultado = normalizar_nombre(
            resultado.get("name", "")
        )

        palabras_resultado = set(
            nombre_resultado.split()
        )

        palabras_comunes = (
            palabras_buscadas
            & palabras_resultado
        )

        if not palabras_buscadas:
            continue

        puntuacion = (
            len(palabras_comunes)
            / len(palabras_buscadas)
        )

        if puntuacion > mejor_puntuacion:

            mejor_puntuacion = puntuacion
            mejor_resultado = resultado

    # Solo aceptar una coincidencia razonablemente buena
    if (
        mejor_resultado
        and mejor_puntuacion >= 0.60
    ):

        juego = obtener_steam(
            mejor_resultado["id"]
        )

        if juego:
            return juego

    return None


# ============================================================
# MOSTRAR JUEGO
# ============================================================

def mostrar_juego(juego):

    print("\n" + "=" * 65)

    print("🎮 JUEGO ENCONTRADO")

    print("=" * 65)

    print(
        f"Nombre:         {juego['nombre']}"
    )

    print(
        f"App ID:         {juego['app_id']}"
    )

    print(
        f"Precio:         {juego['precio']}"
    )

    print(
        f"Moneda:         {juego['moneda']}"
    )

    print(
        f"¿Es gratis?:    "
        f"{'Sí' if juego['esGratis'] else 'No'}"
    )

    print(
        f"Desarrolladora: "
        f"{juego['desarrolladora']}"
    )

    print(
        f"Géneros:        "
        f"{', '.join(juego['genero'])}"
    )

    print(
        f"Imagen:         {juego['imagen']}"
    )

    print("=" * 65)


# ============================================================
# PROGRAMA PRINCIPAL
# ============================================================

if __name__ == "__main__":

    print("=" * 65)
    print("🎮 STEAM SCRAPER")
    print("=" * 65)

    nombre = input(
        "\nEscribe el nombre del juego: "
    ).strip()

    if not nombre:

        print("❌ Debes escribir un nombre.")

    else:

        print(
            f"\n🔎 Buscando '{nombre}' en Steam..."
        )

        juego = buscar_juego_steam(nombre)

        if juego:

            mostrar_juego(juego)

        else:

            print(
                f"\n❌ No se encontró "
                f"'{nombre}' en Steam."
            )