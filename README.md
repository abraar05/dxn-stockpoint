# DXN Stockpoint 🛒

A zero-cost, offline-first POS and order management system for DXN stockpoints.

## Stack
- **Android App** — Kotlin + Jetpack Compose (glassmorphic UI)
- **Backend** — Node.js + Express + SQLite (runs on local PC or free cloud)
- **Orders** — WhatsApp deep-link checkout (free, no API key)
- **Tunnel** — Cloudflare Tunnel / Ngrok (free HTTPS)

## Quick Start

### Backend
```bash
cd backend
npm install
npm run seed    # Seeds 12 DXN products
npm start       # http://localhost:3000
```

### Android App
Open `android/DXNStockpoint/` in **Android Studio**, sync Gradle, run on emulator or device.

> The emulator connects to `http://10.0.2.2:3000/` automatically.  
> For a real device, set your Cloudflare/Ngrok tunnel URL in **Settings ⚙️**.

## APK Download
After pushing to `main`, GitHub Actions builds the APK automatically.  
Go to **Actions → Build & Release APK → Artifacts** to download it.

## Features
| Screen | Features |
|--------|----------|
| Catalog | Search, category filters, stock badges, glassmorphic cards |
| Cart | Qty stepper, member/retail pricing toggle, WhatsApp + API checkout |
| Admin | Dashboard stats, low-stock alerts, stock update, add product, orders |
| Settings | Server URL, WhatsApp number |

## API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/products` | List all products |
| POST | `/api/v1/products` | Add product |
| PUT | `/api/v1/products/:id` | Edit product |
| POST | `/api/v1/stock/update` | Update stock |
| GET | `/api/v1/orders` | Order history |
| POST | `/api/v1/orders` | Place order |
| GET | `/api/v1/dashboard` | Stats + low-stock |
