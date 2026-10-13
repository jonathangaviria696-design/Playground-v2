"""
Utilidades compartidas por los scripts de cada tienda (Steam, Epic, ...).

Idea: cada script solo se encarga de OBTENER datos de su tienda y llama a
`upsert_juego(...)`. Aquí vive todo lo delicado: normalizar nombres, decidir si
un juego ya existe y actualizar precios sin pisar los de otras tiendas.

Además, este archivo es el que INICIA la búsqueda automática: toma juegos
aleatorios (de Steam y GOG), descarta los que ya están en la base de datos,
consulta cada juego en las TRES tiendas al mismo tiempo (Steam, GOG y Epic Games)
y guarda un solo documento con el precio de cada tienda donde lo encontró.

    python juegos_comun.py                        # 10 juegos nuevos, las 3 tiendas
    python juegos_comun.py --cantidad 50
    python juegos_comun.py --tiendas steam,gog    # solo algunas tiendas
    python juegos_comun.py -j "witcher 3"         # busca un juego específico (repetible con varios -j)
    python juegos_comun.py --simular              # muestra qué guardaría, sin escribir
    python juegos_comun.py --migrar               # solo agrega claves a documentos antiguos
"""

from __future__ import annotations

import argparse
import difflib
import os
import re
import sys
import time
import unicodedata
import random
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

from dotenv import load_dotenv
from pymongo import MongoClient, UpdateOne

load_dotenv()

MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017")
DB_NAME = os.getenv("MONGO_DB", "juegos_db")
COLLECTION = os.getenv("MONGO_COLLECTION", "juegos")

# Campos de contenido que se rellenan solo si el documento no los tiene
CAMPOS_CONTENIDO = ("desarrolladora", "descripcion", "genero", "imagenUrl")

# ------------------------- Normalización de nombres -------------------------
_ROMANOS = {"ii": "2", "iii": "3", "iv": "4", "vi": "6", "vii": "7", "viii": "8", "ix": "9"}
_ARTICULOS_INICIALES = {"the", "el", "la", "los", "las"}
_FRASES_EDICION = (
    r"game of the year", r"juego del ano", r"directors cut", r"director s cut",
)
_PALABRAS_EDICION = {
    "edition", "edicion", "remastered", "remaster", "remasterizado", "remasterizada",
    "definitive", "definitiva", "complete", "completa", "deluxe", "standard",
    "estandar", "ultimate", "goty", "enhanced", "mejorada", "anniversary",
    "aniversario", "gold", "premium", "digital",
}


def clave_estricta(nombre: str) -> str:
    """
    Minúsculas, sin acentos, sin ™ ® ©, sin apóstrofes y con cualquier signo
    (: - – — ' ’ & etc.) convertido en un espacio. Así "Assassin’s Creed®: Origins"
    y "Assassins Creed - Origins" dan la misma clave.
    """
    s = nombre or ""
    s = re.sub(r"[™®©℠]", "", s)          # antes de NFKD, que convertiría ™ en "TM"
    s = re.sub(r"[’‘'`´]", "", s)         # apóstrofes: se eliminan (assassin's -> assassins)
    s = s.replace("&", " and ")
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.casefold()
    s = re.sub(r"[\W_]+", " ", s)         # \W es unicode: no borra títulos en japonés, etc.
    return re.sub(r"\s+", " ", s).strip()


def clave_flexible(nombre: str) -> str:
    """
    Como la estricta, pero además ignora artículo inicial, palabras de edición
    (Remastered, Complete Edition, GOTY...) y unifica números romanos (III -> 3).
    """
    base = clave_estricta(nombre)
    for frase in _FRASES_EDICION:
        base = re.sub(rf"\b{frase}\b", " ", base)
    tokens = [t for t in base.split() if t not in _PALABRAS_EDICION]
    if tokens and tokens[0] in _ARTICULOS_INICIALES and len(tokens) > 1:
        tokens = tokens[1:]
    tokens = [_ROMANOS.get(t, t) for t in tokens]
    return " ".join(tokens) or base


