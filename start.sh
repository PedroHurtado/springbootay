#!/usr/bin/env bash
# ============================================================================
#  Arranca el microservicio (demo) y la Spring Cloud Gateway a la vez.
#  Pensado para Git Bash en Windows. Muestra los dos logs en vivo y,
#  con Ctrl+C, para ambos servicios de forma limpia.
#
#  Uso:   ./start.sh
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/.logs"
mkdir -p "$LOG_DIR"

PIDS=()

cleanup() {
  echo
  echo "==> Parando servicios..."
  kill "${PIDS[@]}" 2>/dev/null || true
  # Por si Maven deja un java escuchando, lo cerramos por puerto (Windows).
  for port in 8080 8081; do
    pid="$(netstat -ano 2>/dev/null | grep -E "[:.]${port}[[:space:]].*LISTENING" | awk '{print $NF}' | head -n1 || true)"
    if [ -n "${pid:-}" ]; then taskkill //F //PID "$pid" >/dev/null 2>&1 || true; fi
  done
  echo "==> Hecho."
}
trap cleanup INT TERM

echo "==> Arrancando MICROSERVICIO (demo) en http://localhost:8080 ..."
( cd "$ROOT/demo" && ./mvnw -q spring-boot:run ) >"$LOG_DIR/demo.log" 2>&1 &
PIDS+=("$!")

# Pequena espera para que el micro empiece a levantar antes que la gateway.
sleep 3

echo "==> Arrancando GATEWAY en http://localhost:8081 ..."
( cd "$ROOT/gateway" && ./mvnw -q spring-boot:run ) >"$LOG_DIR/gateway.log" 2>&1 &
PIDS+=("$!")

cat <<EOF

------------------------------------------------------------
 Arrancando. Logs en vivo abajo (Ctrl+C para parar ambos).
 Ficheros: $LOG_DIR/demo.log  y  $LOG_DIR/gateway.log

 Cuando ambos pongan 'Started', prueba:
   Acceso DIRECTO al micro : http://localhost:8080/api/ingredients
   A traves del GATEWAY    : http://localhost:8081/api/ingredients
------------------------------------------------------------

EOF

# Muestra ambos logs en vivo hasta que pulses Ctrl+C.
tail -n +1 -f "$LOG_DIR/demo.log" "$LOG_DIR/gateway.log"
