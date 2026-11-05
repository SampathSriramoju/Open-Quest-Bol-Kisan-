import sqlite3
import sys

# Ensure UTF-8 output
sys.stdout.reconfigure(encoding='utf-8')

con = sqlite3.connect('temp_device_bolkisan.db')
cur = con.cursor()
cols = cur.execute("PRAGMA table_info(interactions);").fetchall()
col_names = [c[1] for c in cols]
print("Interactions schema:", col_names)
rows = cur.execute("SELECT id, detectedLanguage, rawTranscript, responseScenario, citationDoc, citationPage, isChemicalFlagged FROM interactions ORDER BY timestamp ASC;").fetchall()
print(f"Total interactions recorded: {len(rows)}")
for i, r in enumerate(rows, 1):
    print(f"\n--- INTERACTION {i} ---")
    print(f"ID: {r[0]}")
    print(f"Lang: {r[1]}")
    print(f"Transcript: {r[2]}")
    print(f"Scenario: {r[3]}")
    print(f"Citation: {r[4]} (Page {r[5]})")
    print(f"Chemical Flagged: {bool(r[6])}")

con.close()