def _numeros(clave: str):
    return sorted(re.findall(r"\d+", clave))


# ------------------------------ Normalización ------------------------------
def normalizar_precio(precio: dict) -> dict:
    """Deja el objeto de precio con tipos consistentes sin importar la tienda."""
    valor = precio.get("precio")
    if valor is not None:
        valor = float(valor)
        valor = int(valor) if valor == int(valor) else round(valor, 2)
    return {
        "plataforma": str(precio["plataforma"]).strip(),
        "precio": valor,
        "moneda": (precio.get("moneda") or "").strip().upper() or None,
        "esGratis": bool(precio.get("esGratis", valor == 0)),
    }


# --------------------------------- MongoDB ---------------------------------
def obtener_coleccion():
    if not MONGO_URI:
        raise RuntimeError("Define MONGO_URI en .env o en el entorno.")
    col = MongoClient(MONGO_URI)[DB_NAME][COLLECTION]
    col.create_index("claveNombre")
    col.create_index("claveFlexible")
    return col


def migrar_claves(col) -> int:
    """Agrega claveNombre/claveFlexible a documentos antiguos (ejecutar una vez)."""
    ops = []
    for d in col.find({"claveNombre": {"$exists": False}}, {"nombre": 1}):
        if not d.get("nombre"):
            continue
        ops.append(UpdateOne({"_id": d["_id"]}, {"$set": {
            "claveNombre": clave_estricta(d["nombre"]),
            "claveFlexible": clave_flexible(d["nombre"]),
        }}))
    if ops:
        col.bulk_write(ops)
    return len(ops)


def buscar_existente(col, nombre: str, umbral: float = 0.92):
    """
    Devuelve (documento, motivo) o (None, None).
    Orden: clave estricta -> clave flexible -> documentos sin claves -> similitud.
    """
    estricta, flexible = clave_estricta(nombre), clave_flexible(nombre)

    doc = col.find_one({"claveNombre": estricta})
    if doc:
        return doc, "exacto"
    doc = col.find_one({"claveFlexible": flexible})
    if doc:
        return doc, "flexible"

    # Documentos viejos que aún no tienen claves (hasta que corras --migrar)
    for d in col.find({"claveNombre": {"$exists": False}}, {"nombre": 1}):
        n = d.get("nombre") or ""
        if clave_estricta(n) == estricta or clave_flexible(n) == flexible:
            return col.find_one({"_id": d["_id"]}), "flexible (sin migrar)"

    # Similitud: solo entre candidatos que empiezan igual y con los mismos números
    # (evita confundir "Resident Evil 2" con "Resident Evil 3")
    primera = flexible.split(" ")[0]
    mejor, mejor_ratio = None, 0.0
    for c in col.find({"claveFlexible": {"$regex": "^" + re.escape(primera)}},
                      {"nombre": 1, "claveFlexible": 1}):
        otra = c["claveFlexible"]
        if _numeros(otra) != _numeros(flexible):
            continue
        ratio = difflib.SequenceMatcher(None, flexible, otra).ratio()
        if ratio > mejor_ratio:
            mejor, mejor_ratio = c, ratio
    if mejor and mejor_ratio >= umbral:
        return col.find_one({"_id": mejor["_id"]}), f"similar {mejor_ratio:.2f} a '{mejor['nombre']}'"
    return None, None


