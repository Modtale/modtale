<div align="center">
<a href="https://modtale.net">
<img src="logo.svg" alt="Modtale Logo" width="850" height="132">
</a>

<p align="center">
<strong>The Hytale Community Repository</strong>
</p>

<p align="center">
    <a href="https://www.gnu.org/licenses/agpl-3.0"><img src="https://img.shields.io/badge/License-AGPL_v3-blue.svg?style=flat-square&logo=gnu" alt="License: AGPL v3"></a>
    <a href="https://astro.build"><img src="https://img.shields.io/badge/Astro-4.0-orange?style=flat-square&logo=astro" alt="Astro"></a>
    <a href="https://react.dev"><img src="https://img.shields.io/badge/React-19.0-blue?style=flat-square&logo=react" alt="React"></a>
    <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-3.3-green?style=flat-square&logo=springboot" alt="Spring Boot"></a>
    <a href="https://www.java.com"><img src="https://img.shields.io/badge/Java-21-red?style=flat-square&logo=openjdk" alt="Java 21"></a>
    <a href="https://www.mongodb.com"><img src="https://img.shields.io/badge/Database-MongoDB-forestgreen?style=flat-square&logo=mongodb" alt="MongoDB"></a>
    <a href="https://github.com/Modtale/modtale"><img src="https://img.shields.io/endpoint?url=https%3A%2F%2Fghloc.vercel.app%2Fapi%2FModtale%2Fmodtale%2Fbadge&style=flat-square&logo=git" alt="Lines of Code"></a>
</p>
<br />
</div>

## Welcome to the Modtale Monorepo!

```text
modtale/
├── backend/                       # ☕ Spring Boot API
│   ├── src/main/java/             # Core Java Application
│   │   ├── config/                # Security, CORS, and WebMvc configs
│   │   ├── controllers/           # REST endpoints mapping
│   │   ├── models/                # MongoDB document schemas
│   │   ├── repositories/          # Database interaction layer
│   │   └── services/              # Core business logic (Uploads, Auth, etc.)
│   ├── Dockerfile.status          # Detached status page/checker image
│   └── build.gradle               # Dependencies & build definitions
│
├── frontend/                      # Astro + React Web Application
│   ├── src/
│   │   ├── components/            # Reusable, stateless UI components (Buttons, Modals)
│   │   ├── modules/               # Domain-Driven Design (Auth, Project, User domains)
│   │   ├── pages/                 # Astro SSR entry points (e.g., /[...all].astro)
│   │   ├── styles/                # Tailwind global CSS & theme constants
│   │   └── utils/                 # API clients, Helpers
│   ├── astro.config.mjs           # Astro build & integration settings
│   └── package.json               # Node dependencies
│
└── Warden/                        # Security Scanner Service (Closed Source)

```

---

## Tech Stack

| Domain | Technology | Usage |
| --- | --- | --- |
| **Frontend** | **Astro** | Framework & Server-Side Rendering (SSR) |
|  | **React** | Interactive UI Components & SPA Routing (`react-router-dom`) |
|  | **Tailwind CSS** | Utility-first, responsive, and dark-mode compatible styling |
|  | **Lucide React** | Consistent, lightweight SVG iconography |
| **Backend** | **Java 21** | Modern, high-performance server language |
|  | **Spring Boot** | Enterprise-grade REST API Framework |
|  | **MongoDB** | Primary NoSQL document data store |
|  | **Bucket4j / Caffeine** | Token-bucket rate limiting and high-speed in-memory caching |
| **Infrastructure** | **Cloudflare R2** | Zero-egress, S3-compatible Object Storage for mod files & images |

### Detached Status Service

The public status page is served by a separate Spring Boot entry point: `net.modtale.status.StatusServiceApplication`.
It owns the status HTML, `/api/v1/status`, and `/api/v1/status/live`, and it does not depend on the main Astro server or the main backend process to render.

Build and run it locally from `backend/`:

```bash
./gradlew statusServiceJar
PORT=18080 \
STATUS_TARGET_SITE_URL=http://localhost:5173 \
STATUS_TARGET_API_URL=http://localhost:8080/actuator/health/readiness \
java -jar build/libs/modtale-backend-0.0.1-SNAPSHOT-status.jar
```

The status image is built with `backend/Dockerfile.status` and `backend/cloudbuild-status.yml` as `modtale-status`. Deploy it to a separate Cloud Run service and map the status domain or route to that service. The main frontend `/status` path redirects to `PUBLIC_STATUS_URL` (`https://status.modtale.net` by default).

---

## Local Development Setup

Ready to contribute? Follow these steps to get Modtale running on your local machine.

