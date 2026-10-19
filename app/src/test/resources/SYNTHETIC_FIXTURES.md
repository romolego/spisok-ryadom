# Synthetic backup fixtures

`synthetic-backup-before-080.zip` and `synthetic-backup-after-080.zip` both contain **entirely invented data**.
They contain no user export, real addresses, personal names, URLs, photos,
device preferences, original product identities, or original timestamps.

Regenerate them with Python 3 and the standard library:

```sh
python app/src/test/resources/generate_synthetic_080.py
```

The generator takes no input files. It builds a deterministic ZIP8 graph from
scratch: 120 numbered test products, 21 purchases, 14 fictional shops, 134
product/shop links, 9 points and 12 availability rows. The paired snapshots
model an intended dictionary update: 12 to 21 global department types, 60 to
47 local placements, and 9 to 12 units. Thirteen unused broadcast placements
are removed without changing products, purchases, links, shops or points.
Shop 8 retains its legitimate frozen-food department; shops 11 and 12 end
with no local departments. Standard department labels and units are generic
vocabulary used by the regression assertions.

`Refine080DataTest` verifies full import, foreign keys, exact preservation of
unchanged sections, intended dictionary differences, and round-trip export.
The original real-data verification was performed locally before publication;
those private inputs are deliberately absent from Git and release assets.
