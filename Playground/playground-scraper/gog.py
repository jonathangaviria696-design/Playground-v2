#!/usr/bin/env python3
"""
GOG.com -> MongoDB (mismo esquema que Epic/Steam vía juegos_comun).

Uso:
    pip install requests pymongo python-dotenv
    export MONGO_URI="mongodb://localhost:27017"
    python gog.py "witcher" --limite 5
    python gog.py --migrar
    python gog.py "witcher" --debug

La búsqueda usa catalog.gog.com con query=like:<texto> (coincidencia aproximada);
los resultados se filtran por relevancia en el título antes de guardar.
"""

from __future__ import annotations

import argparse
import html
import re
import sys

import requests

from juegos_comun import clave_estricta, migrar_claves, obtener_coleccion, upsert_juego

PLATAFORMA = "GOG"
CATALOG_URL = "https://catalog.gog.com/v1/catalog"
DETAIL_URL = "https://api.gog.com/v2/games/{product_id}"

HEADERS = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                   "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"),
    "Accept": "application/json",
    "Accept-Language": "es-ES,es;q=0.9",
}

GENEROS_GOG = {
    "Action": "Acción",
    "Adventure": "Aventura",
    "Role-playing": "Rol",
    "Strategy": "Estrategia",
    "Simulation": "Simulación",
    "Racing": "Carreras",
    "Sports": "Deportes",
    "Shooter": "Disparos",
    "Indie": "Indie",
    "Casual": "Casual",
    "Platformer": "Plataformas",
    "Puzzle": "Puzles",
    "Horror": "Terror",
    "Fighting": "Lucha",
    "Fantasy": "Fantasía",
    "Sci-fi": "Ciencia ficción",
}


class ConsultaFallida(Exception):
    pass


def _log(debug, *a):
    if debug:
        print("[debug]", *a, file=sys.stderr)


def _texto_plano(html_fragment: str | None) -> str | None:
    if not html_fragment:
        return None
    s = re.sub(r"<br\s*/?>", "\n", html_fragment, flags=re.I)
    s = re.sub(r"<[^>]+>", " ", s)
    s = html.unescape(s)
    s = re.sub(r"\s+", " ", s).strip()
    return s or None


def _relevante(titulo: str, busqueda: str) -> bool:
    clave_t = clave_estricta(titulo or "")
    clave_b = clave_estricta(busqueda or "")
    if not clave_b or not clave_t:
        return False
    if clave_b in clave_t:
        return True
    tokens = [t for t in clave_b.split() if len(t) > 1]
    return bool(tokens) and all(t in clave_t for t in tokens)


def _genero(producto: dict) -> str | None:
    for g in producto.get("genres") or []:
        nombre = (g.get("name") or "").strip()
        if nombre:
            return GENEROS_GOG.get(nombre, nombre)
    for t in producto.get("tags") or []:
        nombre = (t.get("name") or "").strip()
        if nombre in GENEROS_GOG:
            return GENEROS_GOG[nombre]
    return None


def _imagen(producto: dict) -> str | None:
    for key in ("coverVertical", "coverHorizontal", "logo", "galaxyBackgroundImage"):
        url = producto.get(key)
        if url:
            return url.replace("{formatter}", "product_card_v2_mobile_slider_639")
    shots = producto.get("screenshots") or []
    if shots:
        return shots[0].replace("{formatter}", "product_card_v2_mobile_slider_639")
    return None


def buscar_en_gog(
    texto: str,
    pais: str = "CO",
    locale: str = "es-ES",
    moneda: str = "COP",
    limite: int = 5,
    debug: bool = False,
) -> list[dict]:
    """Catálogo GOG (like:) y filtro por título; pide más filas si hace falta."""
    if not texto.strip():
        raise ConsultaFallida("texto de búsqueda vacío")

    pedir = max(limite * 4, 20)
    params = {
        "query": f"like:{texto.strip()}",
        "limit": min(pedir, 48),
        "productType": "in:game,pack",
        "countryCode": pais,
        "locale": locale,
        "currencyCode": moneda,
        "order": "desc:popularity",
    }
    _log(debug, "GET", CATALOG_URL, params)
    try:
        r = requests.get(CATALOG_URL, params=params, headers=HEADERS, timeout=25)
    except requests.RequestException as e:
        raise ConsultaFallida(str(e)) from e

    _log(debug, "HTTP", r.status_code, r.text[:280].replace("\n", " "))
    if r.status_code != 200:
        raise ConsultaFallida(f"HTTP {r.status_code}")
    try:
        data = r.json()
    except ValueError as e:
        raise ConsultaFallida("respuesta no JSON") from e

    candidatos = data.get("products") or []
    filtrados = [p for p in candidatos if _relevante(p.get("title", ""), texto)]
    if not filtrados and candidatos:
        filtrados = candidatos[:limite]
        _log(debug, "sin coincidencias estrictas; usando primeros del catálogo")
    return filtrados[:limite]


