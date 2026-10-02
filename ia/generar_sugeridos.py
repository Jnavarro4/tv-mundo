"""
Genera `sugeridos.json` para TV Mundo usando Claude con búsqueda web.

1. Descarga la API de iptv-org y arma la lista de canales candidatos
   (deportes, noticias y canales reconocidos, priorizando los de habla hispana).
2. Investigación: Claude busca en la web los eventos importantes de las próximas
   ~36 horas para el público hispanohablante (partidos, noticias, especiales).
3. Estructuración: Claude convierte esa investigación en JSON con un esquema fijo
   (structured outputs) y relaciona cada evento con canales de la lista.
4. Se validan los ids de canal y las fechas, y se escribe el archivo.

Uso:
    ANTHROPIC_API_KEY=... python ia/generar_sugeridos.py --salida sugeridos.json
    python ia/generar_sugeridos.py --solo-candidatos   # sin IA: muestra la lista de canales

La app lee el resultado desde la rama `datos` del repositorio.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request
from datetime import datetime, timedelta, timezone

API = "https://iptv-org.github.io/api"
MODELO = os.environ.get("MODELO_CLAUDE") or "claude-opus-5-5"
ZONA_COLOMBIA = timezone(timedelta(hours=-5))

HISPANOS = {
    "CO", "MX", "AR", "ES", "CL", "PE", "VE", "EC", "BO", "PY", "UY",
    "CR", "PA", "GT", "HN", "SV", "NI", "DO", "CU", "PR",
}

# Mismos canales que WorldChannels.kt / FeaturedChannels.kt (siempre candidatos).
SIEMPRE = {
    "CaracolTV.co", "CanalRCN.co", "NoticiasRCN.co", "NTN24.co", "WinSports.co", "CitytvBogota.co",
    "LasEstrellas.mx", "Canal5.mx", "ImagenTV.mx", "MilenioTelevision.mx", "AztecaDeportesNetwork.mx",
    "TN.ar", "Telefe.ar", "ElTrece.ar", "TyCSports.ar", "La1.es", "Antena3.es", "24Horas.es",
    "RealMadridTV.es", "Telemundo.us", "Univision.us", "ESPNDeportes.us", "FoxDeportes.us",
    "DW.de", "France24.fr", "EuronewsSpanish.fr", "CGTNSpanish.cn", "BBCNews.uk", "AlJazeera.qa",
    "RedBullTV.at", "FIFAPlus.uk", "OlympicChannel.es", "beINSPORTSXTRAenEspanol.us",
    "TVEInternacionalAmerica.es", "NationalGeographicLatinAmerica.us", "Teledeporte.es",
}

ESQUEMA = {
    "type": "object",
    "properties": {
        "eventos": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "titulo": {"type": "string"},
                    "descripcion": {"type": "string"},
                    "tipo": {"type": "string", "enum": ["futbol", "deporte", "noticias", "cine", "musica", "especial"]},
                    "competicion": {"type": "string"},
                    "inicio": {"type": "string", "description": "ISO 8601 con zona horaria, p. ej. 2026-10-03T20:30:00-05:00"},
                    "duracion_min": {"type": "integer"},
                    "importancia": {"type": "integer", "description": "1 a 5"},
                    "canales": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["titulo", "descripcion", "tipo", "competicion", "inicio", "duracion_min", "importancia", "canales"],
                "additionalProperties": False,
            },
        },
        "recomendados": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "canal": {"type": "string"},
                    "motivo": {"type": "string"},
                },
                "required": ["canal", "motivo"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["eventos", "recomendados"],
    "additionalProperties": False,
}


def descargar(nombre: str):
    with urllib.request.urlopen(f"{API}/{nombre}", timeout=120) as r:
        return json.load(r)


def canales_candidatos() -> list[dict]:
    """Canales con stream, sin NSFW ni cerrados: deportes y noticias hispanos + reconocidos."""
    streams = descargar("streams.json")
    con_stream = {s["channel"] for s in streams if s.get("channel")}
    feeds: dict[str, set[str]] = {}
    for s in streams:
        if s.get("channel") and s.get("feed"):
            feeds.setdefault(s["channel"], set()).add(s["feed"])

    candidatos = []
    for c in descargar("channels.json"):
        cid = c["id"]
        if cid not in con_stream or c.get("is_nsfw") or c.get("closed"):
            continue
        categorias = c.get("categories") or []
        hispano = c.get("country") in HISPANOS
        deportes = "sports" in categorias
        noticias = "news" in categorias
        if cid in SIEMPRE or (deportes and (hispano or c.get("country") == "US")) or (noticias and hispano and c.get("country") in {"CO", "MX", "AR", "ES"}):
            candidatos.append({
                "id": cid,
                "nombre": c["name"],
                "pais": c.get("country"),
                "categorias": categorias,
                "feeds": sorted(feeds.get(cid, set())),
            })
    return candidatos


def texto_candidatos(candidatos: list[dict]) -> str:
    lineas = []
    for c in candidatos:
        feeds = [f for f in c["feeds"] if f not in ("SD", "HD")]
        extra = f" | feeds: {', '.join(feeds[:6])}" if feeds else ""
        lineas.append(f"{c['id']} | {c['nombre']} | {c['pais']} | {', '.join(c['categorias'])}{extra}")
    return "\n".join(lineas)


def investigar(client, ahora: datetime) -> str:
    """Paso 1: Claude busca en la web los eventos del día. Devuelve un informe en texto."""
    pedido = (
        f"Hoy es {ahora.strftime('%A %d de %B de %Y, %H:%M')} hora de Colombia (UTC-5).\n\n"
        "Eres el editor de una app de TV en vivo gratuita para público hispanohablante "
        "(principalmente Colombia y Latinoamérica, también España y EE. UU. hispano).\n"
        "Investiga en la web y haz una lista de los eventos más importantes que se puedan ver o seguir "
        "por televisión entre ahora y las próximas 36 horas:\n"
        "- Fútbol: Liga BetPlay (Colombia), Liga MX, Liga Profesional Argentina, LaLiga, Premier League, "
        "Champions/Europa League, Copa Libertadores/Sudamericana, eliminatorias y partidos de selecciones.\n"
        "- Otros deportes grandes: Fórmula 1, NBA/MLB en playoffs, tenis (Grand Slam/Masters), boxeo, ciclismo.\n"
        "- Noticias o acontecimientos de gran interés, premiaciones, conciertos o especiales transmitidos.\n"
        "Para cada evento indica: título, competición, fecha y hora exactas con zona horaria, "
        "una descripción breve en español y qué cadenas lo transmiten (según las fuentes). "
        "Incluye solo eventos confirmados por fuentes; no inventes horarios. Prioriza calidad: 8 a 15 eventos."
    )
    mensajes = [{"role": "user", "content": pedido}]
    for _ in range(6):  # pause_turn: el servidor puede pausar búsquedas largas
        with client.beta.messages.stream(
            model=MODELO,
            max_tokens=32000,
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            output_config={"effort": "medium"},
            tools=[{
                "type": "web_search_20260209",
                "name": "web_search",
                "max_uses": 15,
                "user_location": {"type": "approximate", "city": "Bogotá", "country": "CO", "timezone": "America/Bogota"},
            }],
            messages=mensajes,
        ) as stream:
            respuesta = stream.get_final_message()
        if respuesta.stop_reason == "refusal":
            raise RuntimeError(f"La investigación fue rechazada: {respuesta.stop_details}")
        if respuesta.stop_reason == "pause_turn":
            mensajes = [mensajes[0], {"role": "assistant", "content": respuesta.content}]
            continue
        return "\n".join(b.text for b in respuesta.content if b.type == "text")
    raise RuntimeError("La investigación quedó en pausa demasiadas veces")


def estructurar(client, informe: str, candidatos: list[dict], ahora: datetime) -> dict:
    """Paso 2: convertir el informe en JSON con esquema fijo y elegir canales de la lista."""
    pedido = (
        f"Fecha y hora actual: {ahora.isoformat()} (Colombia).\n\n"
        "<informe>\n" + informe + "\n</informe>\n\n"
        "<canales_disponibles>\n" + texto_candidatos(candidatos) + "\n</canales_disponibles>\n\n"
        "Convierte el informe en datos para la app:\n"
        "- `eventos`: solo los del informe, con `inicio` en ISO 8601 con zona horaria. "
        "`canales`: ids EXACTOS de <canales_disponibles> que probablemente lo transmitan o lo cubran en vivo "
        "(por ejemplo el canal oficial del torneo o la cadena con derechos según el informe). Si un canal tiene un feed "
        "en español, puedes escribir `id@feed` (ej. `DW.de@Espanol`). Si ninguno aplica, deja la lista vacía: "
        "no fuerces coincidencias. `descripcion`: 1-2 frases en español neutro. `importancia` de 1 a 5. "
        "`competicion`: nombre del torneo o \"\" si no aplica.\n"
        "- `recomendados`: 6 a 10 canales de la lista que valga la pena ver hoy (por los eventos o la actualidad), "
        "con un `motivo` corto y concreto en español (máx. 120 caracteres)."
    )
    respuesta = client.beta.messages.create(
        model=MODELO,
        max_tokens=16000,
        betas=["server-side-fallback-2026-07-01"],
        fallbacks="default",
        output_config={"effort": "low", "format": {"type": "json_schema", "schema": ESQUEMA}},
        messages=[{"role": "user", "content": pedido}],
    )
    if respuesta.stop_reason == "refusal":
        raise RuntimeError(f"La estructuración fue rechazada: {respuesta.stop_details}")
    if respuesta.stop_reason == "max_tokens":
        raise RuntimeError("La respuesta se cortó por max_tokens")
    texto = next(b.text for b in respuesta.content if b.type == "text")
    return json.loads(texto)


def validar(datos: dict, candidatos: list[dict], ahora: datetime) -> dict:
    """Descarta ids inexistentes y fechas inválidas; agrega `id` e `inicio_epoch` para la app."""
    ids = {c["id"] for c in candidatos}
    feeds = {c["id"]: set(c["feeds"]) for c in candidatos}

    def canal_valido(entrada: str) -> bool:
        cid, _, feed = entrada.partition("@")
        return cid in ids and (not feed or feed in feeds.get(cid, set()))

    eventos = []
    for e in datos.get("eventos", []):
        try:
            inicio = datetime.fromisoformat(e["inicio"].replace("Z", "+00:00"))
        except (KeyError, ValueError):
            continue
        if inicio.tzinfo is None:
            inicio = inicio.replace(tzinfo=ZONA_COLOMBIA)
        duracion = max(30, min(int(e.get("duracion_min") or 120), 600))
        if inicio + timedelta(minutes=duracion) < ahora or inicio > ahora + timedelta(hours=48):
            continue
        epoch = int(inicio.timestamp())
        titulo = e["titulo"].strip()
        eventos.append({
            "id": f"{epoch}-{''.join(ch for ch in titulo.lower() if ch.isalnum())[:40]}",
            "titulo": titulo,
            "descripcion": e.get("descripcion", "").strip(),
            "tipo": e.get("tipo", "especial"),
            "competicion": (e.get("competicion") or "").strip() or None,
            "inicio_epoch": epoch,
            "duracion_min": duracion,
            "importancia": max(1, min(int(e.get("importancia") or 3), 5)),
            "canales": [c for c in dict.fromkeys(e.get("canales", [])) if canal_valido(c)],
        })
    eventos.sort(key=lambda e: (e["inicio_epoch"], -e["importancia"]))

    recomendados = [
        {"canal": r["canal"], "motivo": r["motivo"].strip()[:160]}
        for r in datos.get("recomendados", [])
        if canal_valido(r.get("canal", "")) and r.get("motivo")
    ]
    return {
        "version": 1,
        "generado": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "fuente": f"{MODELO} + búsqueda web",
        "eventos": eventos,
        "recomendados": recomendados,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--salida", default="sugeridos.json")
    parser.add_argument("--solo-candidatos", action="store_true", help="no llama a la IA; imprime los canales candidatos")
    args = parser.parse_args()

    candidatos = canales_candidatos()
    print(f"{len(candidatos)} canales candidatos", file=sys.stderr)
    if args.solo_candidatos:
        print(texto_candidatos(candidatos))
        return 0

    import anthropic  # solo hace falta para generar

    client = anthropic.Anthropic()
    ahora = datetime.now(ZONA_COLOMBIA)
    try:
        informe = investigar(client, ahora)
        print(f"Informe: {len(informe)} caracteres", file=sys.stderr)
        datos = estructurar(client, informe, candidatos, ahora)
    except anthropic.AuthenticationError:
        print("ANTHROPIC_API_KEY inválida", file=sys.stderr)
        return 1
    except anthropic.RateLimitError as e:
        print(f"Límite de uso de la API: {e.message}", file=sys.stderr)
        return 1
    except anthropic.APIStatusError as e:
        print(f"Error de la API ({e.status_code}): {e.message} [request_id={e.request_id}]", file=sys.stderr)
        return 1
    except anthropic.APIConnectionError:
        print("No se pudo conectar con la API de Claude", file=sys.stderr)
        return 1

    resultado = validar(datos, candidatos, ahora)
    with open(args.salida, "w", encoding="utf-8") as f:
        json.dump(resultado, f, ensure_ascii=False, indent=2)
    print(f"{len(resultado['eventos'])} eventos y {len(resultado['recomendados'])} recomendados -> {args.salida}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
