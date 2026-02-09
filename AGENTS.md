# Instructions for Agents

## Build Artifacts & Dynamic Files
* **Do not commit changes** to files in `config-lib/src/main/assets/tslFiles/` (e.g., `EE.xml`, `eu-lotl.xml`). These files are updated dynamically by build scripts (like `TSLXMLDownloader.py`). If you run these scripts locally for testing, **revert the changes** to these files before submitting.

## Romanian eID Support
* Support for Romanian eID (CEI) has been added. It utilizes a specific discovery flow:
    1.  **PACE Authentication**: Authenticate using CAN (6 digits) or PIN (4 digits) in the Master File (MF) context.
    2.  **Secure Applet Selection**: Select the ICAO Applet (`00 A4 04 00 07 A0 00 00 02 47 10 01`) using a **Wrapped APDU** via the established secure channel.
    3.  **Manual Secure Reading**: Read Data Groups (DG1, DG2) using `readDataGroupSecure` which sends wrapped `READ BINARY` commands with implicit SFI, bypassing standard `SELECT` commands that are rejected by the card.
* **Debugging**: To debug Romanian cards, use the `RomanianCardService` wrapper which logs APDU traffic.