def candidatos_aleatorios(pais: str = "CO", locale: str = "es-ES", moneda: str = "COP",
                          cantidad: int = 48, debug: bool = False) -> list[dict]:
    """
    Una página del catálogo de GOG elegida al azar. Devuelve los productos tal
    como los entrega el catálogo (sirven directo para a_documentos). [] si falla.
    """
    import math
    import random

    base = {
        "limit": cantidad,
        "productType": "in:game,pack",
        "countryCode": pais,
        "locale": locale,
        "currencyCode": moneda,
        "order": "desc:trending",
    }

    def pedir(pagina: int):
        try:
            r = requests.get(CATALOG_URL, params={**base, "page": pagina}, headers=HEADERS, timeout=25)
            if r.status_code != 200:
                _log(debug, "HTTP", r.status_code)
                return None
            return r.json()
        except (requests.RequestException, ValueError) as e:
            _log(debug, "catálogo falló", e)
            return None

    primera = pedir(1)
    if not primera:
        return []
    paginas = primera.get("pages") or math.ceil((primera.get("productCount") or 0) / cantidad) or 1
    elegida = random.randint(1, int(paginas))
    data = primera if elegida == 1 else pedir(elegida)
    productos = [pr for pr in ((data or {}).get("products") or []) if pr.get("id") and pr.get("title")]
    random.shuffle(productos)
    return productos


def obtener_descripcion(product_id: str, locale: str, debug: bool) -> str | None:
    url = DETAIL_URL.format(product_id=product_id)
    try:
        r = requests.get(url, params={"locale": locale}, headers=HEADERS, timeout=20)
    except requests.RequestException as e:
        _log(debug, "detalle falló", e)
        return None
    if r.status_code != 200:
        _log(debug, "detalle HTTP", r.status_code)
        return None
    try:
        body = r.json()
    except ValueError:
        return None
    desc = _texto_plano(body.get("description") or body.get("overview"))
    _log(debug, "descripción", product_id, "ok" if desc else "vacía")
    return desc


def a_documentos(producto: dict, descripcion: str | None) -> tuple[dict, dict | None]:
    devs = producto.get("developers") or []
    juego = {
        "nombre": producto.get("title"),
        "desarrolladora": devs[0] if devs else None,
        "descripcion": descripcion,
        "genero": _genero(producto),
        "imagenUrl": _imagen(producto),
    }

    price = producto.get("price") or {}
    final = price.get("finalMoney") or {}
    if "amount" not in final:
        return juego, None

    try:
        valor = float(final["amount"])
    except (TypeError, ValueError):
        return juego, None

    moneda = (final.get("currency") or "").upper() or None
    estado = producto.get("productState") or ""
    es_gratis = valor == 0 or estado == "free"

    precio = {
        "plataforma": PLATAFORMA,
        "precio": valor,
        "moneda": moneda,
        "esGratis": es_gratis,
    }
    return juego, precio


def main():
    ap = argparse.ArgumentParser(description="GOG -> MongoDB")
    ap.add_argument("busqueda", nargs="?", help="Texto a buscar, p. ej. 'witcher'")
    ap.add_argument("--limite", type=int, default=5)
    ap.add_argument("--pais", default="CO", help="Código de país (precios)")
    ap.add_argument("--locale", default="es-ES")
    ap.add_argument("--moneda", default="COP", help="Código de moneda para el catálogo")
    ap.add_argument("--umbral", type=float, default=0.92, help="Similitud mínima de nombres (0-1)")
    ap.add_argument("--sobrescribir", action="store_true",
                    help="Reemplaza descripción/género/etc. aunque ya existan")
    ap.add_argument("--sin-descripcion", action="store_true",
                    help="No consulta api.gog.com/v2/games (más rápido, sin descripción)")
    ap.add_argument("--migrar", action="store_true",
                    help="Agrega claves de búsqueda a documentos antiguos y termina")
    ap.add_argument("--debug", action="store_true")
    args = ap.parse_args()

    col = obtener_coleccion()

    if args.migrar:
        print(f"Documentos migrados: {migrar_claves(col)}")
        return
    if not args.busqueda:
        ap.error("indica un texto de búsqueda (o usa --migrar)")

    try:
        resultados = buscar_en_gog(
            args.busqueda, args.pais, args.locale, args.moneda, args.limite, args.debug,
        )
    except ConsultaFallida as e:
        sys.exit(f"No se pudo consultar GOG -> {e}")

    if not resultados:
        sys.exit("Sin resultados.")

    for producto in resultados:
        titulo = producto.get("title")
        if not titulo:
            continue
        descripcion = None
        if not args.sin_descripcion and producto.get("id"):
            descripcion = obtener_descripcion(str(producto["id"]), args.locale, args.debug)
        juego, precio = a_documentos(producto, descripcion)
        estado, motivo = upsert_juego(col, juego, precio, args.sobrescribir, args.umbral)
        extra = f" ({motivo})" if motivo else ""
        print(f"[{estado}{extra}] {juego['nombre']} -> {precio}")


if __name__ == "__main__":
    main()