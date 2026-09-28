#!/usr/bin/env bash
#
# extracto_oms.sh — Extracto BC -> SFTP (solo depósito).
#
# Reproduce el módulo del backend SIN levantarlo: consulta Oracle, arma
#   oms_sl_<yyyyMMdd>.csv = remisiones Liverpool SoftLine + shipping groups Suburbia
#   oms_bt_<yyyyMMdd>.csv = órdenes de venta Big Ticket
# sobre el DÍA ANTERIOR completo y los sube por SFTP a /ecommerce_oms/.
#
# El SQL y el driver Oracle viven en extracto-oms.jar (junto a este script).
# La máquina destino necesita: java (JRE >= 8), sshpass y sftp (openssh-client). No requiere internet.
#
# Uso:
#   ./extracto_oms.sh              # procesa el día de ayer
#   ./extracto_oms.sh 20260921     # procesa un día específico (YYYYMMDD)
#   DRY_RUN=1 ./extracto_oms.sh    # muestra fecha/ventana/archivos y sale, sin tocar Oracle ni SFTP
#
set -euo pipefail

# ===================== Configuración (mismas conexiones del backend) =====================
JDBC_URL="jdbc:oracle:thin:@//172.17.212.224:1527/APPSPRO"
ORA_USER="USR_VENTA_ENTREGA"
ORA_PASS="Us5_v3nt4_3ntrEg4"

SFTP_HOST="172.17.203.61"
SFTP_PORT="22"
SFTP_USER="LOGVAD"
SFTP_PASS="sterling15"
SFTP_DIR="/ecommerce_oms/"
# =========================================================================================

# El jar va junto al script (se resuelve aunque se copie a otra máquina).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/extracto-oms.jar"

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
ARCHIVO_SL="oms_sl_${SELLO}.csv"
ARCHIVO_BT="oms_bt_${SELLO}.csv"

echo "[extracto-oms] dia=$FECHA sello=$SELLO ventana=[$INICIO .. $FIN]"

# ----- DRY_RUN: solo el plan, sin Oracle ni SFTP -----
if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "[extracto-oms] DRY_RUN: subiría $ARCHIVO_SL y $ARCHIVO_BT a ${SFTP_HOST}:${SFTP_DIR}"
  exit 0
fi

# ----- Dependencias -----
[ -f "$JAR" ] || { echo "No se encontró el jar: $JAR" >&2; exit 3; }
command -v java    >/dev/null 2>&1 || { echo "Falta 'java' (JRE >= 8) en el PATH." >&2; exit 3; }
command -v sshpass >/dev/null 2>&1 || { echo "Falta 'sshpass'. Instálalo (apt/yum install sshpass)." >&2; exit 3; }
command -v sftp    >/dev/null 2>&1 || { echo "Falta 'sftp' (paquete openssh-client)." >&2; exit 3; }

# ----- Directorio temporal + limpieza -----
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ----- Corre las consultas y arma los CSV en $TMP -----
java -cp "$JAR" Extractor \
  "$JDBC_URL" "$ORA_USER" "$ORA_PASS" "$INICIO" "$FIN" "$SELLO" "$TMP"

# ----- Sube por SFTP lo que se haya producido (misma regla del módulo) -----
subidos=0
for f in "$ARCHIVO_SL" "$ARCHIVO_BT"; do
  if [ -f "$TMP/$f" ]; then
    sshpass -p "$SFTP_PASS" sftp -oBatchMode=no -oStrictHostKeyChecking=no -P "$SFTP_PORT" \
      "$SFTP_USER@$SFTP_HOST" >/dev/null <<SFTP
cd $SFTP_DIR
put $TMP/$f $f
bye
SFTP
    echo "[extracto-oms] subido: $f"
    subidos=$((subidos + 1))
  else
    echo "[extracto-oms] $f no se generó (todas sus fuentes fallaron); no se sube"
  fi
done

echo "[extracto-oms] listo. Archivos subidos: $subidos"
