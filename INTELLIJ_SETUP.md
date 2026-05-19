# Running MPhasis in IntelliJ IDEA

This guide walks you through importing and running all three Spring Boot microservices inside IntelliJ IDEA.

---

## Prerequisites

Before opening IntelliJ, make sure the following are installed and working:

| Tool | Minimum Version | Check Command |
|---|---|---|
| IntelliJ IDEA | 2023.1+ (Community or Ultimate) | — |
| JDK | 21 or higher | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker Desktop | Latest | `docker ps` |
| ngrok | Any | `ngrok version` |

> **JDK tip:** IntelliJ can download a JDK for you. Go to **File → Project Structure → SDKs → +** and pick Amazon Corretto 21 or Eclipse Temurin 21 from the list.

---

## Step 1 — Open the Project

1. Launch IntelliJ IDEA
2. Click **Open** (not "New Project")
3. Navigate to `/Users/apple/Desktop/Hackathon/MPhasis`
4. Select the **MPhasis** folder and click **Open**
5. When IntelliJ asks *"Trust this project?"* → click **Trust Project**

IntelliJ will detect three separate Maven modules (`webhook-service`, `rag-service`, `codegen-service`). It may prompt you to import them — click **Import** or **Load Maven Projects** for each.

---

## Step 2 — Import Each Service as a Maven Module

Each microservice is an independent Spring Boot project with its own `pom.xml`. You need to open them one at a time **or** add them all as separate modules.

### Option A — Open Each Service in Its Own Window (Simplest)

Open three separate IntelliJ windows:

| Window | Folder to open |
|---|---|
| Window 1 | `MPhasis/webhook-service` |
| Window 2 | `MPhasis/rag-service` |
| Window 3 | `MPhasis/codegen-service` |

For each: **File → Open** → select the service folder → **Open in New Window**

### Option B — Add All Three as Modules in One Window

1. Open `MPhasis/webhook-service` as the primary project
2. Go to **File → New → Module from Existing Sources**
3. Select `MPhasis/rag-service/pom.xml` → click **Open** → **Finish**
4. Repeat for `MPhasis/codegen-service/pom.xml`

All three services will appear in the **Project** panel on the left.

---

## Step 3 — Set the JDK for Each Module

For each service:

1. **File → Project Structure** (or `Cmd + ;` on Mac)
2. Under **Project Settings → Project**: set SDK to **Java 21**
3. Set **Language level** to **21**
4. Under **Project Settings → Modules**: select each module and set its module SDK to **21**
5. Click **Apply → OK**

---

## Step 4 — Load Maven Dependencies

In the **Maven** tool window (right sidebar, or **View → Tool Windows → Maven**):

1. Expand each service
2. Click the **Reload All Maven Projects** button (circular arrow icon at the top of Maven panel)

Wait for IntelliJ to download all dependencies. The progress bar at the bottom will show when it's done.

---

## Step 5 — Start Infrastructure (Docker)

Before running any Spring Boot service, the Docker containers must be running.

Open the **Terminal** tab inside IntelliJ (**View → Tool Windows → Terminal**) and run:

```bash
cd /Users/apple/Desktop/Hackathon/MPhasis
docker-compose up -d
```

Verify all containers are up:
```bash
docker ps
```

You should see four containers running:
- `zookeeper`
- `kafka`
- `kafka-ui`
- `postgres`

**Kafka UI:** http://localhost:8090  
**PostgreSQL:** `localhost:5432` / database: `ragdb` / user: `postgres` / password: `postgres`

---

## Step 6 — Create Run Configurations

You need one Run Configuration per service so IntelliJ knows how to launch each one.

### webhook-service

1. Click the **Run/Debug Configurations** dropdown at the top right → **Edit Configurations**
2. Click **+** → **Spring Boot**
3. Fill in:
   - **Name:** `webhook-service`
   - **Module:** select `webhook-service` (Java 21)
   - **Main class:** click the browse button and select `com.mphasis.webhook.WebhookServiceApplication`
4. Click **Apply**

### rag-service

1. Click **+** → **Spring Boot**
2. Fill in:
   - **Name:** `rag-service`
   - **Module:** select `rag-service` (Java 21)
   - **Main class:** `com.mphasis.rag.RagServiceApplication`
3. Click **Apply**

### codegen-service

1. Click **+** → **Spring Boot**
2. Fill in:
   - **Name:** `codegen-service`
   - **Module:** select `codegen-service` (Java 21)
   - **Main class:** `com.mphasis.codegen.CodegenServiceApplication`
3. Click **Apply → OK**

> **Tip:** If IntelliJ can't find the main class via browse, type it manually in the field.

---

## Step 7 — Run the Services (in Order)

Start them in this exact sequence. Wait for each one to fully start before starting the next.

### 1. webhook-service

- Select `webhook-service` from the Run Configuration dropdown
- Click the green **Run ▶** button (or `Shift + F10`)
- Wait until you see in the **Run** console:
  ```
  Started WebhookServiceApplication in X.XXX seconds
  ```
