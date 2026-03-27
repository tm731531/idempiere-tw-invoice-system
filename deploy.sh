#!/usr/bin/env bash
# deploy.sh — Build and hot-deploy tw.idempiere.invoice.tax into running iDempiere
# Usage: ./deploy.sh

set -e

PLUGIN_DIR="/opt/idempiere-server/x86_64/plugins"
OSGI_HOST="127.0.0.1"
OSGI_PORT="12612"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── 1. Build ──────────────────────────────────────────────────────────────────
echo ">>> Building JAR..."
cd "$PROJECT_DIR"
mvn clean package -q
JAR=$(ls target/tw.idempiere.invoice.tax-*.jar | sort | tail -1)
echo "    Built: $JAR"

# ── 2. Copy to plugins dir ────────────────────────────────────────────────────
echo ">>> Copying to $PLUGIN_DIR ..."
# Remove any old tw.idempiere.invoice.tax jars first
rm -f "$PLUGIN_DIR"/tw.idempiere.invoice.tax_*.jar "$PLUGIN_DIR"/tw.idempiere.invoice.tax-*.jar 2>/dev/null || true
BASENAME=$(basename "$JAR" | sed 's/-/_/2')   # tw.idempiere.invoice.tax_1.0.0.xxx.jar
cp "$JAR" "$PLUGIN_DIR/$BASENAME"
DEST="$PLUGIN_DIR/$BASENAME"
echo "    Installed: $DEST"

# ── 3. Talk to OSGi console via Python telnet ─────────────────────────────────
echo ">>> Querying OSGi console..."

python3 - "$DEST" "$OSGI_HOST" "$OSGI_PORT" <<'PYEOF'
import socket, time, sys, re

dest = sys.argv[1]
host = sys.argv[2]
port = int(sys.argv[3])

def osgi_session(host, port, commands, wait=2.5):
    s = socket.socket()
    s.connect((host, port))
    s.settimeout(2)
    # Read initial telnet negotiation
    init = b''
    try:
        while True:
            chunk = s.recv(4096)
            if not chunk: break
            init += chunk
    except: pass
    # Reply: DO ECHO, DO SGA, WILL NAWS (80x24), WILL TTYPE ANSI
    s.sendall(
        b'\xff\xfd\x01'
        b'\xff\xfd\x03'
        b'\xff\xfb\x1f'
        b'\xff\xfa\x1f\x00\x80\x00\x18\xff\xf0'
        b'\xff\xfb\x18'
        b'\xff\xfa\x18\x00ANSI\xff\xf0'
    )
    time.sleep(0.5)
    # Drain prompt
    extra = b''
    try:
        while True:
            extra += s.recv(4096)
    except: pass
    # Send each command
    results = []
    for cmd in commands:
        s.sendall((cmd + '\r\n').encode())
        time.sleep(wait)
        data = b''
        try:
            while True:
                chunk = s.recv(4096)
                if not chunk: break
                data += chunk
        except: pass
        # Strip IAC and control sequences
        clean = re.sub(rb'\xff[\xfa-\xff][\x00-\xff]*?\xff\xf0', b'', data, flags=re.DOTALL)
        clean = re.sub(rb'\xff[\xfb-\xfe].', b'', clean)
        clean = clean.replace(b'\r', b'\n').replace(b'\x00', b'')
        results.append(clean.decode('utf-8', errors='replace').strip())
    s.close()
    return results

# Step A: find our bundle ID
r = osgi_session(host, port, ['ss tw.idempiere.invoice.tax'])
print(r[0])

bundle_id = None
for line in r[0].splitlines():
    m = re.match(r'\s*(\d+)\s+(ACTIVE|INSTALLED|RESOLVED|STARTING|STOPPING)\s+tw\.idempiere\.invoice\.tax', line)
    if m:
        bundle_id = m.group(1)
        state = m.group(2)
        break

file_url = 'file://' + dest

if bundle_id:
    print(f"\n>>> Bundle found: id={bundle_id} state={state}")
    print(f">>> Updating bundle {bundle_id} from {file_url} ...")
    cmds = [
        f'update {bundle_id} {file_url}',
        f'start {bundle_id}',
        f'ss tw.idempiere.invoice.tax',
    ]
    results = osgi_session(host, port, cmds, wait=4)
    for i, (cmd, out) in enumerate(zip(cmds, results)):
        print(f"\n[{cmd}]\n{out}")
else:
    print(f"\n>>> Bundle not found — installing fresh from {file_url} ...")
    cmds = [
        f'install {file_url}',
        'ss tw.idempiere.invoice.tax',
    ]
    results = osgi_session(host, port, cmds, wait=4)
    for cmd, out in zip(cmds, results):
        print(f"\n[{cmd}]\n{out}")
    # find new id and start
    new_id = None
    for line in results[-1].splitlines():
        m = re.match(r'\s*(\d+)\s+\S+\s+tw\.idempiere\.invoice\.tax', line)
        if m:
            new_id = m.group(1)
            break
    if new_id:
        print(f"\n>>> Starting bundle {new_id}...")
        r2 = osgi_session(host, port, [f'start {new_id}', 'ss tw.idempiere.invoice.tax'], wait=4)
        for out in r2:
            print(out)

PYEOF

echo ""
echo ">>> Done."
