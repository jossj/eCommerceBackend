# Session Notes — 2026-05-05

## Branch
`claude/upgrade-springboot-3.5.14-2Hprg`

## Changes Made

### 1. Spring Boot Version Upgrade (`pom.xml`)
Upgraded `spring-boot-starter-parent` from `4.0.6` → `3.5.14`.

All source files already used `jakarta.*` imports (required for Spring Boot 3.x), so no code changes were needed.

### 2. Lombok Version Pinned (`pom.xml`)
Spring Boot 3.5 removed Lombok from its managed BOM, causing a runtime error:

```
java.lang.RuntimeException: org.apache.maven.artifact.InvalidArtifactRTException:
For artifact {org.projectlombok:lombok:null:jar}: The version cannot be empty.
```

Fixed by explicitly declaring the version in `<properties>`:

```xml
<lombok.version>1.18.36</lombok.version>
```

### 3. README Added (`README.md`)
Created `README.md` from scratch covering:
- Tech stack and prerequisites
- Database setup and configuration
- Full API reference for all 5 controllers (Users, Products, Cart, Orders, Payments)
- All enum values (order statuses, payment statuses/methods, user roles)
- Project structure overview
- Build and run instructions

## Commits

| Hash | Message |
|------|---------|
| `7724e4c` | Upgrade Spring Boot from 4.0.6 to 3.5.14 |
| `d739c23` | Add README with setup instructions and API reference |
| `eb2003c` | Pin Lombok 1.18.36 explicitly — removed from Spring Boot 3.5 BOM |
