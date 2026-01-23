# Contributing to Modtale

First off, thank you for considering contributing to Modtale! We are a community-driven platform building the future of Hytale content sharing. Whether you're fixing a bug, improving documentation, or proposing a new feature, your help is appreciated.

## 1. Getting Started

### Prerequisites

* **Node.js:** v20+
* **Java JDK:** 21 (Amazon Corretto or OpenJDK)
* **MongoDB:** Local instance or Atlas
* **Warden (Mock Mode):** Since our security scanner is closed-source, external contributors must set `WARDEN_ENABLED=false` in their backend configuration to run the API locally.

### Installation

Please refer to the "Local Development" section in the [README.md](README.md) for detailed setup instructions.

## 2. Git Workflow & Branching

We use a feature-branch workflow rooted in `develop`.

### For External Contributors

1. **Fork the Repo:** You cannot create branches directly on the main Modtale repository. Please fork the project to your own account.
2. **Branch off `develop`:** Create your feature branch from `develop` in your fork.
```bash
git checkout develop
git pull origin develop
git checkout -b feat/my-cool-feature

```


3. **Submit PR:** Open a Pull Request from your fork's branch to Modtale's `develop` branch.

## 3. Coding Standards

### Backend (Java/Spring Boot)

* **Style:** We follow **Google Java Format**.
* **Linting:** Please ensure your code is formatted before submitting. Most IDEs (IntelliJ, VS Code) have plugins for Google Java Format.
* **Architecture:** Follow the existing patterns:
* Controllers handle web concerns.
* Services handle business logic.
* Repositories handle data access.
* **Do not leak implementation details** (like specific Mongo queries) into Controllers.



### Frontend (React/Astro)

* **Style:** While we don't strictly enforce a linter config yet, please try to match the existing code style.
* **Components:** Prefer functional components with hooks.
* **State:** Use React Context for global state only when necessary.

## 5. Reporting Issues

If you find a bug or have a feature request, please open an issue.

* **Bugs:** Include steps to reproduce, expected behavior, and actual behavior. Screenshots are helpful!
* **Features:** Describe the problem you are solving and your proposed solution.

## 6. Licensing & Legal

By contributing to Modtale, you agree that:

1. **License:** Your code will be licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.
2. **Ownership:** You grant Modtale (the project maintainers) ownership and the necessary licensing rights over the code contributed to the repository. This allows us to defend the project legally and ensure it remains open and sustainable.

---

**Questions?**
Join our [Discord Server](https://discord.gg/PcFaDVYqVe) to chat with the dev team and other contributors.
