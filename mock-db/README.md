# Modtale Mock Database

This directory contains the helper script for importing the public mock MongoDB fixture into a local database.

The fixture JSON is not checked into the repository. Download the latest `modtale-mock-db-json` artifact from the [`Refresh Mock Database` GitHub Actions workflow](https://github.com/Modtale/modtale/actions/workflows/mock-db-refresh.yml), then extract the JSON files into:

```text
mock-db/collections/
```

After extraction, the directory should contain files such as:

```text
mock-db/collections/projects.json
mock-db/collections/users.json
mock-db/collections/comments.json
```

## Import Locally

Install MongoDB Database Tools so `mongoimport` is available, then run:

```bash
MONGODB_URI=mongodb://localhost:27017 \
MONGODB_DATABASE_NAME=modtale-mock \
bash mock-db/import.sh
```

Point the backend at the imported database:

```bash
export MONGODB_URI=mongodb://localhost:27017
export MONGODB_DATABASE_NAME=modtale-mock
export WARDEN_ENABLED=false
```

Start the backend from `backend/`:

```bash
./gradlew bootRun
```

## Mock Accounts

Mock sign-in accounts all use the password `password`:

```text
super_admin
admin
user
atlas_studio
pixelwright
northstar_collective
```

You can also sign in with seeded email addresses such as:

```text
user@example.test
```
