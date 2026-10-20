from pathlib import Path
import argparse
import os
import shutil
import signal
import subprocess
import time
import urllib.request

root = Path(__file__).resolve().parents[1]
frontend = root / "aivora-frontend"
parser = argparse.ArgumentParser(description="Restart the local Aivora frontend")
parser.add_argument("--mode", choices=["preview", "dev"], default="preview")
args = parser.parse_args()
if args.mode == "preview" and not (frontend / "dist/index.html").exists():
    raise SystemExit("Build the frontend first: npm --prefix aivora-frontend run build")
node = shutil.which("node")
if not node:
    raise SystemExit("Node.js is required")
raw = subprocess.run(["lsof", "-tiTCP:5173", "-sTCP:LISTEN"], capture_output=True, text=True).stdout
for value in set(raw.split()):
    pid = int(value)
    command = subprocess.run(["ps", "-p", str(pid), "-o", "args="], capture_output=True, text=True).stdout
    cwd = subprocess.run(["lsof", "-a", "-p", str(pid), "-d", "cwd", "-Fn"], capture_output=True, text=True).stdout
    if "vite" not in command or "n" + str(frontend) not in cwd.splitlines():
        raise SystemExit("Port 5173 belongs to another application; not stopped")
    os.kill(pid, signal.SIGTERM)
    for _ in range(40):
        if not subprocess.run(["lsof", "-tiTCP:5173", "-sTCP:LISTEN"], capture_output=True, text=True).stdout.strip():
            break
        time.sleep(0.15)
command = [node, str(frontend / "node_modules/vite/bin/vite.js")]
if args.mode == "preview":
    command.append("preview")
command.extend(["--host", "127.0.0.1", "--port", "5173", "--strictPort"])
with open("/tmp/aivora-frontend-current.log", "ab") as log:
    process = subprocess.Popen(command, cwd=frontend, stdout=log, stderr=log, start_new_session=True)
for _ in range(30):
    if process.poll() is not None:
        raise SystemExit("Frontend exited; inspect /tmp/aivora-frontend-current.log")
    try:
        with urllib.request.urlopen("http://127.0.0.1:5173/", timeout=1) as response:
            if response.status == 200:
                print("Aivora frontend ready:", args.mode, "PID", process.pid, "http://127.0.0.1:5173/")
                break
    except Exception:
        time.sleep(0.25)
else:
    raise SystemExit("Frontend did not become ready")
