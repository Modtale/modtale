<div align="center">
<a href="[https://modtale.net](https://modtale.net)">
<img src="logo.svg" alt="Modtale Logo" width="850" height="132">
</a>

<p align="center">
<strong>The Hytale Community Repository</strong>
</p>

<p align="center">
<a href="[https://www.gnu.org/licenses/agpl-3.0](https://www.gnu.org/licenses/agpl-3.0)"><img src="[https://img.shields.io/badge/License-AGPL_v3-blue.svg?style=flat-square&logo=gnu](https://img.shields.io/badge/License-AGPL_v3-blue.svg?style=flat-square&logo=gnu)" alt="License: AGPL v3"></a>
<a href="[https://astro.build](https://astro.build)"><img src="[https://img.shields.io/badge/Astro-4.3-orange?style=flat-square&logo=astro](https://www.google.com/search?q=https://img.shields.io/badge/Astro-4.3-orange%3Fstyle%3Dflat-square%26logo%3Dastro)" alt="Astro"></a>
<a href="[https://react.dev](https://react.dev)"><img src="[https://img.shields.io/badge/React-19-blue?style=flat-square&logo=react](https://www.google.com/search?q=https://img.shields.io/badge/React-19-blue%3Fstyle%3Dflat-square%26logo%3Dreact)" alt="React"></a>
<a href="[https://spring.io/projects/spring-boot](https://spring.io/projects/spring-boot)"><img src="[https://img.shields.io/badge/Spring_Boot-3.3-green?style=flat-square&logo=springboot](https://img.shields.io/badge/Spring_Boot-3.3-green?style=flat-square&logo=springboot)" alt="Spring Boot"></a>
<a href="[https://www.java.com](https://www.java.com)"><img src="[https://img.shields.io/badge/Java-21-red?style=flat-square&logo=openjdk](https://img.shields.io/badge/Java-21-red?style=flat-square&logo=openjdk)" alt="Java 21"></a>
<a href="[https://www.mongodb.com](https://www.mongodb.com)"><img src="[https://img.shields.io/badge/Database-MongoDB-forestgreen?style=flat-square&logo=mongodb](https://img.shields.io/badge/Database-MongoDB-forestgreen?style=flat-square&logo=mongodb)" alt="MongoDB"></a>
</p>
</div>

<br />

## About

Modtale is the premier community repository for hosting and discovering Hytale community content. 

To balance performance with discoverability, Modtale utilizes a hybrid rendering strategy: standard traffic is served via Client-Side Rendering (CSR) for immediate interactivity using **React 19**, while search engine crawlers receive Server-Side Rendered (SSR) content via **Astro Node Adapter** for optimized SEO. Binary assets (mods, plugins, art) are distributed globally via **Cloudflare R2**.

### Supported Content Types

Modtale is engineered to support the specific file structures and metadata requirements of Hytale's modification system:

* **Modpacks:** Curated collections of plugins and asset packs with automated dependency resolution.
* **Server Plugins:** Server-side JAR files executed by the Hytale server.
* **Worlds:** Maps, lobbies, and schematics.
* **Art Assets:** Models, textures, and client-side visuals.
* **Data Assets:** Configs, loot tables, and other data-driven properties.

---

## Repository Structure

This monorepo houses the core components of the Modtale ecosystem:

```text
modtale/
├── backend/                # Spring Boot REST API
│   ├── src/main/java/      # Application source code
│   └── build.gradle        # Backend dependencies & build config
├── frontend/               # Astro + React Web Application
│   ├── src/                # Components, pages, and styles
│   ├── astro.config.mjs    # Astro configuration
│   └── package.json        # Frontend dependencies & scripts
└── Warden/                 # (Closed Source) Security Scanner Service

```

---

## Tech Stack

