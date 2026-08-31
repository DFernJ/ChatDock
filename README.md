# ChatDock: self-hosted Docker services manager with failure detection and notification system using a Discord bot.

ChatDock is a self-hosted Docker services manager that provides failure detection and notification capabilities through a Discord bot. 
It allows users to monitor their Docker containers, receive alerts when services fail, and manage their Docker environment efficiently. 
In addition, ChatDock offers a user-friendly interface for managing Docker services, making it easier to deploy, update, and maintain applications in a containerized environment.
It integrates seamlessly with Discord, enabling users to receive real-time notifications about the status of their Docker services directly in their Discord channels.
In addition, ChatDock provides a failure detection diagnostic system empowered by AI that helps users identify and troubleshoot issues with their Docker containers, 
ensuring that services remain operational and minimizing downtime.

# Architecture

The system is based on 6 essential components deployed in Docker containers:
1. **MySQL Database**: Stores users information, app stacks with secrets and notifications.
2. **Core API**: Handles the main logic of the system, including user management, app stack management, 
and notification handling. In addition, it also provides an API with Webhook integration to interact with Discord bot 
and SSE to interact with the web service.
3. **Discord Bot**: A bot that connects to Discord, manage guild's channels and send notifications to users about the 
status of their Docker services. It also provides a command interface to manage the system through Discord.
4. **Web Service**: A web interface that allows users to manage their Docker services, view logs, realize diagnostics, 
and configure notifications. It also provides a dashboard to monitor the status of Docker containers.
5. **Reverse Proxy**: A reverse proxy that handles incoming requests and routes them to the appropriate service.
6. **Cloudflare Tunnel**: A tunnel that allows secure access to the web service from the internet without exposing the 
server's IP address. It also provides SSL encryption for secure communication between the client and the server and anti 
DDoS protection.

It also provides custom containers to analyze GitHub repositories and zip files to detect Dockerfiles, docker-compose files 
and security issues like zip bombs or inode bombs. To create their image and import the service into the system.

In addition, the system provides 3 main network interfaces:
1. **External Network**: A network that allows communication between the Cloudflare tunnel, the reverse proxy and the web service.
2. **Internal Network**: A network that allows communication between the core API, the reverse proxy, the discord bot and the mysql database.
3. **Apps Network**: A network that allows communication between the reverse proxy and the imported services.

# Technologies

ChatDock is built using a variety of technologies to ensure robustness, scalability, and ease of use.
1. **Docker**: The core technology that allows for containerization of applications, enabling easy deployment and management of services.
2. **MySQL**: A relational database management system used to store user data, application
3. **Java + Spring Boot**: The backend of the system is built using Java and Spring Boot, providing a robust and scalable framework for developing the core API.
4. **React + TypeScript**: The frontend of the system is built using React and TypeScript, providing a responsive and user-friendly interface for managing Docker services.
5. **Python**: The analyzer service is built using a Python script.

# Previous Requirements

To run ChatDock, the following requirements must be met:
1. **Docker**: Ensure that Docker is installed and running on your system. 
2. **Docker Compose**: Ensure that Docker Compose is installed on your system. 
3. **Discord Account**: You need a Discord account to create a bot and receive notifications. 
4. **Discord Bot Token**: You need to create a Discord bot and obtain its token. 
5. **Cloudflare Account**: You need a Cloudflare account to set up the tunnel for secure access to the web service. 
6. **Cloudflare Tunnel Token**: You need to create a Cloudflare tunnel and obtain its token. 
7. **Domain Name**: You need a domain name to set up the Cloudflare tunnel and access the web service securely.
8. **GitHub Account**: You need a GitHub account to analyze repositories and import services.
9. **GitHub Apps Token**: You need to create a GitHub app and obtain its token to analyze repositories and import services.

It requires to create a Discord bot, a Cloudflare tunnel and a GitHub app to obtain the necessary tokens for the system to function properly.

# Installation and setup

To install ChatDock, follow these steps:
1. Clone the repository to your local machine. Using the command:

    `git clone https://github.com/DFernJ/ChatDock.git`


