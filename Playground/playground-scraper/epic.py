#!/usr/bin/env python3
"""
Epic Games Store -> MongoDB (esquema: nombre, desarrolladora, descripcion,
fechaActualizacion, genero, imagenUrl, precios[{plataforma, precio, moneda, esGratis}])

Uso:
    pip install requests pymongo
    export MONGO_URI="mongodb://localhost:27017"
    export EPIC_SEARCH_HASH="<sha256 actual de searchStoreQuery>"   # ver abajo
    python epic_a_mongo.py "witcher" --limite 5
    python epic_a_mongo.py --migrar        # una sola vez: agrega claves a docs antiguos
    python epic_a_mongo.py "witcher" --debug

Cómo conseguir el hash (si la consulta falla con PersistedQueryNotFound):
  1. Abre https://store.epicgames.com/es-ES/browse en el navegador.
  2. F12 -> pestaña Network -> filtra por "graphql" -> escribe algo en el buscador.
  3. Abre la petición con operationName=searchStoreQuery y copia el valor de
     extensions.persistedQuery.sha256Hash. Ese es EPIC_SEARCH_HASH.
"""

import argparse
import json
import os
import sys

import requests

from juegos_comun import migrar_claves, obtener_coleccion, upsert_juego

PLATAFORMA = "Epic Games"
EPIC_URL = "https://store.epicgames.com/graphql"
EPIC_HASH = os.getenv("EPIC_SEARCH_HASH", "").strip()

HEADERS = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                   "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"),
    "Accept": "application/json",
    "Accept-Language": "es-ES,es;q=0.9",
    "Origin": "https://store.epicgames.com",
    "Referer": "https://store.epicgames.com/",
}

# IDs de tags de género de Epic -> nombre en español (como en tus datos de Steam).
# Los IDs salen de la documentación de un scraper público; si alguno no coincide, ajústalo aquí.
GENEROS_EPIC = {
    "1216": "Acción", "1336": "Acción", "1117": "Aventura", "1181": "Cartas",
    "1296": "Casual", "1116": "Comedia", "1381": "Exploración", "1287": "Fantasía",
    "1344": "Lucha", "1218": "Terror", "1307": "Mundo abierto", "1298": "Puzles",
    "1212": "Carreras", "1367": "Rol", "1393": "Simulación", "1121": "Espacio",
    "1283": "Deportes", "1080": "Supervivencia",
}

CATEGORIA = "games/edition/base|bundles/games|editors"

QUERY_COMPLETA = """
query searchStoreQuery($keywords: String, $country: String!, $locale: String,
                       $count: Int, $category: String) {
  Catalog {
    searchStore(keywords: $keywords, country: $country, locale: $locale,
                count: $count, category: $category, sortBy: "relevancy",
                sortDir: "DESC") {
      elements {
        title description
        seller { name }
        customAttributes { key value }
        keyImages { type url }
        tags { id name groupName }
        price(country: $country) {
          totalPrice { discountPrice originalPrice currencyCode currencyInfo { decimals } }
        }
      }
    }
  }
}
"""


class ConsultaFallida(Exception):
    pass


def _log(debug, *a):
    if debug:
        print("[debug]", *a, file=sys.stderr)


def _elementos(resp, debug):
    _log(debug, "HTTP", resp.status_code, resp.text[:300].replace("\n", " "))
    if resp.status_code != 200:
        raise ConsultaFallida(f"HTTP {resp.status_code} (¿Cloudflare/bloqueo o endpoint cambiado?)")
    try:
        data = resp.json()
    except ValueError:
        raise ConsultaFallida("la respuesta no es JSON")
    if data.get("errors"):
        msg = json.dumps(data["errors"])[:200]
        if "PersistedQueryNotFound" in msg:
            raise ConsultaFallida("PersistedQueryNotFound: el hash ya no es válido, actualiza EPIC_SEARCH_HASH")
        raise ConsultaFallida(f"errores GraphQL: {msg}")
    try:
        return data["data"]["Catalog"]["searchStore"]["elements"] or []
    except (KeyError, TypeError):
        raise ConsultaFallida("estructura de respuesta inesperada")