| Domain | Technology | Version | Usage |
| --- | --- | --- | --- |
| **Frontend** | **Astro** | v4.3.5 | Framework & Hybrid Routing (SSR/CSR) |
|  | **React** | v19.2.1 | UI Library & Interactive Islands |
|  | **Node.js** | v20+ | Runtime Environment |
|  | **Tailwind CSS** | v3.4 | Utility-first Styling System |
|  | **Lucide React** | Latest | Iconography |
| **Backend** | **Java** | JDK 21 | Server-side Language |
|  | **Spring Boot** | v3.3.4 | REST API Framework |
|  | **MongoDB** | Latest | Primary NoSQL Data Store |
|  | **Bucket4j** | v0.10.3 | Rate Limiting (Token Bucket Algorithm) |
|  | **Caffeine** | Latest | High-performance In-memory Caching |
| **Infra** | **Cloudflare R2** | N/A | S3-compatible Object Storage |
|  | **Docker** | Latest | Containerization & Deployment |

---

## Local Development

Follow these steps to set up the development environment on your local machine.

### Prerequisites

* **Node.js:** v20 or higher.
* **Java JDK:** Version 21 (Amazon Corretto or OpenJDK recommended).
* **MongoDB:** A local instance running on port `27017` or a valid Atlas connection string.

### 1. Clone the Repository

```bash
git clone https://github.com/Modtale/modtale.git
cd modtale

```

### 2. Backend Configuration

The backend relies on environment variables for configuration. You can set these in your IDE run configuration or export them in your terminal session.

**Required Variables:**
| Variable | Description | Example (Local) |
| :--- | :--- | :--- |
| `MONGODB_URI` | MongoDB Connection String | `mongodb://localhost:27017/modtale` |
| `R2_ACCESS_KEY` | Storage Access Key | `your_r2_access_key` |
| `R2_SECRET_KEY` | Storage Secret Key | `your_r2_secret_key` |
| `R2_ENDPOINT` | Storage Endpoint URL | `https://<accountid>.r2.cloudflarestorage.com` |
| `WARDEN_ENABLED` | **Set to `false` for local dev** | `false` |

> **Note on Warden:** The "Warden" security scanner is closed source and not included in this repo. You **must** set `WARDEN_ENABLED=false` to run the backend locally. This enables a "Mock Mode" where all file scans return a mock "CLEAN" result.

**Optional Variables (OAuth):**
*To test login features, you will need valid OAuth credentials. If you skip these, the app will start, but login will fail.*

* `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`
* `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET`
* `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`

### 3. Run the Backend

Navigate to the backend directory and run the application using Gradle.

```bash
cd backend
# Linux/Mac
./gradlew bootRun

# Windows
gradlew.bat bootRun

```

*The API will initialize at `http://localhost:8080`.*

### 4. Frontend Configuration

Create a `.env` file in the `frontend/` directory to configure the API connection.

**File:** `frontend/.env`

```ini
# Points to your local backend instance
PUBLIC_API_URL=http://localhost:8080/api/v1

```

### 5. Run the Frontend

Navigate to the frontend directory, install dependencies, and start the dev server.

```bash
cd frontend
npm install
npm run dev

```

*The web client will initialize at `http://localhost:5173`.*

---

## Production Build

### Backend (executable JAR)

```bash
cd backend
./gradlew clean build -x test
# Output: build/libs/modtale-backend-0.0.1-SNAPSHOT.jar

```

### Frontend (Node Adapter)

The frontend builds into a standalone Node.js server to support SSR.

```bash
cd frontend
npm run build
node dist/server/entry.mjs

```

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.

Modtale is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

## Community & Support

* **Docs:** [modtale.net/api-docs](https://modtale.net/api-docs)
* **Discord:** [Join the Server](https://discord.gg/PcFaDVYqVe)
* **X (Twitter):** [@modtalenet](https://x.com/modtalenet)
* **Bluesky:** [@modtale.net](https://bsky.app/profile/modtale.net)

---

### Contributing

Please refer to [CONTRIBUTING.md](https://www.google.com/search?q=CONTRIBUTING.md) for guidelines on submitting pull requests and reporting issues.

<div align="center">
<p>© 2026 Modtale. The Hytale Community Repository.</p>
</div>