def upsert_juego(col, juego: dict, precio: dict | None, sobrescribir: bool = False,
                 umbral: float = 0.92):
    """
    juego:  {nombre, desarrolladora, descripcion, genero, imagenUrl}
    precio: {plataforma, precio, moneda, esGratis}  (o None si no hay precio)

    - Si el juego no existe, lo inserta.
    - Si existe, NO cambia su nombre ni sus textos (a menos que sobrescribir=True);
      solo rellena campos vacíos y agrega/actualiza el precio de ESA plataforma.
    - Es atómico por plataforma: dos scripts pueden correr a la vez sin pisarse.
    Devuelve (estado, motivo).
    """
    ahora = datetime.now(timezone.utc)
    precio = normalizar_precio(precio) if precio else None
    existente, motivo = buscar_existente(col, juego["nombre"], umbral)

    if existente is None:
        doc = {k: juego.get(k) for k in ("nombre",) + CAMPOS_CONTENIDO}
        doc.update({
            "claveNombre": clave_estricta(juego["nombre"]),
            "claveFlexible": clave_flexible(juego["nombre"]),
            "precios": [precio] if precio else [],
            "fechaActualizacion": ahora,
        })
        col.insert_one(doc)
        return "insertado", None

    campos = {}
    for k in CAMPOS_CONTENIDO:
        nuevo = juego.get(k)
        if nuevo and (sobrescribir or not existente.get(k)):
            campos[k] = nuevo
    if "claveNombre" not in existente:
        campos["claveNombre"] = clave_estricta(existente["nombre"])
        campos["claveFlexible"] = clave_flexible(existente["nombre"])
    campos["fechaActualizacion"] = ahora

    if precio is None:
        col.update_one({"_id": existente["_id"]}, {"$set": campos})
    else:
        r = col.update_one(
            {"_id": existente["_id"], "precios.plataforma": precio["plataforma"]},
            {"$set": {**campos, "precios.$": precio}},
        )
        if r.matched_count == 0:  # esa plataforma aún no tenía precio
            col.update_one({"_id": existente["_id"]},
                           {"$set": campos, "$push": {"precios": precio}})
    return "actualizado", motivo


# ------------------------- Búsqueda automática -------------------------
TIENDAS = ("Steam", "GOG", "Epic Games")                  # también es el orden de prioridad del contenido
ALIAS_TIENDAS = {"steam": "Steam", "gog": "GOG", "epic": "Epic Games", "epic games": "Epic Games"}
TIENDAS_CON_DESCUBRIMIENTO = ("Steam", "GOG")             # de dónde salen los juegos aleatorios


def mismo_juego(a: str, b: str, umbral: float = 0.9) -> bool:
    """¿Dos títulos de tiendas distintas son el mismo juego? (mismos números y nombre casi igual)."""
    fa, fb = clave_flexible(a), clave_flexible(b)
    if not fa or not fb:
        return False
    if fa == fb:
        return True
    if _numeros(fa) != _numeros(fb):
        return False
    return difflib.SequenceMatcher(None, fa, fb).ratio() >= umbral


def _relevante_libre(titulo: str, busqueda: str) -> bool:
    """¿El título contiene todas las palabras de lo que escribió el usuario? ('witcher 3' -> 'The Witcher 3: ...')"""
    t, b = clave_estricta(titulo or "").split(), clave_estricta(busqueda or "").split()
    return bool(b) and all(any(w == x or w in x for x in t) for w in b)


# Cada buscador recibe (nombre, ref, libre) y devuelve (juego, precio|None) o None.
# `ref`: solo viene cuando el juego se descubrió en esa misma tienda (evita volver a buscarlo).
# `libre`: el nombre es texto parcial escrito por el usuario; se acepta el primer resultado que lo contenga.
def _buscar_steam(nombre, ref=None, libre=False):
    import steam
    datos = steam.obtener_steam(ref) if ref else steam.buscar_juego_steam(nombre)
    return steam.a_documentos(datos) if datos else None


def _buscar_gog(nombre, ref=None, libre=False):
    import gog
    producto = ref
    if producto is None:
        coincide = _relevante_libre if libre else (lambda t, q: mismo_juego(q, t))
        producto = next((p for p in gog.buscar_en_gog(nombre, limite=10)
                         if coincide(p.get("title", ""), nombre)), None)
    if producto is None:
        return None
    descripcion = gog.obtener_descripcion(str(producto["id"]), "es-ES", False) if producto.get("id") else None
    return gog.a_documentos(producto, descripcion)


def _buscar_epic(nombre, ref=None, libre=False):
    import epic
    coincide = _relevante_libre if libre else (lambda t, q: mismo_juego(q, t))
    elementos = epic.buscar_en_epic(nombre, limite=10)   # lanza RuntimeError si Epic falla
    elemento = next((e for e in elementos if coincide(e.get("title") or "", nombre)), None)
    return epic.a_documentos(elemento) if elemento else None