2. Navigate to the cloned directory and make a copy of the `.env.example` file and rename it to `.env`. Fill in the required environment variables in the `.env` file.

    ```dotenv
    # Database (MySQL)
    MYSQL_ROOT_PASSWORD=root-user-password
    MYSQL_DATABASE=database-name
    MYSQL_USER=database-user
    MYSQL_PASSWORD=database-user-password
    MYSQL_PORT=database-port
    MYSQL_IP=127.0.0.1

    # Core API (Spring Boot)
    CORE_API_PORT=core-api-port
    JWT_SECRET=random-robust-jwt-secret
    JWT_EXPIRATION_MS=jwt-expiration-time-in-milliseconds
    COOKIE_SECURE=true-or-false-based-on-your-setup
    CORS_ALLOW_ORIGIN=allowed-origins-for-cors
    SECRET_ENCRYPTION_KEY=AES-256-encryption-key-for-imported-apps-secrets-coded-in-base64-32bytes
    GEMINI_API_KEY=gemini-api-key
    GEMINI_MODEL=gemini-api-model
    APP_PUBLIC_URL=public-url-of-the-frontend
    GITHUB_APP_CLIENT_ID=github-app-client-id
    GITHUB_APP_CLIENT_SECRET=github-app-client-secret
    GITHUB_APP_SLUG=github-app-slug
    GITHUB_APP_ID=github-app-id
    GITHUB_APP_PRIVATE_KEY=github-app-private-key-from-github-app-settings-body-without-begin-end-lines-and-newlines

    # Core API and Bot Service internal communication
    INTERNAL_SECURITY_TOKEN=internal-communication-token-between-core-api-and-bot-service

    # Bot Service (Discord)
    BOT_SERVICE_PORT=bot-service-port
    BOT_SERVICE_URL=bot-service-url-provided-by-docker-namespace-without-trailing-slash
    BACKEND_URL=core-api-url-provided-by-docker-namespace-without-trailing-slash
    DISCORD_BOT_TOKEN=discord-bot-token

    # Cloudflare (Tunnel + DNS)
    TUNNEL_TOKEN=cloudflare-tunnel-token
    CLOUDFLARE_API_TOKEN=cloudflare-api-token
    CLOUDFLARE_ZONE_ID=cloudflare-zone-id
    CLOUDFLARE_TUNNEL_ID=cloudflare-tunnel-id
    CLOUDFLARE_BASE_DOMAIN=base-domain-for-cloudflare-dns
    ```

    
3. Run the following command to start the system using Docker Compose:

    `docker-compose up --build`


4. Create an user with Admin Role in the database volume.


5. Access the web service by navigating to the domain name you set up with Cloudflare in your web browser.

# Operational configuration

Besides the environment variables in `.env` (secrets and per-deployment values such as URLs, ports and credentials), `core-api` exposes a set of internal tuning parameters in `core-api/src/main/resources/application.yml` under the `app` key. These are operational defaults (timeouts, size limits, network/image names) rather than deployment secrets, so they ship with sensible values and only need to be edited directly in the YAML file if you want to change them:

| Property | Default | Purpose |
|---|---|---|
| `app.docker.connection-timeout-seconds` | `10` | Timeout to establish a connection with the Docker daemon. |
| `app.docker.response-timeout-seconds` | `60` | Timeout to wait for a response from the Docker daemon. |
| `app.docker.stats-wait-seconds` | `5` | Max wait time when collecting a container's resource stats. |
| `app.docker.logs-wait-seconds` | `30` | Max wait time when streaming a container's logs. |
| `app.docker.pull-image-timeout-seconds` | `120` | Timeout when pulling an image before creating a container. |
| `app.docker.apps-network-name` | `chatops-apps` | Docker network used to expose imported apps through the reverse proxy. |
| `app.docker.default-subdomain-port` | `80` | Fallback container port used when publishing a subdomain and none can be inferred. |
| `app.docker.reserved-subdomains` | `www,api,admin,mail,ftp,root,localhost` | Comma-separated subdomains that can't be claimed by an app. |
| `app.dockerhub.search-page-size` | `25` | Number of results returned when searching Docker Hub for images. |
| `app.import.image` | `chatops/importer:latest` | Tag of the sandboxed image used to analyze uploaded/cloned projects. |
| `app.import.max-zip-bytes` | `314572800` (300 MB) | Max size accepted for a ZIP upload. |
| `app.import.sandbox-wait-seconds` | `60` | Max time the import sandbox container is allowed to run. |
| `app.import.build-timeout-seconds` | `300` | Timeout when building an image from an imported project. |
| `app.import.image-build-timeout-seconds` | `120` | Timeout when building the importer's own sandbox image, on first use. |
| `app.import.sandbox-memory-bytes` | `536870912` (512 MB) | Memory limit for the import sandbox container. |
| `app.ai.max-log-bytes` | `10485760` (10 MB) | Max amount of log data (tail) sent to Gemini for AI diagnosis. |
| `app.oauth.github-state-cookie-ttl-minutes` | `10` | Lifetime of the CSRF-state cookies used during the GitHub OAuth flow. |
| `app.discord.link-code-ttl-minutes` | `10` | Lifetime of the one-time code used to link a Discord account. |

