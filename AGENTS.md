# Axentria Document Downloader — Agent Guide

## Build & Run

```sh
./mvnw clean package -DskipTests   # build JAR (skip slow Selenium test)
./mvnw test                         # all tests
./mvnw test -Dtest=AxentriaDocumentDownloaderApplicationTests   # single test
java -jar target/*.jar              # run app
```

## Project structure

- Single-module Maven, Spring Boot 3.x (parent `4.1.0`)
- **Entrypoint**: `src/main/java/py/itau/com/py/axentriadocumentdownloader/AxentriaDocumentDownloaderApplication.java`
- **Core logic**: `DocumentDownloaderService` — headless Chrome via Selenium, navigates to a URL and clicks a CSS selector to trigger download
- **Controller**: `POST /download` triggers the service
- Only one test: `@SpringBootTest` context load test

## Key dependencies

- Selenium 4.18.1 + WebDriverManager 5.7.0 + Chrome (headless)
- **AWS SDK for Java v2 (S3) 2.25.0** — uploads downloaded files to S3
- Lombok (annotation processing required at compile time)
- Prometheus metrics (actuator + micrometer)

## Configuration (all optional with defaults)

| Property | Default | Env override |
|---|---|---|
| `download.axentriaBaseUrl` | `http://pyhmlw415/AxentriaCI` | `DOWNLOAD_AXENTRIA_BASE_URL` |
| `download.linksSelector` | `a.list-group-item` | `DOWNLOAD_LINKS_SELECTOR` |
| `download.directory` | `/mnt/Downloads` | `DOWNLOAD_DIRECTORY` |
| `download.s3.bucket` | (empty — S3 upload skipped) | `S3_BUCKET` |

Set via `application.yaml`, env vars, or command-line args.

## Java version mismatch

- `pom.xml` declares **Java 17**
- `Dockerfile` builds with **eclipse-temurin:22** (maven:3.9.6-eclipse-temurin-22 for build, eclipse-temurin:22-jre for runtime)
- The Maven wrapper (`mvnw`) uses whatever `JAVA_HOME` points to — ensure it is Java 17+ (22 for Docker builds)

## Docker

Multi-stage build in `Dockerfile`:
1. Builds JAR with Maven + Java 22
2. Runtime image: eclipse-temurin:22-jre with Google Chrome installed
3. Download directory: `/mnt/Downloads`
4. Run: `docker build -t axentria-document-downloader . && docker run -p 8080:8080 axentria-document-downloader`

## S3 upload

- `DocumentDownloaderService` lists files in `download.directory` after the 10s wait and uploads each to S3 under `downloads/{timestamp}/{filename}`
- If `download.s3.bucket` is empty/null, S3 upload is skipped (safe for local dev)

## Terraform (`terraform/`)

Full AWS infra as IaC:
1. `terraform init` — creates backend (S3 bucket `axentria-terraform-state` must exist)
2. `terraform plan` / `terraform apply` — provisions all resources
3. Deploy image: build & push Docker image to the ECR repo (see output `ecr_repository_url`)

### What it creates

| Resource | Notes |
|---|---|
| VPC + 2 public subnets + IGW | CIDR `10.0.0.0/16` |
| ALB + target group + listener | health check on `/actuator/health` |
| ECS Fargate cluster + service | 1 vCPU / 2 GB, auto-assigns public IP |
| ECR repository | `axentria-document-downloader` |
| S3 bucket (versioned, encrypted) | name includes account ID for uniqueness |
| IAM execution role | ECR pull + CloudWatch logs |
| IAM task role | s3:PutObject/GetObject/ListBucket on the S3 bucket |

### Deploy steps

```sh
# 1. Build & push image (after terraform apply)
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $(terraform output -raw ecr_repository_url)
docker build -t axentria-document-downloader .
docker tag axentria-document-downloader:latest $(terraform output -raw ecr_repository_url):latest
docker push $(terraform output -raw ecr_repository_url):latest

# 2. Trigger download
curl -X POST http://$(terraform output -raw alb_dns_name):8080/download
```

## Gotchas

- Test requires Chrome/Chromium installed (or runs headless only) — may fail in environments without Chrome
- The service uses `Thread.sleep(10000)` — no smart wait logic
- No CI workflows exist
- `HELP.md` is gitignored (auto-generated Spring Initializr file)