BUSCADORES = {"Steam": _buscar_steam, "GOG": _buscar_gog, "Epic Games": _buscar_epic}


def _candidatos(tienda: str) -> list[dict]:
    """Juegos aleatorios de una tienda: [{tienda, nombre, id, ref}, ...]."""
    if tienda == "Steam":
        import steam
        return [{"tienda": "Steam", "nombre": n, "id": i, "ref": i} for i, n in steam.candidatos_aleatorios()]
    import gog
    return [{"tienda": "GOG", "nombre": p["title"], "id": p["id"], "ref": p} for p in gog.candidatos_aleatorios()]


def _consultar_tiendas(cand: dict, tiendas: list[str], umbral: float, fallos: dict | None = None,
                       libre: bool = False) -> dict:
    """Busca el juego en todas las tiendas a la vez. {tienda: (juego, precio) | None}."""
    fallos = fallos if fallos is not None else {}

    def tarea(tienda):
        origen = tienda == cand["tienda"]
        try:
            res = BUSCADORES[tienda](cand["nombre"], cand["ref"] if origen else None, libre)
            fallos[tienda] = 0
        except Exception as e:      # una tienda caída no debe detener a las otras
            fallos[tienda] = fallos.get(tienda, 0) + 1
            print(f"⚠️  {tienda}: {type(e).__name__}: {e}")
            return tienda, None
        if res and not origen and not libre and not mismo_juego(cand["nombre"], res[0].get("nombre") or "", umbral):
            print(f"↪️  {tienda}: coincidencia descartada ('{res[0].get('nombre')}' != '{cand['nombre']}')")
            return tienda, None
        return tienda, res

    with ThreadPoolExecutor(max_workers=len(tiendas)) as ex:
        return dict(ex.map(tarea, tiendas))


def _fusionar(resultados: dict) -> tuple[dict, list[dict]]:
    """Un solo juego con el contenido de la primera tienda que lo tenga, y todos los precios."""
    juego, precios = {}, []
    for tienda in TIENDAS:
        res = resultados.get(tienda)
        if not res:
            continue
        j, precio = res
        for k in ("nombre",) + CAMPOS_CONTENIDO:
            if not juego.get(k) and j.get(k):
                juego[k] = j[k]
        if precio:
            precios.append(precio)
    return juego, precios


def _guardar(col, juego: dict, precios: list[dict], umbral: float, sobrescribir: bool = False) -> str:
    """Guarda el juego con el precio de cada tienda. Devuelve 'insertado' o 'actualizado'."""
    estado, _ = upsert_juego(col, juego, precios[0], sobrescribir, umbral)
    for precio in precios[1:]:
        upsert_juego(col, juego, precio, sobrescribir, umbral)
    return estado


def _linea_precios(precios: list[dict]) -> str:
    return " · ".join(f"{p['plataforma']} {p['precio']} {p['moneda']}" for p in precios)


