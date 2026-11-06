import socket
import time

s = socket.socket()
s.connect(('127.0.0.1', 5554))
time.sleep(0.1)
s.recv(1024)

token = open(r'C:\Users\Sampath Sriramoju\.emulator_console_auth_token').read().strip()
s.send(f'auth {token}\n'.encode('utf-8'))
time.sleep(0.1)
s.recv(1024)

def key(code):
    s.send(f'event send EV_KEY:{code}:1 EV_KEY:{code}:0\n'.encode('utf-8'))
    time.sleep(0.1)
    s.recv(1024)

print("Waking up...")
key('KEY_WAKEUP')
time.sleep(0.3)
key('KEY_HOMEPAGE')
time.sleep(0.3)

# If USB debugging dialog is up:
# Press Tab to focus, Right to focus Allow, Enter to click
key('KEY_TAB')
time.sleep(0.1)
key('KEY_RIGHT')
time.sleep(0.1)
key('KEY_ENTER')
time.sleep(0.1)

s.close()
print("Done.")
