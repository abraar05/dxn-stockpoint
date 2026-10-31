# Privacy Policy for DXN Stockpoint POS

**Last Updated:** October 31, 2026

DXN Stockpoint POS ("we", "our", or "the app") is committed to protecting your privacy. This Privacy Policy explains our practices regarding user data, privacy, and device permissions.

---

### 1. Zero Personal Data Harvesting
DXN Stockpoint operates on a local-first architecture:
- We **do not sell, track, monetize, or harvest** your personal information, sales records, customer identities, or inventory volumes.
- All product inventory, pricing choices, and order histories are stored **locally on your device** (via SQLite Room database on Android, and `localStorage` on the Web POS).

---

### 2. Permissions & Usage
- **INTERNET & ACCESS_NETWORK_STATE**: Required exclusively to:
  1. Synchronize product and order data with your designated local or cloud backend server (if configured).
  2. Transmit pre-formatted order deep-links to WhatsApp.
- **WhatsApp Integration**: The app constructs standard WhatsApp Universal Links (`https://wa.me/` or `https://api.whatsapp.com/send`). Order information is only sent when you explicitly tap the "Confirm & Send via WhatsApp" button.

---

### 3. Contact & Support
For inquiries, source code, or customization support:
- GitHub: [https://github.com/abraar05/dxn-stockpoint](https://github.com/abraar05/dxn-stockpoint)
- Releases & Updates: [https://github.com/abraar05/dxn-stockpoint/releases](https://github.com/abraar05/dxn-stockpoint/releases)
