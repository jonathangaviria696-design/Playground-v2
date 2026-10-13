#!/usr/bin/env python3
"""
Steam -> MongoDB (mismo esquema que Epic/GOG vía juegos_comun).

Este archivo solo OBTIENE datos de Steam. Guardar, evitar duplicados y decidir
qué juegos buscar de forma automática es trabajo de juegos_comun.py:

    python juegos_comun.py --cantidad 20      # búsqueda automática de juegos aleatorios

También se puede usar solo, buscando un juego por nombre:

    python steam.py "witcher 3"
    python steam.py "witcher 3" --no-guardar
"""

from __future__ import annotations

import argparse
import html
import random
import re
import sys
import time

import requests

PLATAFORMA = "Steam"
PAIS = "co"          # precios en COP
IDIOMA = "spanish"

URL_DETALLES = "https://store.steampowered.com/api/appdetails"
URL_BUSQUEDA = "https://store.steampowered.com/api/storesearch/"
URL_CATALOGO = "https://store.steampowered.com/search/results/"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/154.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json",
    "Accept-Language": "es-ES,es;q=0.9",
}

# Géneros de Steam que en realidad no son géneros (F2P, acceso anticipado, contenido...)
GENEROS_IGNORADOS = {"37", "70", "71", "72", "73", "74"}
GENEROS_TRADUCIDOS = {"RPG": "Rol"}

TAM_PAGINA = 50
_total_catalogo: int | None = None   # se consulta una sola vez por ejecución


# ============================================================
# UTILIDADES
# ============================================================

def normalizar_nombre(nombre):
    """
    Convierte nombres diferentes en una forma comparable:
    "The Witcher 3: Wild Hunt — Remastered" ~ "The Witcher 3: Wild Hunt - Remastered".
    """
    nombre = nombre.lower()
    nombre = nombre.replace("—", "-").replace("–", "-")
    nombre = re.sub(r"[^a-z0-9áéíóúüñ\s-]", " ", nombre)
    nombre = re.sub(r"\s+", " ", nombre)
    return nombre.strip()


def limpiar_nombre(nombre: str) -> str:
    """Quita ™ ® © que Steam agrega a muchos títulos."""
    nombre = re.sub(r"[™®©℠]", "", nombre or "")
    return re.sub(r"\s+", " ", nombre).strip()


def _texto_plano(fragmento: str | None) -> str | None:
    if not fragmento:
        return None
    s = re.sub(r"<br\s*/?>", " ", fragmento, flags=re.I)
    s = re.sub(r"<[^>]+>", " ", s)
    s = html.unescape(s)
    s = re.sub(r"\s+", " ", s).strip()
    return s or None


def extraer_precio_numerico(precio_raw):
    """
    Convierte precios de Steam a número.

    COL$ 59.900 -> 59900   |   $59.99 -> 59.99   |   29,99 -> 29.99
    """
    if precio_raw is None:
        return None
    if isinstance(precio_raw, (int, float)):
        return float(precio_raw)

    precio = str(precio_raw).strip()
    if precio.lower() in ("free", "gratis", "gratuito", "free to play"):
        return 0.0

    precio = re.sub(r"(COL\$|COP|USD|EUR|GBP|ARS|BRL|MXN|CAD|AUD)", "", precio, flags=re.IGNORECASE)
    for simbolo in ("$", "€", "£", " ", "\xa0"):
        precio = precio.replace(simbolo, "")

    if "," in precio and "." in precio:
        if precio.rfind(",") > precio.rfind("."):      # 59.900,99
            precio = precio.replace(".", "").replace(",", ".")
        else:                                          # 59,900.99
            precio = precio.replace(",", "")
    elif "," in precio:
        if len(precio.split(",")[-1]) == 2:            # 29,99
            precio = precio.replace(",", ".")
        else:                                          # 59,900
            precio = precio.replace(",", "")
    elif "." in precio:
        if len(precio.split(".")[-1]) == 3:            # 59.900
            precio = precio.replace(".", "")

    try:
        return float(precio)
    except ValueError:
        return None


def _get(url, params, reintentos=3, timeout=20):
    """GET con reintentos si Steam limita la tasa (429/403) o falla (5xx). Devuelve la respuesta o None."""
    for intento in range(reintentos):
        try:
            r = requests.get(url, params=params, headers=HEADERS, timeout=timeout)
        except requests.RequestException as e:
            print(f"⚠️ Error de red con Steam: {e}")
            time.sleep(3 * (intento + 1))
            continue
        if r.status_code in (429, 403) or r.status_code >= 500:
            espera = 10 * (intento + 1)
            print(f"⚠️ Steam respondió {r.status_code}; espero {espera}s...")
            time.sleep(espera)
            continue
        if r.status_code != 200:
            print(f"⚠️ Steam respondió {r.status_code}")
            return None
        return r
    return None


