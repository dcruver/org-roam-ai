# Deployment Guide - org-roam-ai

This guide covers deploying all three components of org-roam-ai in a reliable, maintainable way.

## Overview

org-roam-ai consists of three integrated components:
1. **Emacs package** (Elisp) - Already on GitHub, accessible via straight.el
2. **MCP server** (Python) - Needs packaging and remote access
3. **Agent** (Java) - Needs distribution and service setup

## Deployment Strategies

### Strategy 1: Git + pip + systemd (Recommended for Single Server)

Best for: Single Proxmox server deployment with automatic updates

**Advantages:**
- Simple version management via git tags
- Easy updates with git pull
- Systemd handles service lifecycle
- All components accessible remotely

**Setup:**

1. **Emacs Package** (Already Done)
   ```elisp
   ;; In your Emacs config
   (straight-use-package
     '(org-roam-semantic :type git
                         :host github
                         :repo "yourusername/org-roam-ai"
                         :files ("emacs/*.el")))
   ```

2. **MCP Server via Git**
   ```bash
   # Install directly from Gitea
   pip install git+https://gitea-backend.cruver.network/dcruver/org-roam-ai.git#subdirectory=mcp

   # Or clone and install in development mode
   cd /opt/org-roam-ai
   git clone https://gitea-backend.cruver.network/dcruver/org-roam-ai.git
   cd org-roam-ai/mcp
   pip install -e .
   ```

3. **Agent via Releases**
   ```bash
   # Download from Gitea releases
   cd /opt/org-roam-agent
   wget https://gitea-backend.cruver.network/dcruver/org-roam-ai/releases/download/v0.1.0/embabel-note-gardener-0.1.0.jar
   ```

### Strategy 2: Package Registries (Recommended for Multiple Installations)

Best for: Multiple servers or sharing with others

#### A. MCP Server to Gitea PyPI Registry

**Setup Gitea Package Registry:**

1. Create `.pypirc` in your home directory:
   ```ini
   [distutils]
   index-servers =
       gitea

   [gitea]
   repository = https://gitea-backend.cruver.network/api/packages/dcruver/pypi
   username = dcruver
   password = YOUR_GITEA_TOKEN
   ```

2. Publish MCP package:
   ```bash
   cd mcp
   python -m build
   python -m twine upload --repository gitea dist/*
   ```

3. Install from Gitea PyPI:
   ```bash
   pip install org-roam-mcp \
     --index-url https://gitea-backend.cruver.network/api/packages/dcruver/pypi/simple
   ```

4. Or add to `requirements.txt`:
   ```
   --index-url https://gitea-backend.cruver.network/api/packages/dcruver/pypi/simple
   org-roam-mcp==0.1.0
   ```

#### B. Agent to Gitea Releases

**Create Release with Maven:**

1. Update version in `pom.xml`:
   ```xml
   <version>0.1.0</version>
   ```

2. Build release artifact:
   ```bash
   cd agent
   ./mvnw clean package
   cp target/embabel-note-gardener-0.1.0-SNAPSHOT.jar \
      target/embabel-note-gardener-0.1.0.jar
   ```

3. Create Gitea release:
   - Go to your Gitea repo
   - Click "Releases" → "New Release"
   - Tag: `v0.1.0`
   - Upload: `embabel-note-gardener-0.1.0.jar`

4. Install on server:
   ```bash
   wget https://gitea-backend.cruver.network/dcruver/org-roam-ai/releases/download/v0.1.0/embabel-note-gardener-0.1.0.jar
   ```

### Strategy 3: Docker Compose (Most Robust)

Best for: Isolated deployments, easy backups, consistent environments

**Note**: Emacs package stays on host (needs filesystem access), but MCP and Agent run in containers.

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mcp:
    image: gitea-backend.cruver.network/dcruver/org-roam-mcp:latest
    container_name: org-roam-mcp
    ports:
      - "8000:8000"
    volumes:
      - ${HOME}/org-roam:/org-roam:ro
      - ${HOME}/.emacs.d/server:/emacs-server:ro
    environment:
      - EMACS_SERVER_FILE=/emacs-server/server
    restart: unless-stopped
    networks:
      - org-roam

  agent:
    image: gitea-backend.cruver.network/dcruver/org-roam-agent:latest
    container_name: org-roam-agent
    volumes:
      - ${HOME}/org-roam:/org-roam
      - ./agent-config:/config
    environment:
      - ORG_ROAM_PATH=/org-roam
      - MCP_BASE_URL=http://mcp:8000
      - OLLAMA_BASE_URL=http://host.docker.internal:11434
    depends_on:
      - mcp
    restart: unless-stopped
    networks:
      - org-roam