def buscar_juego_especifico(col, consulta: str, tiendas: list[str] | None = None, umbral: float = 0.92,
                            simular: bool = False, sobrescribir: bool = False) -> str:
    """
    Busca UN juego por nombre (puede ser parcial: 'witcher 3') en las tiendas a la vez y lo guarda.

      1. Busca el texto en todas las tiendas; el primer resultado en orden Steam, GOG, Epic
         fija el nombre oficial del juego.
      2. Con ese nombre consulta las demás tiendas, descartando coincidencias dudosas.
      3. Si el juego ya existe en Mongo NO se omite: se actualizan/agregan los precios de cada
         tienda (sirve para completar juegos que se guardaron sin precio de alguna tienda).

    Devuelve 'insertado', 'actualizado', 'simulado', 'sin_precio' o 'no_encontrado'.
    """
    tiendas = [t for t in TIENDAS if t in (tiendas or TIENDAS)]
    print(f"\n🔎 '{consulta}' en {', '.join(tiendas)}...")

    libres = _consultar_tiendas({"tienda": None, "nombre": consulta, "id": None, "ref": None},
                                tiendas, umbral, libre=True)
    base = next((t for t in TIENDAS if libres.get(t)), None)
    if base is None:
        print(f"❌ No se encontró '{consulta}' en ninguna tienda.")
        return "no_encontrado"

    resultados = {base: libres[base]}
    nombre_oficial = libres[base][0]["nombre"]
    otras = [t for t in tiendas if t != base]
    if otras:
        resultados.update(_consultar_tiendas(
            {"tienda": None, "nombre": nombre_oficial, "id": None, "ref": None}, otras, umbral))

    juego, precios = _fusionar(resultados)
    sin_resultado = [t for t in tiendas if not resultados.get(t)]
    if not precios:
        print(f"🚫 '{juego['nombre']}' no tiene precio disponible en ninguna tienda.")
        return "sin_precio"

    if simular:
        estado = "simulado"
    else:
        estado = _guardar(col, juego, precios, umbral, sobrescribir)
    print(f"✅ [{estado}] {juego['nombre']} | {juego.get('genero')} | {_linea_precios(precios)}")
    if sin_resultado:
        print(f"   sin resultado en: {', '.join(sin_resultado)}")
    return estado


def buscar_juegos_aleatorios(col, cantidad: int = 10, pausa: float = 1.5, umbral: float = 0.92,
                             max_paginas: int | None = None, simular: bool = False,
                             tiendas: list[str] | None = None) -> dict:
    """
    Encuentra `cantidad` juegos NUEVOS (que no estén en la base de datos) y los guarda con
    el precio de cada tienda donde existan.

    Por cada candidato aleatorio (de Steam o GOG, alternando al azar):
      1. Si el nombre ya está en Mongo (exacto, flexible o similar) -> se omite sin consultar tiendas.
      2. Se consulta en TODAS las tiendas a la vez; las coincidencias dudosas se descartan.
      3. Se fusionan los datos; si ninguna tienda lo tiene disponible con precio, se omite.
      4. Se revisa otra vez con el nombre final y se guarda con upsert_juego.

    `pausa`: segundos entre candidatos (Steam limita ~200 consultas cada 5 min).
    """
    tiendas = [t for t in TIENDAS if t in (tiendas or TIENDAS)]
    fuentes = [t for t in TIENDAS_CON_DESCUBRIMIENTO if t in tiendas]
    if not fuentes:
        raise ValueError("Activa al menos Steam o GOG: de ahí salen los juegos aleatorios.")

    resumen = {"guardados": 0, "ya_existian": 0, "no_disponibles": 0, "errores": 0}
    fallos: dict[str, int] = {}
    vistos: set[tuple] = set()
    max_paginas = max_paginas or max(10, cantidad)
    paginas_vacias = 0

    try:
        for _ in range(max_paginas):
            if resumen["guardados"] >= cantidad or not fuentes:
                break

            candidatos = _candidatos(random.choice(fuentes))
            if not candidatos:
                paginas_vacias += 1
                resumen["errores"] += 1
                if paginas_vacias >= 5:
                    print("⛔ Las tiendas no responden; me detengo.")
                    break
                time.sleep(3)
                continue
            paginas_vacias = 0

            for cand in candidatos:
                if resumen["guardados"] >= cantidad or not fuentes:
                    break
                clave_vista = (cand["tienda"], cand["id"])
                if clave_vista in vistos:
                    continue
                vistos.add(clave_vista)

                existente, motivo = buscar_existente(col, cand["nombre"], umbral)
                if existente:
                    resumen["ya_existian"] += 1
                    print(f"⏭️  ya existe ({motivo}): {cand['nombre']}")
                    continue

                time.sleep(pausa)
                resultados = _consultar_tiendas(cand, tiendas, umbral, fallos)
                for t in [t for t in tiendas if fallos.get(t, 0) >= 3]:
                    tiendas.remove(t)
                    print(f"🔌 {t} falló 3 veces seguidas; la desactivo para el resto de esta corrida.")
                fuentes = [f for f in fuentes if f in tiendas]
                if not any(resultados.values()):
                    resumen["errores"] += 1
                    continue

                juego, precios = _fusionar(resultados)
                if not precios:
                    resumen["no_disponibles"] += 1
                    print(f"🚫 sin precio en ninguna tienda: {cand['nombre']}")
                    continue

                existente, motivo = buscar_existente(col, juego["nombre"], umbral)
                if existente:   # el nombre oficial puede diferir del que mostró la búsqueda
                    resumen["ya_existian"] += 1
                    print(f"⏭️  ya existe ({motivo}): {juego['nombre']}")
                    continue

                estado = "simulado" if simular else _guardar(col, juego, precios, umbral)
                if estado in ("insertado", "simulado"):
                    resumen["guardados"] += 1
                    print(f"✅ [{estado}] ({resumen['guardados']}/{cantidad}) "
                          f"{juego['nombre']} | {juego.get('genero')} | {_linea_precios(precios)}")
                else:               # otro proceso lo insertó justo antes
                    resumen["ya_existian"] += 1
    except KeyboardInterrupt:
        print("\n⏹️  Interrumpido por el usuario.")

    return resumen