# ============================================================
# DETALLES DE UN JUEGO
# ============================================================

def _genero(data: dict) -> tuple[str | None, list[str]]:
    """(género principal en texto, lista de todos los géneros)."""
    todos, principal = [], None
    for g in data.get("genres") or []:
        desc = (g.get("description") or "").strip()
        if not desc:
            continue
        desc = GENEROS_TRADUCIDOS.get(desc, desc)
        todos.append(desc)
        if principal is None and str(g.get("id")) not in GENEROS_IGNORADOS:
            principal = desc
    return principal, todos


def obtener_steam(app_id):
    """
    Consulta appdetails y devuelve un dict, o None si no es un juego / falla.

    `disponible` es True solo si el juego ya salió y se puede comprar (o es gratis)
    en la tienda de Colombia; si no, `precio` queda en None.
    """
    r = _get(URL_DETALLES, {"appids": app_id, "l": IDIOMA, "cc": PAIS})
    if r is None:
        return None
    try:
        entrada = r.json().get(str(app_id), {})
    except ValueError:
        print("⚠️ Steam devolvió una respuesta que no es JSON")
        return None

    if not entrada.get("success"):
        return None
    data = entrada.get("data") or {}
    if data.get("type") != "game":
        return None

    es_gratis = bool(data.get("is_free"))
    precio, moneda = None, "COP"
    if es_gratis:
        precio = 0.0
    elif data.get("price_overview"):
        info = data["price_overview"]
        precio = extraer_precio_numerico(info.get("final_formatted"))
        if precio is None and info.get("final") is not None:
            precio = info["final"] / 100          # Steam entrega el precio x100
        moneda = info.get("currency", "COP")

    sale_pronto = bool((data.get("release_date") or {}).get("coming_soon"))
    disponible = (not sale_pronto) and (es_gratis or precio is not None)

    genero, generos = _genero(data)
    devs = data.get("developers") or []
    descripcion = _texto_plano(data.get("short_description"))
    if not descripcion:
        descripcion = (_texto_plano(data.get("about_the_game")) or "")[:400] or None

    return {
        "app_id": app_id,
        "nombre": limpiar_nombre(data.get("name", "")),
        "desarrolladora": devs[0] if devs else None,
        "descripcion": descripcion,
        "genero": genero,
        "generos": generos,
        "imagenUrl": data.get("header_image") or None,
        "precio": precio,
        "moneda": moneda,
        "esGratis": es_gratis,
        "disponible": disponible,
    }


def a_documentos(datos: dict) -> tuple[dict, dict | None]:
    """
    Convierte lo que devuelve obtener_steam() a lo que espera
    juegos_comun.upsert_juego(): (juego, precio).
    """
    juego = {k: datos.get(k) for k in ("nombre", "desarrolladora", "descripcion", "genero", "imagenUrl")}
    precio = None
    if datos.get("precio") is not None:
        precio = {
            "plataforma": PLATAFORMA,
            "precio": datos["precio"],
            "moneda": datos.get("moneda") or "COP",
            "esGratis": bool(datos.get("esGratis")),
        }
    return juego, precio


# ============================================================
# BÚSQUEDA POR NOMBRE
# ============================================================

def buscar_juego_steam(nombre):
    """Busca por nombre y devuelve los detalles del mejor resultado (o None)."""
    variantes = list(dict.fromkeys([
        nombre,
        nombre.replace("—", "-"),
        nombre.replace("—", ""),
        nombre.replace("–", "-"),
    ]))

    resultados = []
    for termino in variantes:
        r = _get(URL_BUSQUEDA, {"term": termino, "l": IDIOMA, "cc": PAIS})
        if r is None:
            continue
        try:
            items = r.json().get("items", [])
        except ValueError:
            continue
        for item in items:
            if item.get("id") and not any(x["id"] == item["id"] for x in resultados):
                resultados.append(item)

    if not resultados:
        return None

    buscado = normalizar_nombre(nombre)
    palabras_buscadas = set(buscado.split())

    def puntuacion(res):
        palabras = set(normalizar_nombre(res.get("name", "")).split())
        return len(palabras_buscadas & palabras) / len(palabras_buscadas) if palabras_buscadas else 0

    # Del criterio más estricto al más flexible
    criterios = [
        lambda n: n == buscado,                     # 1. coincidencia exacta
        lambda n: bool(n) and n in buscado,         # 2. el buscado contiene al resultado
        lambda n: bool(buscado) and buscado in n,   # 3. el resultado contiene al buscado
    ]
    for criterio in criterios:
        for res in resultados:
            if criterio(normalizar_nombre(res.get("name", ""))):
                juego = obtener_steam(res["id"])
                if juego:
                    return juego

    # 4. coincidencia por palabras (mínimo 60 %)
    mejor = max(resultados, key=puntuacion)
    if puntuacion(mejor) >= 0.60:
        return obtener_steam(mejor["id"])
    return None


