import subprocess
import time
import sys

proc = subprocess.Popen([r"D:\Snugle-Musix\gh_bin\bin\gh.exe", "auth", "login", "-h", "github.com", "-p", "https", "-w"],
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

# Keep reading output until logged in
start = time.time()
while time.time() - start < 120:
    line = proc.stdout.readline()
    if line:
        print(line, end="", flush=True)
        if "Logged in as" in line:
            print("\nAUTHENTICATION SUCCESSFUL!")
            break
    time.sleep(0.5)