networks:
  org-roam:
    driver: bridge
```

**Dockerfiles:**

`mcp/Dockerfile`:
```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY pyproject.toml README.md ./
COPY src/ src/

RUN pip install --no-cache-dir .

EXPOSE 8000

CMD ["org-roam-mcp"]
```

`agent/Dockerfile`:
```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/embabel-note-gardener-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Systemd Service Setup (Recommended)

### MCP Server Service

Create `/etc/systemd/system/org-roam-mcp.service`:

```ini
[Unit]
Description=Org-roam MCP Server
After=network.target emacs.service
Requires=emacs.service

[Service]
Type=simple
User=dcruver
Group=dcruver
WorkingDirectory=/home/dcruver
Environment="PATH=/home/dcruver/.local/bin:/usr/local/bin:/usr/bin"
Environment="EMACS_SERVER_FILE=/home/dcruver/.emacs.d/server/server"
ExecStart=/home/dcruver/.local/bin/org-roam-mcp
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### Agent Service

Create `/etc/systemd/system/org-roam-agent.service`:

```ini
[Unit]
Description=Org-roam Maintenance Agent
After=network.target org-roam-mcp.service ollama.service
Requires=org-roam-mcp.service

[Service]
Type=simple
User=dcruver
Group=dcruver
WorkingDirectory=/opt/org-roam-agent
Environment="ORG_ROAM_PATH=/home/dcruver/org-roam"
Environment="OLLAMA_BASE_URL=http://localhost:11434"
Environment="MCP_BASE_URL=http://localhost:8000"
ExecStart=/usr/bin/java -jar /opt/org-roam-agent/embabel-note-gardener.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### Enable and Start Services

```bash
# Enable services
sudo systemctl daemon-reload
sudo systemctl enable org-roam-mcp.service
sudo systemctl enable org-roam-agent.service

# Start services
sudo systemctl start org-roam-mcp.service
sudo systemctl start org-roam-agent.service

# Check status
sudo systemctl status org-roam-mcp.service
sudo systemctl status org-roam-agent.service

# View logs
sudo journalctl -u org-roam-mcp.service -f
sudo journalctl -u org-roam-agent.service -f
```

## Complete Installation Script

Create `install.sh` for automated setup:

```bash
#!/bin/bash
set -e

echo "Installing org-roam-ai components..."

# Configuration
INSTALL_DIR="/opt/org-roam-ai"
AGENT_VERSION="0.1.0"
GITEA_URL="https://gitea-backend.cruver.network/dcruver/org-roam-ai"

# Check prerequisites
command -v python3 >/dev/null 2>&1 || { echo "Python 3 required"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Java 21 required"; exit 1; }
command -v ollama >/dev/null 2>&1 || { echo "Ollama required"; exit 1; }

# 1. Install MCP server
echo "Installing MCP server..."
pip install --user git+${GITEA_URL}.git#subdirectory=mcp

# 2. Download Agent
echo "Installing Agent..."
sudo mkdir -p ${INSTALL_DIR}
sudo chown $USER:$USER ${INSTALL_DIR}
wget ${GITEA_URL}/releases/download/v${AGENT_VERSION}/embabel-note-gardener-${AGENT_VERSION}.jar \
  -O ${INSTALL_DIR}/embabel-note-gardener.jar

# 3. Create systemd services
echo "Creating systemd services..."
sudo tee /etc/systemd/system/org-roam-mcp.service > /dev/null <<EOF
[Unit]
Description=Org-roam MCP Server
After=network.target

[Service]
Type=simple
User=$USER
ExecStart=$(which org-roam-mcp)
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF

sudo tee /etc/systemd/system/org-roam-agent.service > /dev/null <<EOF
[Unit]
Description=Org-roam Maintenance Agent
After=network.target org-roam-mcp.service

[Service]
Type=simple
User=$USER
WorkingDirectory=${INSTALL_DIR}
Environment="ORG_ROAM_PATH=$HOME/org-roam"
ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/embabel-note-gardener.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF

# 4. Enable services
sudo systemctl daemon-reload
sudo systemctl enable org-roam-mcp.service org-roam-agent.service

echo "Installation complete!"
echo ""
echo "To start services:"
echo "  sudo systemctl start org-roam-mcp.service"
echo "  sudo systemctl start org-roam-agent.service"
echo ""
echo "To view logs:"
echo "  sudo journalctl -u org-roam-mcp.service -f"
echo "  sudo journalctl -u org-roam-agent.service -f"
```

Make it executable and run:
```bash
chmod +x install.sh
./install.sh
```

## Version Management

### Semantic Versioning

Use semantic versioning (MAJOR.MINOR.PATCH) for all components:

