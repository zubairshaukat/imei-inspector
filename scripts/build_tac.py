"""Rebuild html/tac/ from the upstream TAC CSV.

Usage: python3 scripts/build_tac.py tac_full.csv html/tac [source-commit]
Shards by the first 3 TAC digits and writes meta.json with the count and date.
"""
import csv, json, os, sys, datetime, collections

src, out = sys.argv[1], sys.argv[2]
source_commit = sys.argv[3] if len(sys.argv) > 3 else ""
shards = collections.defaultdict(dict)
with open(src, encoding="utf-8", errors="replace") as f:
    for r in csv.DictReader(f):
        tac = (r.get("TAC") or "").strip()
        if not tac.isdigit():
            continue
        tac = tac.zfill(8)
        if len(tac) != 8:
            continue
        rec = [(r.get("Brand") or "").strip(), (r.get("SPECS") or "").strip()]
        sh = shards[tac[:3]]
        if rec not in sh.setdefault(tac, []):
            sh[tac].append(rec)

if len(shards) < 50 or sum(len(v) for v in shards.values()) < 100000:
    sys.exit("Upstream file looks incomplete; keeping the current database.")

os.makedirs(out, exist_ok=True)
for name in os.listdir(out):
    if name.endswith(".json") and name != "meta.json":
        os.remove(os.path.join(out, name))
total = 0
for k in sorted(shards):
    total += len(shards[k])
    with open(os.path.join(out, f"{k}.json"), "w", encoding="utf-8") as g:
        json.dump(dict(sorted(shards[k].items())), g, separators=(",", ":"), ensure_ascii=False)

meta_path = os.path.join(out, "meta.json")
old = {}
if os.path.exists(meta_path):
    with open(meta_path) as m:
        old = json.load(m)
meta = {"count": total, "shards": sorted(shards), "source_commit": source_commit}
# Only bump the date when the data actually changed, so unchanged runs produce no commit.
same = old.get("count") == total and old.get("source_commit") == source_commit
meta["updated"] = old.get("updated") if same and old.get("updated") else datetime.date.today().isoformat()
with open(meta_path, "w") as m:
    json.dump(meta, m, indent=1)
print(f"{total} TACs in {len(shards)} shards")
