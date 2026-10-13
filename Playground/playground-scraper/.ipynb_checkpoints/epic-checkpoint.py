import re

from epicstore_api import EpicGamesStoreAPI, OfferData


# ============================================================
# CONFIGURACIÓN
# ============================================================

LOCALE = "es-ES"
COUNTRY = "CO"


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
    nombre = re.sub(
        r"[^a-z0-9áéíóúüñ\s-]",
        " ",
        nombre
    )

    # Normalizar espacios
    nombre = re.sub(
        r"\s+",
        " ",
        nombre
    )

    return nombre.strip()


# ============================================================
# EXTRAER PRECIO DE EPIC
# ============================================================

def extraer_precio_epic(price_data):
    """
    Extrae el precio actual de Epic Games.

    Epic devuelve los precios en unidades menores de la moneda.

    Ejemplo:

    discountPrice = 7450000
    decimals = 2

    Resultado:

    7450000 / 100 = 74500.0
    """

    if not price_data:
        return None, "COP", False

    total_price = price_data.get(
        "totalPrice",
        {}
    )

    if not total_price:
        return None, "COP", False

    # --------------------------------------------------------
    # Moneda
    # --------------------------------------------------------

    moneda = total_price.get(
        "currencyCode",
        "COP"
    )

    # --------------------------------------------------------
    # Decimales
    # --------------------------------------------------------

    currency_info = total_price.get(
        "currencyInfo",
        {}
    )

    decimales = currency_info.get(
        "decimals",
        2
    )

    # --------------------------------------------------------
    # Precio actual
    # --------------------------------------------------------

    precio = total_price.get(
        "discountPrice"
    )

    if precio is None:

        precio = total_price.get(
            "originalPrice"
        )

    if precio is None:
        return None, moneda, False

    # Epic devuelve el precio en unidades menores
    divisor = 10 ** decimales

    precio = float(precio) / divisor

    # --------------------------------------------------------
    # Determinar si es gratis
    # --------------------------------------------------------

    es_gratis = (
        precio == 0
    )

    return (
        precio,
        moneda,
        es_gratis
    )


# ============================================================
# CREAR API DE EPIC
# ============================================================

def crear_api():

    return EpicGamesStoreAPI(
        locale=LOCALE,
        country=COUNTRY
    )


# ============================================================
# BUSCAR SLUG EN EPIC
# ============================================================

def buscar_slug_epic(
    api,
    nombre
):
    """
    Busca un juego dentro del catálogo de Epic.

    Epic no nos proporciona aquí una búsqueda de texto
    equivalente a la de Steam, así que utilizamos el
    product mapping y comparamos los slugs.
    """

    try:

        mapping = api.get_product_mapping()

    except Exception as e:

        print(
            f"❌ Error obteniendo catálogo de Epic: {e}"
        )

        return None

    if not mapping:

        return None

    nombre_buscado = normalizar_nombre(
        nombre
    )

    # ========================================================
    # CREAR FORMA COMPARABLE DEL NOMBRE
    # ========================================================

    nombre_slug = nombre_buscado.replace(
        "-",
        " "
    )

    palabras_buscadas = set(
        nombre_slug.split()
    )

    # Palabras poco útiles para comparar
    palabras_ignoradas = {
        "the",
        "a",
        "an",
        "of",
        "and",
        "edition",
        "game",
        "deluxe",
        "complete"
    }

    palabras_buscadas = (
        palabras_buscadas
        - palabras_ignoradas
    )

    # ========================================================
    # GUARDAR CANDIDATOS
    # ========================================================

    candidatos = []

    for namespace, slug in mapping.items():

        if not slug:
            continue

        slug_normalizado = normalizar_nombre(
            str(slug)
        )

        palabras_slug = set(
            slug_normalizado
            .replace("-", " ")
            .split()
        )

        palabras_slug = (
            palabras_slug
            - palabras_ignoradas
        )

        if not palabras_buscadas:
            continue

        palabras_comunes = (
            palabras_buscadas
            & palabras_slug
        )

        puntuacion = (
            len(palabras_comunes)
            / len(palabras_buscadas)
        )

        if puntuacion >= 0.60:

            candidatos.append({
                "namespace": namespace,
                "slug": slug,
                "puntuacion": puntuacion
            })

    # ========================================================
    # NO HAY CANDIDATOS
    # ========================================================

    if not candidatos:

        return None

    # ========================================================
    # ORDENAR POR PUNTUACIÓN
    # ========================================================

    candidatos.sort(
        key=lambda x: x["puntuacion"],
        reverse=True
    )

    mejor = candidatos[0]

    return (
        mejor["namespace"],
        mejor["slug"]
    )


# ============================================================
# OBTENER PRODUCTO DE EPIC
# ============================================================

def obtener_producto_epic(
    api,
    slug
):

    try:

        producto = api.get_product(
            slug
        )

        if not producto:
            return None

        return producto

    except Exception as e:

        print(
            f"❌ Error obteniendo producto Epic: {e}"
        )

        return None


# ============================================================
# BUSCAR OFERTA PRINCIPAL
# ============================================================