- **0.1.0** - Initial functional release
- **0.2.0** - New features (proposal application, file watching)
- **1.0.0** - Production-ready with all planned features

### Release Process

1. **Update versions:**
   ```bash
   # MCP: pyproject.toml
   version = "0.1.0"

   # Agent: pom.xml
   <version>0.1.0</version>

   # Emacs: org-roam-vector-search.el
   ;; Version: 0.1.0
   ```

2. **Create git tag:**
   ```bash
   git tag -a v0.1.0 -m "Release v0.1.0"
   git push origin v0.1.0
   ```

3. **Build and publish:**
   ```bash
   # MCP
   cd mcp && python -m build && twine upload --repository gitea dist/*

   # Agent
   cd agent && ./mvnw clean package
   ```

4. **Create Gitea release:**
   - Upload JAR file
   - Document changes
   - Link to documentation

## Configuration Management

### Centralized Configuration

Create `/etc/org-roam-ai/config.yml`:

```yaml
# Org-roam paths
org_roam_path: /home/dcruver/org-roam
emacs_server: /home/dcruver/.emacs.d/server/server

# Ollama
ollama_url: http://localhost:11434
chat_model: gpt-oss:20b
embedding_model: nomic-embed-text:latest

# MCP Server
mcp_port: 8000
mcp_timeout: 30000

# Agent
target_health: 90
audit_schedule: "0 2 * * *"  # 2 AM daily
```

Reference in services:
```ini
EnvironmentFile=/etc/org-roam-ai/config.yml
```

## Monitoring & Logs

### Centralized Logging

Configure journald forwarding:

```bash
# View all org-roam logs
sudo journalctl -u 'org-roam-*' -f

# Export logs
sudo journalctl -u org-roam-agent.service --since today > agent.log
```

### Health Checks

Add to crontab:
```bash
# Check services every hour
0 * * * * systemctl is-active org-roam-mcp.service || systemctl restart org-roam-mcp.service
5 * * * * systemctl is-active org-roam-agent.service || systemctl restart org-roam-agent.service
```

### Monitoring Dashboard

Optional: Set up Grafana/Prometheus to monitor:
- MCP server uptime
- Agent execution stats
- Corpus health over time
- LLM call latency

## Backup Strategy

### Configuration Backup
```bash
# Backup all configs
tar -czf org-roam-ai-config-$(date +%Y%m%d).tar.gz \
  /etc/org-roam-ai/ \
  /etc/systemd/system/org-roam-*.service
```

### Database Backup
```bash
# Backup org-roam database
cp ~/.emacs.d/org-roam.db ~/.emacs.d/org-roam.db.backup

# Backup agent embeddings
cp ~/.gardener/embeddings.db ~/.gardener/embeddings.db.backup
```

## Troubleshooting

### Service won't start
```bash
# Check service status
systemctl status org-roam-mcp.service

# Check logs
journalctl -xe -u org-roam-mcp.service

# Test manually
org-roam-mcp  # Should start server
curl http://localhost:8000  # Should return health check
```

### Dependency issues
```bash
# Reinstall MCP
pip uninstall org-roam-mcp
pip install --user git+https://gitea-backend.cruver.network/dcruver/org-roam-ai.git#subdirectory=mcp

# Check Java version
java -version  # Should be 21+

# Check Ollama
ollama list  # Should show models
```

## Recommended Deployment (Single Proxmox Server)

For your Proxmox server, I recommend:

1. **MCP Server**: Install via pip from Gitea PyPI, run as systemd service
2. **Agent**: Download JAR from Gitea releases, run as systemd service
3. **Emacs**: Keep current straight.el setup
4. **Automation**: Use install.sh script for initial setup
5. **Updates**: `git pull` + `systemctl restart`

This gives you:
- ✅ Remote accessibility (Gitea)
- ✅ Easy updates (git tags + releases)
- ✅ Auto-start on boot (systemd)
- ✅ Centralized logging (journald)
- ✅ Service dependencies (MCP before Agent)
- ✅ Simple rollback (previous release)

## Next Steps

1. Push code to Gitea: `git push gitea main`
2. Create first release: Tag v0.1.0
3. Publish MCP to Gitea PyPI
4. Upload Agent JAR to release
5. Run install.sh on Proxmox
6. Set up systemd services
7. Configure automatic updates

---

**See also:**
- [ARCHITECTURE.md](ARCHITECTURE.md) - System design
- [DEVELOPMENT.md](DEVELOPMENT.md) - Development guide
- [agent/IMPLEMENTATION-PROGRESS.md](agent/IMPLEMENTATION-PROGRESS.md) - Recent changes
