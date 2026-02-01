# Privacy Audit

This document outlines the network services contacted by the application and the information sent to them, based on a code review performed on the codebase.

### 1. Central Configuration Service
*   **URL:** `https://id.eesti.ee` (specifically `config.json`, `config.pub`, `config.rsa`)
*   **Purpose:** Downloads the latest application configuration, including URLs for other services and supported file types.
*   **Information Sent:** Minimal technical data (IP address, User-Agent).

### 2. Mobile-ID Service (Only if using Mobile-ID)
*   **URL:** `https://eid-dd.ria.ee/mid` (RIA Proxy) or `https://mid.sk.ee/mid-api`
*   **Purpose:** To authenticate the user or sign documents using Mobile-ID.
*   **Information Sent:**
    *   **Personal Code** (National Identity Number)
    *   **Phone Number**
    *   Relying Party Name and UUID
    *   Hash of the document being signed (not the document content itself)
    *   Display text (e.g., "Sign document")

### 3. Smart-ID Service (Only if using Smart-ID)
*   **URL:** `https://eid-dd.ria.ee/sid/v2` (RIA Proxy) or `https://rp-api.smart-id.com/v2`
*   **Purpose:** To authenticate the user or sign documents using Smart-ID.
*   **Information Sent:**
    *   **Personal Code** (embedded in a "semantics identifier")
    *   Relying Party Name and UUID
    *   Hash of the document being signed

### 4. Validation Service (SiVA) (When validating signatures)
*   **URL:** `https://siva.eesti.ee/V3/validate`
*   **Purpose:** To check if the digital signatures on a document are valid.
*   **Information Sent:** The **signed container (file)** or its hash is sent to this service for verification.

### 5. Time Stamping Authority (TSA) (When signing)
*   **URL:** `https://eid-dd.ria.ee/ts` (or other configured TSA)
*   **Purpose:** To add a trusted timestamp to the signature, proving when it was created.
*   **Information Sent:** A **hash** of the signature.

### 6. Trust Service List (TSL)
*   **URL:** `https://ec.europa.eu/tools/lotl/eu-lotl.xml`
*   **Purpose:** Downloads the official list of trusted certification authorities in Europe.
*   **Information Sent:** None (Download only).

### 7. LDAP Directory Service (When encrypting documents)
*   **URLs:** `ldaps://esteid.ldap.sk.ee` (Personal) or `ldaps://k3.ldap.sk.ee` (Corporate)
*   **Purpose:** To find the public encryption certificate of a recipient to encrypt a file for them.
*   **Information Sent:** The **Personal Code** or **Name** of the person being searched for.

### 8. OCSP Service (When using ID-card or validating)
*   **URL:** Varies (defined in the certificate, e.g., `http://aia.sk.ee/...`)
*   **Purpose:** To check if a certificate (e.g., the ID-card certificate) is valid or has been revoked.
*   **Information Sent:** The **Serial Number** of the certificate being checked.

### 9. CDOC2 Keyserver (Optional, for CDOC2 encryption)
*   **URL:** `https://cdoc2.id.ee`
*   **Purpose:** Key management for CDOC2 encrypted files.
*   **Information Sent:** Key capsules (encrypted keys). Note that by default, the configuration `CDOC2-USE-KEYSERVER` is set to `false`.
