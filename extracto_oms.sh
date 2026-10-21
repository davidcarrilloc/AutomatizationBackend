#!/usr/bin/env bash
#
# extracto_oms.sh — Extracto BC -> carpetas locales.
#
# Reproduce el módulo del backend SIN levantarlo: consulta Oracle sobre el DÍA ANTERIOR completo y deja,
# junto a este script:
#   liverpool/lp_sl_<yyyyMMdd>.csv    = remisiones Liverpool SoftLine (BRIDGECORE, tipo '1')
#   liverpool/lp_bt_<yyyyMMdd>.csv    = órdenes de venta Liverpool Big Ticket (BRIDGECORE, tipo '0')
#   suburbia/sbb_sl_<yyyyMMdd>.csv    = shipping groups Suburbia SoftLine (BRIDGECORE2, tipo '1')
#   suburbia/sbb_bt_<yyyyMMdd>.csv    = shipping groups Suburbia Big Ticket (BRIDGECORE2, tipo '0')
#
# El SQL y el driver Oracle viven en extracto-oms.jar (fuente: extracto-oms/Extractor.java).
# Credenciales en extracto_oms.properties junto al script (copiar de extracto_oms.properties.example).
# La máquina destino solo necesita java (JRE >= 8). No requiere internet.
#
# Uso:
#   ./extracto_oms.sh              # procesa el día de ayer
#   ./extracto_oms.sh 20260921     # procesa un día específico (YYYYMMDD)
#   DRY_RUN=1 ./extracto_oms.sh    # muestra fecha/ventana/archivos y sale, sin tocar Oracle
#
# Salida: 0 = los 4 archivos generados; 1 = alguna consulta o la conexión falló (su archivo no se crea).
#
set -euo pipefail

# El jar y el properties van junto al script (se resuelve aunque se copie a otra máquina).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/extracto-oms.jar"
PROPS="$SCRIPT_DIR/extracto_oms.properties"

# ----- Fecha: sin argumento = ayer; con argumento YYYYMMDD = ese día -----
if [ "${1:-}" = "" ]; then
  DIA="yesterday"
else
  arg="$1"
  if ! printf '%s' "$arg" | grep -qE '^[0-9]{8}$'; then
    echo "Argumento inválido: '$arg'. Se espera YYYYMMDD (ej. 20260921)." >&2
    exit 2
  fi
  DIA="${arg:0:4}-${arg:4:2}-${arg:6:2}"
fi

SELLO=$(date -d "$DIA" +%Y%m%d)
FECHA=$(date -d "$DIA" +%F)
INICIO="$FECHA 00:00:00"
FIN="$FECHA 23:59:59"

echo "[extracto-oms] dia=$FECHA sello=$SELLO ventana=[$INICIO .. $FIN]"

# ----- DRY_RUN: solo el plan, sin Oracle -----
if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "[extracto-oms] DRY_RUN: generaría en $SCRIPT_DIR:"
  for f in "liverpool/lp_sl_$SELLO.csv" "liverpool/lp_bt_$SELLO.csv" \
           "suburbia/sbb_sl_$SELLO.csv" "suburbia/sbb_bt_$SELLO.csv"; do
    echo "  $f"
  done
  exit 0
fi

# ----- Dependencias -----
[ -f "$JAR" ]   || { echo "No se encontró el jar: $JAR" >&2; exit 3; }
[ -f "$PROPS" ] || { echo "No se encontró $PROPS (copiar de extracto_oms.properties.example)." >&2; exit 3; }
command -v java >/dev/null 2>&1 || { echo "Falta 'java' (JRE >= 8) en el PATH." >&2; exit 3; }

# ----- Corre las consultas y escribe los CSV en las carpetas -----
java -cp "$JAR" Extractor "$PROPS" "$INICIO" "$FIN" "$SELLO" "$SCRIPT_DIR"

echo "[extracto-oms] listo."
