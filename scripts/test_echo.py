import socket
import time

s = socket.socket()
s.connect(('127.0.0.1', 4444))
time.sleep(0.5)
s.send(b"\n\necho HELLO_FROM_ROOT\n\n")
time.sleep(1.0)
data = s.recv(4096).decode('utf-8', errors='replace')
print("RECEIVED:\n", data)
s.close()