# ============================================================
# JUEGOS ALEATORIOS (lo usa juegos_comun.py)
# ============================================================

def _params_catalogo(inicio: int, cantidad: int) -> dict:
    return {
        "query": "",
        "start": inicio,
        "count": cantidad,
        "category1": 998,          # solo juegos (sin DLC, bandas sonoras, software...)
        "infinite": 1,
        "cc": PAIS,
        "l": IDIOMA,
    }


def _total_juegos() -> int | None:
    """Cantidad de juegos en el catálogo de Steam (se consulta una vez)."""
    global _total_catalogo
    if _total_catalogo is None:
        r = _get(URL_CATALOGO, _params_catalogo(0, 1))
        try:
            _total_catalogo = int(r.json().get("total_count", 0)) if r else 0
        except (ValueError, TypeError):
            _total_catalogo = 0
    return _total_catalogo or None


def candidatos_aleatorios(cantidad: int = TAM_PAGINA) -> list[tuple[int, str]]:
    """
    Toma una página del catálogo de Steam en una posición al azar y devuelve
    [(app_id, nombre), ...] mezclados. No consulta precios ni detalles.
    Lista vacía si Steam no responde.
    """
    total = _total_juegos()
    if not total:
        return []

    inicio = random.randint(0, max(0, total - cantidad))
    r = _get(URL_CATALOGO, _params_catalogo(inicio, cantidad))
    if r is None:
        return []
    try:
        pagina = r.json().get("results_html") or ""
    except ValueError:
        return []

    candidatos = []
    for m in re.finditer(r'<a\s[^>]*data-ds-appid="([\d,]+)"[^>]*>(.*?)</a>', pagina, flags=re.S):
        titulo = re.search(r'<span class="title">(.*?)</span>', m.group(2), flags=re.S)
        if not titulo:
            continue
        app_id = int(m.group(1).split(",")[0])
        nombre = limpiar_nombre(html.unescape(titulo.group(1)))
        if nombre:
            candidatos.append((app_id, nombre))

    random.shuffle(candidatos)
    return candidatos


# ============================================================
# PROGRAMA PRINCIPAL (búsqueda por nombre)
# ============================================================

def mostrar_juego(juego):
    print("\n" + "=" * 65)
    print("🎮 JUEGO ENCONTRADO")
    print("=" * 65)
    print(f"Nombre:         {juego['nombre']}")
    print(f"App ID:         {juego['app_id']}")
    print(f"Precio:         {juego['precio'] if juego['precio'] is not None else 'No disponible'}")
    print(f"Moneda:         {juego['moneda']}")
    print(f"¿Es gratis?:    {'Sí' if juego['esGratis'] else 'No'}")
    print(f"Desarrolladora: {juego['desarrolladora']}")
    print(f"Géneros:        {', '.join(juego['generos'])}")
    print(f"Imagen:         {juego['imagenUrl']}")
    print("=" * 65)


def main():
    ap = argparse.ArgumentParser(description="Steam -> MongoDB (búsqueda por nombre)")
    ap.add_argument("busqueda", nargs="?", help="Nombre del juego")
    ap.add_argument("--no-guardar", action="store_true", help="Solo mostrar, no escribir en MongoDB")
    ap.add_argument("--umbral", type=float, default=0.92, help="Similitud mínima de nombres (0-1)")
    ap.add_argument("--sobrescribir", action="store_true",
                    help="Reemplaza descripción/género/etc. aunque ya existan")
    args = ap.parse_args()

    nombre = (args.busqueda or input("\nEscribe el nombre del juego: ")).strip()
    if not nombre:
        sys.exit("❌ Debes escribir un nombre.")

    print(f"\n🔎 Buscando '{nombre}' en Steam...")
    datos = buscar_juego_steam(nombre)
    if not datos:
        sys.exit(f"❌ No se encontró '{nombre}' en Steam.")
    mostrar_juego(datos)

    if args.no_guardar:
        return
    from juegos_comun import obtener_coleccion, upsert_juego   # import aquí: juegos_comun importa a steam

    juego, precio = a_documentos(datos)
    estado, motivo = upsert_juego(obtener_coleccion(), juego, precio, args.sobrescribir, args.umbral)
    print(f"[{estado}{f' ({motivo})' if motivo else ''}] {juego['nombre']} -> {precio}")


if __name__ == "__main__":
    main()