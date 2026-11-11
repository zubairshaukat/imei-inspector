# IMEI Inspector

Free, self-hosted IMEI checker. Decodes any IMEI in the browser — no paid API, no data sent anywhere.

**Live:** https://imei.witsols.com

## What it shows
- Checksum validation (Luhn), with auto-completion of 14-digit IMEIs and IMEISV support
- TAC, serial number (SNR), check digit and issuing body
- Brand, model, model number, market/region and release year from a 275,000-model TAC database
- Manufacture date for older Apple devices from the 12-character serial number
- Links to official warranty checkers (Apple, Huawei, others via search)
- Bulk mode: paste up to 500 IMEIs, copy results as CSV

Manufacture date and warranty are not stored inside an IMEI; they come from the device serial and the manufacturer.

## Install on Android
**App (APK):** every push to `main` builds an APK with GitHub Actions. Open the repo's **Actions** tab, pick the latest *Build Android APK* run, and download `imei-inspector-apk`. Releases also get the APK attached. The APK bundles the full database, so it works offline.

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

## Updating the database
Replace the files in `html/tac/`. Changes are live immediately; no restart needed.

## Credits
TAC data from [MoazEb/tac-database](https://github.com/MoazEb/tac-database) (MIT licence, October 2026), see `html/tac/LICENSE-tac-database.txt`.