def obtener_oferta_principal(
    producto
):
    """
    Busca la oferta correspondiente al juego principal.

    Epic puede devolver otras ofertas relacionadas,
    por ejemplo:

    - REDkit
    - DLC
    - Extras
    - Ediciones adicionales

    No queremos confundirlas con el juego principal.
    """

    paginas = producto.get(
        "pages",
        []
    )

    candidatos = []

    for pagina in paginas:

        oferta = pagina.get(
            "offer"
        )

        if not oferta:
            continue

        offer_id = oferta.get(
            "id"
        )

        namespace = pagina.get(
            "namespace"
        )

        if not offer_id or not namespace:
            continue

        # ====================================================
        # INFORMACIÓN DE LA PÁGINA
        # ====================================================

        titulo = ""

        about = pagina.get(
            "about",
            {}
        )

        if not isinstance(
            about,
            dict
        ):
            about = {}

        if about:

            titulo = about.get(
                "title",
                ""
            )

        if not titulo:

            titulo = pagina.get(
                "title",
                ""
            )

        # ====================================================
        # TIPO DE PÁGINA
        # ====================================================

        page_type = pagina.get(
            "type",
            ""
        )

        template = pagina.get(
            "_templateName",
            ""
        )

        # ====================================================
        # PUNTUACIÓN
        # ====================================================

        puntuacion = 0

        # Las páginas principales tienen prioridad
        if page_type == "productHome":
            puntuacion += 10

        if template == "productDetail":
            puntuacion += 5

        # Si tiene información "about", probablemente
        # corresponde al producto principal
        if about:
            puntuacion += 3

        # Evitar páginas claramente relacionadas con mods
        texto_pagina = (
            f"{titulo} "
            f"{page_type} "
            f"{template}"
        ).lower()

        if "redkit" in texto_pagina:
            puntuacion -= 10

        if "mod" in texto_pagina:
            puntuacion -= 5

        candidatos.append({
            "offer": OfferData(
                namespace,
                offer_id
            ),
            "offer_id": offer_id,
            "namespace": namespace,
            "titulo": titulo,
            "puntuacion": puntuacion
        })

    if not candidatos:

        return None

    # ========================================================
    # ORDENAR CANDIDATOS
    # ========================================================

    candidatos.sort(
        key=lambda x: x["puntuacion"],
        reverse=True
    )

    return candidatos[0]


# ============================================================
# OBTENER DATOS DE LA OFERTA
# ============================================================

def obtener_datos_oferta(
    api,
    oferta
):

    try:

        resultados = api.get_offers_data(
            oferta["offer"]
        )

        if not resultados:

            return None

        for resultado in resultados:

            catalog = (
                resultado
                .get("data", {})
                .get("Catalog", {})
                .get("catalogOffer", {})
            )

            if not catalog:
                continue

            return catalog

    except Exception as e:

        print(
            f"❌ Error obteniendo precio de Epic: {e}"
        )

    return None


# ============================================================
# EXTRAER DESARROLLADORA
# ============================================================

def extraer_desarrolladora(catalog):

    custom_attributes = catalog.get(
        "customAttributes",
        {}
    )

    # Epic puede devolver customAttributes como:
    #
    # {}
    #
    # o:
    #
    # []
    #
    # o una estructura diferente dependiendo del juego.

    if isinstance(custom_attributes, dict):

        posibles_campos = [
            "developerName",
            "DeveloperName",
            "developer",
            "Developer"
        ]

        for campo in posibles_campos:

            valor = custom_attributes.get(
                campo
            )

            if valor:

                if isinstance(valor, dict):

                    return (
                        valor.get("value")
                        or valor.get("string")
                        or ""
                    )

                return str(valor)

    # --------------------------------------------------------
    # Si Epic devuelve una lista
    # --------------------------------------------------------

    elif isinstance(custom_attributes, list):

        for atributo in custom_attributes:

            if not isinstance(
                atributo,
                dict
            ):
                continue

            nombre = str(
                atributo.get("key", "")
            ).lower()

            if nombre in {
                "developername",
                "developer",
                "developer_name"
            }:

                valor = (
                    atributo.get("value")
                    or atributo.get("string")
                    or ""
                )

                if isinstance(
                    valor,
                    dict
                ):

                    return (
                        valor.get("value")
                        or valor.get("string")
                        or ""
                    )

                return str(valor)

    return ""


# ============================================================
# EXTRAER IMAGEN
# ============================================================

def extraer_imagen(
    catalog,
    producto
):

    # ========================================================
    # PRIMERA OPCIÓN: KEY IMAGES DE LA OFERTA
    # ========================================================

    key_images = catalog.get(
        "keyImages",
        []
    )

    if isinstance(
        key_images,
        list
    ):

        for imagen in key_images:

            if not isinstance(
                imagen,
                dict
            ):
                continue

            url = imagen.get(
                "url"
            )

            if url:

                return url

    # ========================================================
    # SEGUNDA OPCIÓN: IMÁGENES DEL PRODUCTO
    # ========================================================

    paginas = producto.get(
        "pages",
        []
    )

    if not isinstance(
        paginas,
        list
    ):

        return ""

    for pagina in paginas:

        if not isinstance(
            pagina,
            dict
        ):
            continue

        imagenes = pagina.get(
            "images",
            []
        )

        if not isinstance(
            imagenes,
            list
        ):
            continue

        for imagen in imagenes:

            if not isinstance(
                imagen,
                dict
            ):
                continue

            url = imagen.get(
                "url"
            )

            if url:

                return url

    return ""


