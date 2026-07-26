#!/usr/bin/env python3
"""One-shot Stripe test-mode setup for Katedra Pro.

Creates the product and both recurring prices in Stripe, then writes the secrets the
backend reads into Doppler. Safe to re-run: an existing "Katedra Pro" product and any
price with the same amount and interval are reused instead of duplicated (Stripe prices
are immutable, and a second identical price would silently split reporting).

Usage:
    export STRIPE_SECRET_KEY=sk_test_...
    export STRIPE_PUBLISHABLE_KEY=pk_test_...
    export STRIPE_WEBHOOK_SECRET=whsec_...      # optional, see the setup checklist
    python3 scripts/setup_stripe.py

Only the standard library and the doppler CLI are required.
"""

import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

PRODUCTO = "Katedra Pro"
DESCRIPCION = (
    "Generaciones y exportaciones ampliadas, modelo Catedrático "
    "y todas las fuentes de temario."
)
MONEDA = "usd"
PRECIOS = {"month": 1900, "year": 18000}  # cents, aligned with utils/plan.js
API = "https://api.stripe.com/v1/"


def fallar(mensaje):
    print(f"error: {mensaje}", file=sys.stderr)
    sys.exit(1)


def stripe(ruta, datos=None, clave=""):
    """Call the Stripe REST API. GET when `datos` is None, POST otherwise."""
    cuerpo = urllib.parse.urlencode(datos, doseq=True).encode() if datos else None
    peticion = urllib.request.Request(API + ruta, data=cuerpo)
    peticion.add_header("Authorization", f"Bearer {clave}")
    try:
        with urllib.request.urlopen(peticion, timeout=30) as respuesta:
            return json.load(respuesta)
    except urllib.error.HTTPError as err:
        detalle = json.load(err).get("error", {}).get("message", err.reason)
        fallar(f"Stripe respondió {err.code}: {detalle}")
    except urllib.error.URLError as err:
        fallar(f"no se pudo contactar a Stripe: {err.reason}")


def obtener_producto(clave):
    consulta = urllib.parse.urlencode({"query": f'name:"{PRODUCTO}"'})
    encontrados = stripe(f"products/search?{consulta}", clave=clave).get("data", [])
    if encontrados:
        print(f"    reutilizando {encontrados[0]['id']}")
        return encontrados[0]["id"]

    print("    no existe, creándolo")
    return stripe(
        "products",
        {"name": PRODUCTO, "description": DESCRIPCION},
        clave=clave,
    )["id"]


def obtener_precio(producto_id, intervalo, monto, clave):
    consulta = urllib.parse.urlencode(
        {"product": producto_id, "active": "true", "limit": 100}
    )
    for precio in stripe(f"prices?{consulta}", clave=clave).get("data", []):
        recurrente = precio.get("recurring") or {}
        if recurrente.get("interval") == intervalo and precio.get("unit_amount") == monto:
            return precio["id"]

    return stripe(
        "prices",
        {
            "product": producto_id,
            "currency": MONEDA,
            "unit_amount": monto,
            "recurring[interval]": intervalo,
        },
        clave=clave,
    )["id"]


def escribir_doppler(secretos, proyecto, config):
    orden = ["doppler", "secrets", "set", "--project", proyecto, "--config", config, "--silent"]
    orden += [f"{nombre}={valor}" for nombre, valor in secretos.items()]
    resultado = subprocess.run(orden, capture_output=True, text=True)
    if resultado.returncode != 0:
        fallar(f"doppler falló: {resultado.stderr.strip() or resultado.stdout.strip()}")


def main():
    secreta = os.environ.get("STRIPE_SECRET_KEY", "")
    publicable = os.environ.get("STRIPE_PUBLISHABLE_KEY", "")
    webhook = os.environ.get("STRIPE_WEBHOOK_SECRET", "")
    proyecto = os.environ.get("DOPPLER_PROJECT", "katedra-server")
    config = os.environ.get("DOPPLER_CONFIG", "dev")

    # Refusing live keys is deliberate: this project bills in test mode only, and a stray
    # sk_live_ here would create real, chargeable prices in a production account.
    if not secreta.startswith("sk_test_"):
        fallar("exporta STRIPE_SECRET_KEY con una clave sk_test_ (modo prueba)")
    if not publicable.startswith("pk_test_"):
        fallar("exporta STRIPE_PUBLISHABLE_KEY con una clave pk_test_")

    print(f'==> Producto "{PRODUCTO}"…')
    producto_id = obtener_producto(secreta)

    print("==> Precio mensual (19.00 USD/mes)…")
    price_mensual = obtener_precio(producto_id, "month", PRECIOS["month"], secreta)
    print(f"    {price_mensual}")

    print("==> Precio anual (180.00 USD/año)…")
    price_anual = obtener_precio(producto_id, "year", PRECIOS["year"], secreta)
    print(f"    {price_anual}")

    secretos = {
        "STRIPE_SECRET_KEY": secreta,
        "STRIPE_PUBLISHABLE_KEY": publicable,
        "STRIPE_PRICE_PRO_MENSUAL": price_mensual,
        "STRIPE_PRICE_PRO_ANUAL": price_anual,
    }
    if webhook:
        secretos["STRIPE_WEBHOOK_SECRET"] = webhook

    print(f"==> Escribiendo secretos en Doppler ({proyecto}/{config})…")
    escribir_doppler(secretos, proyecto, config)

    if not webhook:
        # The backend boots without it; only signature verification on /webhooks/stripe
        # fails. Checkout still completes because the client confirms explicitly.
        print("    aviso: STRIPE_WEBHOOK_SECRET no definido. El checkout funciona igual")
        print("    (el cliente confirma con POST /suscripciones/{id}/confirmar); el webhook")
        print("    es la red de seguridad. Añádelo después con:")
        print('      doppler secrets set STRIPE_WEBHOOK_SECRET "whsec_..."')

    print()
    print("Listo. Verifica con:")
    print(f"  doppler secrets --project {proyecto} --config {config} --only-names | grep STRIPE")
    print("Arranca el backend con:")
    print("  doppler run -- ./mvnw spring-boot:run")


if __name__ == "__main__":
    main()