def _consulta_persistida(texto, pais, locale, limite, debug):
    if not EPIC_HASH:
        raise ConsultaFallida("no hay EPIC_SEARCH_HASH definido")
    variables = {
        "allowCountries": pais, "category": CATEGORIA, "count": limite,
        "country": pais, "keywords": texto, "locale": locale,
        "sortBy": "relevancy,viewableDate", "sortDir": "DESC,DESC",
        "start": 0, "tag": "", "withPrice": True,
    }
    params = {
        "operationName": "searchStoreQuery",
        "variables": json.dumps(variables, separators=(",", ":")),
        "extensions": json.dumps({"persistedQuery": {"version": 1, "sha256Hash": EPIC_HASH}},
                                 separators=(",", ":")),
    }
    r = requests.get(EPIC_URL, params=params, headers=HEADERS, timeout=20)
    return _elementos(r, debug)


def _consulta_completa(texto, pais, locale, limite, debug):
    payload = {"query": QUERY_COMPLETA, "variables": {
        "keywords": texto, "country": pais, "locale": locale,
        "count": limite, "category": "games/edition/base"}}
    r = requests.post(EPIC_URL, json=payload,
                      headers={**HEADERS, "Content-Type": "application/json"}, timeout=20)
    return _elementos(r, debug)


def buscar_en_epic(texto, pais="CO", locale="es-ES", limite=5, debug=False):
    """Prueba primero la consulta persistida (si hay hash) y luego la completa."""
    estrategias = [("persistida", _consulta_persistida), ("completa", _consulta_completa)]
    errores = []
    for nombre, fn in estrategias:
        try:
            return fn(texto, pais, locale, limite, debug)
        except (ConsultaFallida, requests.RequestException) as e:
            errores.append(f"{nombre}: {e}")
            _log(debug, "falló", nombre, e)
    raise RuntimeError(" | ".join(errores))


# ------------------------------ Conversión ------------------------------
def _imagen(key_images):
    por_tipo = {i.get("type"): i.get("url") for i in key_images or [] if i.get("url")}
    for tipo in ("OfferImageWide", "DieselStoreFrontWide", "Thumbnail", "OfferImageTall"):
        if tipo in por_tipo:
            return por_tipo[tipo]
    return next(iter(por_tipo.values()), None)


def _genero(tags):
    for t in tags or []:
        nombre = GENEROS_EPIC.get(str(t.get("id")))
        if nombre:
            return nombre
    for t in tags or []:  # respaldo si la consulta trae nombres
        if t.get("groupName") == "genre" and t.get("name"):
            return t["name"]
    return None


def a_documentos(el):
    """Devuelve (juego, precio) listos para upsert_juego."""
    attrs = {a.get("key"): a.get("value") for a in el.get("customAttributes") or []}
    desarrolladora = (attrs.get("developerName") or el.get("developerDisplayName")
                      or (el.get("seller") or {}).get("name"))
    juego = {
        "nombre": el.get("title"),
        "desarrolladora": desarrolladora,
        "descripcion": el.get("description"),
        "genero": _genero(el.get("tags")),
        "imagenUrl": _imagen(el.get("keyImages")),
    }
    total = ((el.get("price") or {}).get("totalPrice")) or {}
    if "discountPrice" not in total:
        return juego, None  # sin información de precio: no se toca la lista de precios
    decimales = (total.get("currencyInfo") or {}).get("decimals", 2)
    valor = total["discountPrice"] / (10 ** decimales)
    precio = {
        "plataforma": PLATAFORMA,
        "precio": valor,
        "moneda": total.get("currencyCode"),
        "esGratis": total["discountPrice"] == 0,
    }
    return juego, precio


# --------------------------------- Main ---------------------------------
def main():
    ap = argparse.ArgumentParser(description="Epic Games -> MongoDB")
    ap.add_argument("busqueda", nargs="?", help="Texto a buscar, p. ej. 'witcher'")
    ap.add_argument("--limite", type=int, default=5)
    ap.add_argument("--pais", default="CO", help="País del precio (CO = COP)")
    ap.add_argument("--locale", default="es-ES")
    ap.add_argument("--umbral", type=float, default=0.92, help="Similitud mínima de nombres (0-1)")
    ap.add_argument("--sobrescribir", action="store_true",
                    help="Reemplaza descripción/género/etc. aunque ya existan")
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
        resultados = buscar_en_epic(args.busqueda, args.pais, args.locale, args.limite, args.debug)
    except RuntimeError as e:
        sys.exit(f"No se pudo consultar Epic Games -> {e}")
    if not resultados:
        sys.exit("Sin resultados.")

    for el in resultados:
        if not el.get("title"):
            continue
        juego, precio = a_documentos(el)
        estado, motivo = upsert_juego(col, juego, precio, args.sobrescribir, args.umbral)
        extra = f" ({motivo})" if motivo else ""
        print(f"[{estado}{extra}] {juego['nombre']} -> {precio}")


if __name__ == "__main__":
    main()