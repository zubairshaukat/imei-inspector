# IMEI Inspector

Free, self-hosted IMEI checker. Decodes any IMEI in the browser — no paid API, no data sent anywhere.

**Live:** https://imei.witsols.com

## What it does
**Check** — decode any IMEI.
**Authenticity** — cross-checks IMEI 1, IMEI 2, the box label, the model it's sold as, its model number, RAM and storage, then gives a verdict: *Likely genuine*, *Suspicious* or *Likely fake*. In the Android app it also reads the phone's own maker, model number, RAM, storage, screen and root/emulator signs.
**Scan** — read IMEI barcodes with the camera (box label, SIM tray, *#06# screen) or from a photo.
**Bulk** — check up to 500 IMEIs at once and copy the results as CSV.

## What a check shows
- Checksum validation (Luhn), with auto-completion of 14-digit IMEIs and IMEISV support
- TAC, serial number (SNR), check digit and issuing body
- Brand, model, model number, market/region and release year from a 275,000-model TAC database
- Manufacture date for older Apple devices from the 12-character serial number
- Links to official warranty checkers (Apple, Huawei, others via search)
- Bulk mode: paste up to 500 IMEIs, copy results as CSV

Manufacture date and warranty are not stored inside an IMEI; they come from the device serial and the manufacturer.

## Install on Android
**App (APK):** [Download the latest APK](https://github.com/zubairshaukat/imei-inspector/releases/latest/download/imei-inspector.apk). Open it on your phone and tap Install. Every push to `main` builds a new APK and publishes it under **Releases**; each one installs over the previous version. The APK bundles the full database, so it works offline.

**From the website:** open the site in Chrome on Android, then menu > *Add to Home screen* / *Install app*.

## Structure
```
html/index.html        the app (single page, no build step)
html/tac/*.json        TAC database, sharded by first 3 digits
nginx.conf             gzip + caching for the database files
docker-compose.yml     nginx:alpine container on the ai-agents_ai-net network
android/               WebView app that bundles html/ (built by .github/workflows/android-apk.yml)
```

## Deploy
```bash
git clone <this repo> /opt/imei-inspector
cd /opt/imei-inspector && docker compose up -d
```
Then add a proxy host in Nginx Proxy Manager forwarding to `imei-inspector:80`.

## Database updates (automatic)
`.github/workflows/update-database.yml` runs daily: it downloads the latest TAC list, rebuilds `html/tac/`, commits only when something changed, and then builds a new APK.

The Android app reads the newest database from `https://imei.witsols.com/tac/` whenever the phone is online, and falls back to its built-in copy offline — so new models appear without reinstalling.

On the server, pull the changes every night (crontab -e):
```
30 3 * * * cd /opt/imei-inspector && git pull -q && docker exec imei-inspector nginx -s reload
```

To rebuild the database by hand: `python3 scripts/build_tac.py tac_full.csv html/tac`.

## Credits
TAC data from [MoazEb/tac-database](https://github.com/MoazEb/tac-database) (MIT licence), see `html/tac/LICENSE-tac-database.txt`. Barcode decoding by [ZXing](https://github.com/zxing-js/library) (Apache 2.0), see `html/vendor/LICENSE-zxing.txt`.