- Service is live at: http://localhost:8080

### 2. ngrok (in Terminal tab)

Open a new Terminal tab in IntelliJ (**+** button in Terminal) and run:
```bash
ngrok http 8080
```
Copy the `Forwarding` URL (e.g. `https://abc123.ngrok.io`) and update your Jira webhook URL to:
```
https://abc123.ngrok.io/webhook/jira
```

### 3. rag-service

- Select `rag-service` from the dropdown → click **Run ▶**
- Wait until you see:
  ```
  Started RagServiceApplication in X.XXX seconds
  ```
  followed by Kafka consumer partition assignment lines.
- Service is live at: http://localhost:8081
- Query UI: http://localhost:8081

### 4. codegen-service

- Select `codegen-service` from the dropdown → click **Run ▶**
- Wait until you see:
  ```
  Started CodegenServiceApplication in X.XXX seconds
  ```
- Service is live at: http://localhost:8082

---

## Step 8 — Running All Services at Once (Compound Run)

IntelliJ lets you launch all services with a single click using a **Compound** run configuration.

1. **Run/Debug Configurations → + → Compound**
2. **Name:** `All MPhasis Services`
3. Click **+** and add: `webhook-service`, `rag-service`, `codegen-service`
4. Click **Apply → OK**

Now selecting `All MPhasis Services` and clicking **Run ▶** will start all three simultaneously. Each gets its own tab in the **Run** window.

> **Note:** Still start Docker and ngrok manually first.

---

## Viewing Logs

Each running service has its own tab in the **Run** tool window at the bottom. Click the tab to switch between service logs in real time.

To filter logs by keyword: click inside the console → use `Cmd + F` (Mac) to search.

---

## Rebuilding After a Code Change

IntelliJ rebuilds automatically when you click **Run ▶**. But if you want a manual build:

- **Build → Build Project** (`Cmd + F9` on Mac)

Or rebuild a specific module:
- Right-click the module in the Project panel → **Maven → Reload project** → then **Run ▶**

---

## Debugging a Service

1. Set a breakpoint: click in the gutter (left margin) next to the line number
2. Select the service from the dropdown
3. Click the **Debug 🐛** button (next to the Run button)

The service starts in debug mode. When execution hits your breakpoint, IntelliJ pauses and shows the variable values in the **Debug** panel.

---

## Checking Service Health

You can use IntelliJ's built-in HTTP client or curl in the Terminal:

```bash
# webhook-service
curl http://localhost:8080/actuator/health 2>/dev/null || curl -s http://localhost:8080/webhook/jira -X POST -H "Content-Type: application/json" -d '{}'

# rag-service health
curl http://localhost:8081/api/health

# codegen-service health
curl http://localhost:8082/api/health
```

---

## Stopping Services

- Click the **red square ■** button in the Run/Debug toolbar to stop a specific service
- Or **Run → Stop All** to stop everything at once

To stop Docker:
```bash
docker-compose down
```

---

## Ports Quick Reference

| Service | Port | URL |
|---|---|---|
| webhook-service | 8080 | http://localhost:8080 |
| rag-service | 8081 | http://localhost:8081 |
| codegen-service | 8082 | http://localhost:8082 |
| Kafka UI | 8090 | http://localhost:8090 |
| PostgreSQL | 5432 | localhost:5432 |
| Kafka broker | 9092 | localhost:9092 |

---

## Troubleshooting

### "Port already in use" error
A previous instance is still running. In the IntelliJ Terminal:
```bash
lsof -ti :8080 | xargs kill -9   # webhook-service
lsof -ti :8081 | xargs kill -9   # rag-service
lsof -ti :8082 | xargs kill -9   # codegen-service
```

### "Cannot resolve symbol" / red imports
Click **Maven → Reload All Maven Projects** in the Maven panel (right sidebar).

### Service fails to start — Kafka connection refused
Docker is not running or Kafka hasn't started yet. Run `docker ps` and make sure all four containers are up.

### Service fails to start — PostgreSQL connection refused
Same as above — make sure Docker is running. Also verify the `postgres` container is healthy:
```bash
docker logs postgres
```

### Kotlin/Java version mismatch
Go to **File → Project Structure → Modules** and make sure every module's language level is set to **21**, not a higher or lower version.


### what if debuggers don't work in intellij then do this instead
The request is reaching the service (200 proves that). The breakpoints aren't hitting because the service is running in Run mode (▶), not Debug mode (🐛).
  
  Breakpoints only work when you start with the bug icon.

  Fix:
  
  1. Stop the running webhook-service — click the red square ■ in the Run panel
  2. Select webhook-service from the configuration dropdown at the top
  3. Click the 🐛 Debug button (right next to the ▶ Run button)
  4. Wait for Started WebhookServiceApplication in the Debug console
  5. Now change a Jira ticket status — the breakpoint on line 34 will pause execution