import socket
import time

s = socket.socket()
try:
    s.connect(('127.0.0.1', 4444))
    print("SUCCESS: Connected to root serial shell!")
    s.send(b"id\n")
    time.sleep(0.5)
    print("RESPONSE:\n" + s.recv(1024).decode('utf-8', errors='replace'))
    s.close()
except Exception as ex:
    print(f"FAILED: {ex}")