# ============================================================
# OBTENER DETALLES DE EPIC
# ============================================================

def obtener_epic(
    api,
    namespace,
    slug,
    nombre_buscado
):

    # ========================================================
    # OBTENER PRODUCTO
    # ========================================================

    producto = obtener_producto_epic(
        api,
        slug
    )

    if not producto:

        return None

    # ========================================================
    # OBTENER OFERTA PRINCIPAL
    # ========================================================

    oferta = obtener_oferta_principal(
        producto
    )

    if not oferta:

        print(
            "❌ No se encontró una oferta "
            "principal para el juego."
        )

        return None

    # ========================================================
    # OBTENER DATOS DE PRECIO
    # ========================================================

    catalog = obtener_datos_oferta(
        api,
        oferta
    )

    if not catalog:

        return None

    # ========================================================
    # PRECIO
    # ========================================================

    precio, moneda, es_gratis = (
        extraer_precio_epic(
            catalog.get("price")
        )
    )

    if precio is None:

        print(
            "⚠️ Epic no devolvió un precio válido."
        )

        return None

    # ========================================================
    # NOMBRE
    # ========================================================

    nombre = (
        catalog.get("title")
        or oferta.get("titulo")
        or nombre_buscado
    )

    # ========================================================
    # DESARROLLADORA
    # ========================================================

    desarrolladora = (
        extraer_desarrolladora(
            catalog
        )
    )

    # ========================================================
    # IMAGEN
    # ========================================================

    imagen = extraer_imagen(
        catalog,
        producto
    )

    # ========================================================
    # RESULTADO
    # ========================================================

    return {
        "plataforma": "Epic Games",
        "id": oferta["offer_id"],
        "namespace": namespace,
        "slug": slug,
        "nombre": nombre,
        "nombre_normalizado": normalizar_nombre(
            nombre
        ),
        "precio": precio,
        "moneda": moneda,
        "esGratis": es_gratis,
        "desarrolladora": desarrolladora,
        "genero": [],
        "imagen": imagen
    }


# ============================================================
# BUSCAR JUEGO EN EPIC
# ============================================================

def buscar_juego_epic(
    nombre
):

    try:

        print(
            f"\n🔎 Buscando '{nombre}' en Epic Games..."
        )

        # ====================================================
        # CREAR API
        # ====================================================

        api = crear_api()

        # ====================================================
        # BUSCAR SLUG
        # ====================================================

        resultado_busqueda = (
            buscar_slug_epic(
                api,
                nombre
            )
        )

        if not resultado_busqueda:

            print(
                f"❌ No se encontró "
                f"'{nombre}' en Epic Games."
            )

            return None

        namespace, slug = (
            resultado_busqueda
        )

        print(
            f"✅ Slug encontrado: {slug}"
        )

        print(
            f"✅ Namespace: {namespace}"
        )

        # ====================================================
        # OBTENER INFORMACIÓN COMPLETA
        # ====================================================

        juego = obtener_epic(
            api,
            namespace,
            slug,
            nombre
        )

        return juego

    except Exception as e:

        print(
            f"❌ Error procesando Epic Games: {e}"
        )

        return None


# ============================================================
# MOSTRAR JUEGO
# ============================================================

def mostrar_juego(
    juego
):

    print(
        "\n" + "=" * 65
    )

    print(
        "🎮 JUEGO ENCONTRADO"
    )

    print(
        "=" * 65
    )

    print(
        f"Plataforma:     "
        f"{juego['plataforma']}"
    )

    print(
        f"Nombre:         "
        f"{juego['nombre']}"
    )

    print(
        f"ID oferta:      "
        f"{juego['id']}"
    )

    print(
        f"Namespace:      "
        f"{juego['namespace']}"
    )

    print(
        f"Slug:           "
        f"{juego['slug']}"
    )

    print(
        f"Precio:         "
        f"{juego['precio']}"
    )

    print(
        f"Moneda:         "
        f"{juego['moneda']}"
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
        f"Imagen:         "
        f"{juego['imagen']}"
    )

    print(
        "=" * 65
    )


# ============================================================
# PROGRAMA PRINCIPAL
# ============================================================

if __name__ == "__main__":

    print(
        "=" * 65
    )

    print(
        "🎮 EPIC GAMES SCRAPER"
    )

    print(
        "=" * 65
    )

    nombre = input(
        "\nEscribe el nombre del juego: "
    ).strip()

    if not nombre:

        print(
            "❌ Debes escribir un nombre."
        )

    else:

        juego = buscar_juego_epic(
            nombre
        )

        if juego:

            mostrar_juego(
                juego
            )

        else:

            print(
                f"\n❌ No se pudo obtener "
                f"'{nombre}' desde Epic Games."
            )