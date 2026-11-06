import os

img_path = r'C:\Users\Sampath Sriramoju\.android\avd\Medium_Phone.avd\userdata-qemu.img'
if os.path.exists(img_path):
    with open(img_path, 'rb') as f:
        header = f.read(1024)
    print("Header bytes 0..16:", header[:16])
    # Check ext4 superblock magic 0xEF53 at offset 1080 (0x438)
    with open(img_path, 'rb') as f:
        f.seek(1080)
        magic = f.read(2)
        print("Magic at 1080:", magic.hex())
else:
    print("Not found:", img_path)
