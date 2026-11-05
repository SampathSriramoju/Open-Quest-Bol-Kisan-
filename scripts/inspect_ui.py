import xml.etree.ElementTree as ET
import sys

sys.stdout.reconfigure(encoding='utf-8')
tree = ET.parse('ui_test.xml')
for node in tree.iter('node'):
    text = node.attrib.get('text', '')
    desc = node.attrib.get('content-desc', '')
    if text:
        print(f'TEXT: {text[:100]}')
    elif desc:
        print(f'DESC: {desc[:100]}')
