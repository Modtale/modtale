<div align="center">
  <a href="https://modtale.net">
    <img src="logo.svg" alt="Modtale Logo" width="850" height="132">
  </a>

  <p align="center">
    <strong>The Hytale Community Repository</strong>
    <br />
    The premier platform to discover, download, and share Hytale server plugins, modpacks, worlds, and art + data assets.
  </p>

  <p align="center">
    <a href="https://www.gnu.org/licenses/agpl-3.0"><img src="https://img.shields.io/badge/License-AGPL_v3-blue.svg?style=flat-square&logo=gnu" alt="License: AGPL v3"></a>
    <a href="https://astro.build"><img src="https://img.shields.io/badge/Astro-4.0-orange?style=flat-square&logo=astro" alt="Astro"></a>
    <a href="https://react.dev"><img src="https://img.shields.io/badge/React-19.0-blue?style=flat-square&logo=react" alt="React"></a>
    <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot-3.3-green?style=flat-square&logo=springboot" alt="Spring Boot"></a>
    <a href="https://www.java.com"><img src="https://img.shields.io/badge/Java-21-red?style=flat-square&logo=openjdk" alt="Java 21"></a>
    <a href="https://www.mongodb.com"><img src="https://img.shields.io/badge/Database-MongoDB-forestgreen?style=flat-square&logo=mongodb" alt="MongoDB"></a>
  </p>
</div>

<br />

## About

The platform employs a monorepo architecture leveraging a Spring Boot API and an Astro frontend. To balance performance with discoverability, Modtale utilizes a hybrid rendering strategy: standard traffic is served via Client-Side Rendering (CSR) for immediate interactivity, while search engine crawlers receive Server-Side Rendered (SSR) content for optimized SEO. Binary assets are distributed via Cloudflare R2.

## Supported Content Types

Modtale is engineered to support the specific file structures and metadata requirements of Hytale's modification system:

* **Modpacks:** Curated collections of plugins and asset packs.
* **Server Plugins:** Server side jar files.
* **Worlds:** Maps, lobbies, and schematics. (?)
* **Art Assets:** Models and textures.
* **Data Assets:** Configs, loot tables, and other data driven properties.

## Tech Stack

| Domain | Technology | Usage |
| :--- | :--- | :--- |
| **Frontend** | **Astro 4** | Framework & Routing |
| | **React 19** | Client-Side Rendering & Interactive Islands |
| | **Tailwind CSS** | Styling System |
| | **Lucide React** | Iconography |
| **Backend** | **Java 21** | Server-side Language |
| | **Spring Boot 3.3** | REST API Framework |
| | **MongoDB** | Primary NoSQL Data Store |
| | **Bucket4j** | Rate Limiting |
| | **Caffeine** | In-memory Caching |

## Resources

* **API Documentation:** [modtale.net/api-docs](https://modtale.net/api-docs)
* **Publishing Examples:** [github.com/Modtale/modtale-examples](https://github.com/Modtale/modtale-examples)

## Local Development

### Prerequisites

* Node.js v20+
* Java JDK 21
* MongoDB (Local instance or Atlas connection string)

### 1. Clone

```bash
git clone https://github.com/Modtale/modtale.git
cd modtale
```

### 2. Configuration

**Backend Variables:**
The backend reads from system environment variables. You must set these before running:

| Variable | Description |
| --- | --- |
| `MONGODB_URI` | Connection string (e.g., `mongodb://localhost:27017/modtale`) |
| `R2_ACCESS_KEY` | Cloudflare R2 / S3 Access Key |
| `R2_SECRET_KEY` | Cloudflare R2 / S3 Secret Key |
| `R2_ENDPOINT` | The R2/S3 endpoint URL |

**Frontend Variables:**
Create a `.env` file in the `frontend/` directory:

```ini
PUBLIC_API_URL=http://localhost:8080
```

### 3. Execution

**Backend:**

```bash
cd backend
# Ensure env vars are set (e.g., export MONGODB_URI=...)
./gradlew bootRun
```

*API will initialize on `http://localhost:8080*`

**Frontend:**

```bash
cd frontend
npm install
npm run dev
```

*Client will initialize on `http://localhost:5173*`

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.

Modtale is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

## Community & Support

* **Discord:** [Join the Server](https://discord.gg/PcFaDVYqVe)
* **X (Twitter):** [@modtalenet](https://x.com/modtalenet)
* **Bluesky:** [@modtale.net](https://bsky.app/profile/modtale.net)

---

<div align="center">
<p>© 2026 Modtale. The Hytale Community Repository.</p>
</div>