def main():
    ap = argparse.ArgumentParser(description="Búsqueda automática de juegos aleatorios (Steam + GOG + Epic) -> MongoDB")
    ap.add_argument("--juego", "-j", action="append", metavar="NOMBRE",
                    help="Busca un juego específico en las tiendas (repetible: -j 'witcher 3' -j hades). "
                         "Si ya existe, completa/actualiza sus precios")
    ap.add_argument("--sobrescribir", action="store_true",
                    help="Con --juego: reemplaza descripción/género/etc. aunque ya existan")
    ap.add_argument("--cantidad", type=int, default=10, help="Cuántos juegos aleatorios NUEVOS guardar")
    ap.add_argument("--tiendas", default="steam,gog,epic", help="Tiendas a consultar, separadas por coma")
    ap.add_argument("--pausa", type=float, default=1.5, help="Segundos entre candidatos")
    ap.add_argument("--umbral", type=float, default=0.92, help="Similitud mínima de nombres (0-1)")
    ap.add_argument("--max-paginas", type=int, default=None, help="Tope de páginas aleatorias a revisar")
    ap.add_argument("--simular", action="store_true", help="No escribe en MongoDB, solo muestra")
    ap.add_argument("--migrar", action="store_true",
                    help="Agrega claves de búsqueda a documentos antiguos y termina")
    args = ap.parse_args()

    try:
        tiendas = [ALIAS_TIENDAS[t.strip().lower()] for t in args.tiendas.split(",") if t.strip()]
    except KeyError as e:
        ap.error(f"tienda desconocida {e}; usa steam, gog y/o epic")

    col = obtener_coleccion()
    migrados = migrar_claves(col)   # idempotente; evita el recorrido lento de buscar_existente
    if args.migrar:
        print(f"Documentos migrados: {migrados}")
        return
    if migrados:
        print(f"Documentos antiguos migrados: {migrados}")

    if args.juego:
        estados = [buscar_juego_especifico(col, nombre, tiendas, args.umbral, args.simular, args.sobrescribir)
                   for nombre in args.juego]
        if any(e in ("no_encontrado", "sin_precio") for e in estados):
            sys.exit(1)
        return

    print(f"🎲 Buscando {args.cantidad} juegos nuevos en {', '.join(tiendas)}...")
    try:
        r = buscar_juegos_aleatorios(col, args.cantidad, args.pausa, args.umbral,
                                     args.max_paginas, args.simular, tiendas)
    except ValueError as e:
        sys.exit(str(e))
    print(f"\nResumen: {r['guardados']} guardados, {r['ya_existian']} ya existían, "
          f"{r['no_disponibles']} sin precio, {r['errores']} errores.")
    if r["guardados"] < args.cantidad:
        sys.exit(1)


if __name__ == "__main__":
    main()