`bot-service` has no equivalent section — all of its configuration is already environment-driven through `.env`.

# Repository Structure

The repository is organized as follows:

```
ChatDock/
├── core-api/           # Core API (Java + Spring Boot)
│   ├── src/main/java/com/DockerOps/   # Application source code
│   ├── src/main/resources/            # Config files, importer templates and static resources
│   ├── pom.xml                        # Maven project file
│   └── Dockerfile
│
├── bot-service/        # Discord Bot Service (Java + Spring Boot)
│   ├── src/main/java/com/chatops/     # Application source code
│   ├── src/main/resources/            # Config files
│   ├── pom.xml                        # Maven project file
│   └── Dockerfile
│
├── frontend/            # Web Service (React + TypeScript + Vite)
│   ├── src/
│   │   ├── components/                # Reusable UI components (dashboard, etc.)
│   │   ├── context/                   # React context providers
│   │   ├── lib/                       # External utilities and libraries
│   │   ├── pages/                     # Application pages/routes
│   │   └── types/                     # TypeScript types
│   ├── package.json
│   ├── nginx.conf       # Nginx configuration for serving the frontend
│   └── Dockerfile
│
├── nginx/               # Reverse Proxy
│   ├── nginx.conf
│   └── Dockerfile
│
├── docker-compose.yml   # Orchestrates all services (MySQL, core-api, bot-service, frontend, nginx, cloudflared)
├── .env.example         # Template for the required environment variables
└── README.md
```

# Main functionalities

1. **Authentication and user management**: Registration protected by invitation codes, JWT-based login/logout with secure cookies, and session validation.
2. **Application import**: Import applications either from a GitHub repository (browsing repos and branches through the GitHub App) or by uploading a ZIP file in chunks. Uploaded content is scanned for Dockerfiles and docker-compose files and validated against security issues such as zip bombs or inode bombs before building the image.
3. **Docker container management**: Full container lifecycle control (start, stop, restart, delete) directly from the web dashboard, image and volume management, network management, and a real-time event stream (SSE) to keep the dashboard state updated.
4. **Interactive web terminal and log streaming**: An in-browser interactive terminal (shell access into a running container) and a live log console, both streamed over WebSocket connections directly from the dashboard, plus on-demand container resource statistics.
5. **App stacks and secrets**: Containers are grouped into app stacks, each with its own set of secrets, which are encrypted at rest using AES-256-GCM (see `SECRET_ENCRYPTION_KEY`) before being persisted.
6. **Dynamic routing and public exposure**: Imported applications can be exposed through the reverse proxy and automatically published as subdomains via Cloudflare DNS and the Cloudflare Tunnel.
7. **AI-powered failure diagnosis**: Container logs can be analyzed by Gemini to produce a diagnosis of the failure, helping users troubleshoot issues directly from the dashboard or the Discord bot.
8. **Discord bot integration**: Slash commands to manage containers (`start`, `stop`, `restart`, `delete`, `logs`, `stats`, `diagnosis`) and to link a Discord account to a ChatDock user (`link`, `whoami`), plus automatic notifications posted to Discord channels on container failures and lifecycle events.
9. **Notification system**: In-app notifications (read/unread per user) generated from container failures and lifecycle events, in addition to the Discord notifications.
10. **User profile**: Link/unlink a GitHub account (OAuth) to import repositories, and link/unlink a Discord account to receive notifications and use the bot.
11. **Admin panel**: Manage registered users and generate/revoke invitation codes.

# Contributing

Code updates are proposed through pull requests, and issues are used to track bugs and feature requests.

## Pull request structure

When opening a pull request, please follow this structure:

```markdown
## Description

Brief summary of the changes introduced by this pull request and the motivation behind them.

## Related issue

Closes #<issue-number>

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor
- [ ] Documentation
- [ ] Other (please specify)

## Changes made

- Change 1
- Change 2
- Change 3

## How to test

Steps to test the changes locally.

## Checklist

- [ ] Code builds and runs locally
- [ ] Tests added/updated (if applicable)
- [ ] Documentation updated (if applicable)
```