### Prerequisites

* **Node.js:** v20 or higher.
* **Java JDK:** Version 21 (Amazon Corretto, Eclipse Temurin, or standard OpenJDK).
* **MongoDB:** A local instance running on port `27017`, or a valid MongoDB Atlas connection string.

### 1. Clone the Repository

```bash
git clone https://github.com/Modtale/modtale.git
cd modtale

```

### 2. Backend Configuration

The Spring Boot backend relies on environment variables. You can set these in your IDE's Run Configuration or export them directly in your terminal.

| Variable | Description | Example |
| --- | --- | --- |
| `MONGODB_URI` | Connection String | `mongodb://localhost:27017/modtale` |
| `R2_ACCESS_KEY` | Storage Access Key | `your_dev_access_key` |
| `R2_SECRET_KEY` | Storage Secret Key | `your_dev_secret_key` |
| `R2_ENDPOINT` | Storage Endpoint URL | `https://<accountid>.r2.cloudflarestorage.com` |
| `WARDEN_ENABLED` | **Must be false locally** | `false` |
| `STATUS_DISCORD_WEBHOOK_URL` | Optional Discord webhook for status-change alerts | `https://discord.com/api/webhooks/...` |
| `STATUS_CHECKER_ENABLED` | Opt into the legacy embedded backend checker | `false` |

Detached status service variables:

| Variable | Description | Default |
| --- | --- | --- |
| `PUBLIC_STATUS_URL` | Frontend redirect target for `/status` | `https://status.modtale.net` |
| `STATUS_TARGET_SITE_URL` | Main site URL checked by the detached service | `https://modtale.net` |
| `STATUS_TARGET_API_URL` | API health URL checked by the detached service | `https://api.modtale.net/actuator/health/readiness` |
| `STATUS_MONGODB_URI` | Optional status-service Mongo URI; falls back to `MONGODB_URI` | empty |
| `STATUS_R2_BUCKET_NAME` / `STATUS_R2_ACCESS_KEY` / `STATUS_R2_SECRET_KEY` / `STATUS_R2_ENDPOINT` | Optional status-service R2 credentials; each falls back to the main R2 variable | empty |
| `STATUS_SNAPSHOT_PATH` | Local fallback history cache file | `/tmp/modtale-status-snapshot.json` |
| `STATUS_REQUEST_TIMEOUT` | Probe timeout for HTTP, Mongo, and R2 checks | `5s` |
| `STATUS_REFRESH_INTERVAL_MS` | Probe interval | `60000` |
| `STATUS_CORS_ALLOWED_ORIGINS` | Allowed origins for status API reads | `*` |

> **Note on Warden:** The "Warden" malware and security scanner is proprietary to protect our threat-detection logic. You **must** set `WARDEN_ENABLED=false` to run the backend locally. This enables a "Mock Mode" where file uploads bypass the scanner and automatically return a mock "CLEAN" status.

**(Optional) OAuth Variables:**
To test social logins (GitHub, Discord, Google), provide their respective Client IDs and Secrets (e.g., `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`).

### 3. Run the Backend

Open a terminal in the `backend/` directory and use the Gradle wrapper.

```bash
cd backend
# Linux/Mac
./gradlew bootRun

# Windows
gradlew.bat bootRun

```

*The API will start and listen on `http://localhost:8080`.*

### 4. Frontend Configuration & Execution

Create a `.env` file inside the `frontend/` directory to point the React client to your local API.

**File:** `frontend/.env`

```ini
PUBLIC_API_URL=http://localhost:8080/api/v1

```

Next, open a separate terminal, install the Node dependencies, and start the Astro development server.

```bash
cd frontend
npm install
npm run dev

```

*The web client is now accessible at `http://localhost:5173`!*

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.

Modtale is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This ensures that the platform remains open and accessible to the Hytale community forever.

## Community & Support

* **Docs:** [modtale.net/api-docs](https://modtale.net/api-docs)
* **Discord:** [Join the Server](https://discord.gg/PcFaDVYqVe)
* **X (Twitter):** [@modtalenet](https://x.com/modtalenet)
* **Bluesky:** [@modtale.net](https://bsky.app/profile/modtale.net)

### Contributing

We welcome contributions from the community! Whether it's a bug fix, a new feature, or documentation improvements, please refer to our [CONTRIBUTING.md]() for coding guidelines and pull request instructions.

---

## Star History

<div align="center">
  <a href="https://www.star-history.com/#Modtale/modtale&Date">
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=Modtale/modtale&type=Date" />
  </a>
</div>

<div align="center">
<p>© 2026 Modtale. The Hytale Community Repository.</p>
</div>
