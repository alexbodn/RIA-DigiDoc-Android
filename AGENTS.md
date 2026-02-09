# Instructions for Agents

## Build Artifacts & Dynamic Files
* **Do not commit changes** to files in `config-lib/src/main/assets/tslFiles/` (e.g., `EE.xml`, `eu-lotl.xml`). These files are updated dynamically by build scripts (like `TSLXMLDownloader.py`). If you run these scripts locally for testing, **revert the changes** to these files before submitting.

## Romanian eID Support
* Support for Romanian eID (CEI) has been added. It utilizes a specific discovery flow:
    1.  Explicit ICAO Applet Selection (`00 A4 04 00 07 A0 00 00 02 47 10 01`) via Secure Messaging (if PACE is active) or Plaintext (if not).
    2.  PACE Authentication using Brainpool P256r1 curves.
    3.  Reading Data Groups using `PassportService` with implicit secure messaging.
* **Debugging**: To debug Romanian cards, use the `RomanianCardService` wrapper which logs APDU traffic.
