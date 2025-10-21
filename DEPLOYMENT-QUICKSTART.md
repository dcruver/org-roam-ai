# Deployment Quick Start

## TL;DR - Recommended Approach for Your Proxmox Server

1. **Push to Gitea** ✅
2. **Install via script** - One command installs everything
3. **Run as systemd services** - Auto-start, logging, restarts
4. **Update via script** - Pull latest versions

## Step-by-Step First Deployment

### 1. Push Code to Gitea

```bash
# Add Gitea remote if not already added
git remote add gitea https://gitea-backend.cruver.network/dcruver/org-roam-ai.git

# Push code
git push gitea main
```

### 2. Create First Release (Optional - for stable deployments)

```bash
# Create release v0.1.0
./scripts/release.sh 0.1.0

# Push changes and tag
git push gitea main
git push gitea v0.1.0
```

Then manually upload the JAR file to Gitea releases page:
- Go to: `https://gitea-backend.cruver.network/dcruver/org-roam-ai/releases/new`
- Tag: `v0.1.0`
- Upload: `release/embabel-note-gardener-0.1.0.jar`

### 3. Install on Proxmox

SSH to your Proxmox server and run:

```bash
# Clone repository
git clone https://gitea-backend.cruver.network/dcruver/org-roam-ai.git
cd org-roam-ai

# Run installation script
./install.sh
```

This script will:
- ✅ Check prerequisites (Python, Java, Ollama, Emacs)
- ✅ Install MCP server via pip from Gitea
- ✅ Install Agent JAR to /opt/org-roam-agent
- ✅ Create systemd services for both
- ✅ Enable services for auto-start

### 4. Pull Ollama Models

```bash
ollama pull nomic-embed-text:latest
ollama pull gpt-oss:20b
```

### 5. Start Services

```bash
sudo systemctl start org-roam-mcp.service
sudo systemctl start org-roam-agent.service
```

### 6. Verify Everything Works

```bash
# Check service status
sudo systemctl status org-roam-mcp.service
sudo systemctl status org-roam-agent.service

# Test MCP server
curl http://localhost:8000

# View logs
sudo journalctl -u org-roam-mcp.service -f
sudo journalctl -u org-roam-agent.service -f
```

## Future Updates

When you make changes and want to update your Proxmox deployment:

```bash
# On your development machine:
# 1. Push changes to Gitea
git push gitea main

# 2. Optionally create new release
./scripts/release.sh 0.2.0
git push gitea main
git push gitea v0.2.0

# On Proxmox server:
# 3. Run update script
cd org-roam-ai
./scripts/update-deployment.sh  # latest from git
# OR
./scripts/update-deployment.sh 0.2.0  # specific release
```

## Alternative: Install from Gitea PyPI (More Robust)

If you want to publish MCP to Gitea's package registry:

### One-time Setup

Create `~/.pypirc`:
```ini
[distutils]
index-servers =
    gitea

[gitea]
repository = https://gitea-backend.cruver.network/api/packages/dcruver/pypi
username = dcruver
password = YOUR_GITEA_TOKEN
```

### Publish MCP Package

```bash
./scripts/publish-mcp.sh
```

### Install from Gitea PyPI

```bash
pip install org-roam-mcp \
  --index-url https://gitea-backend.cruver.network/api/packages/dcruver/pypi/simple
```

## Service Management

### Start/Stop Services

```bash
# Start services
sudo systemctl start org-roam-mcp.service
sudo systemctl start org-roam-agent.service

# Stop services
sudo systemctl stop org-roam-agent.service
sudo systemctl stop org-roam-mcp.service

# Restart services
sudo systemctl restart org-roam-mcp.service
sudo systemctl restart org-roam-agent.service
```

### View Logs

```bash
# Follow logs in real-time
sudo journalctl -u org-roam-mcp.service -f
sudo journalctl -u org-roam-agent.service -f

# View recent logs
sudo journalctl -u org-roam-mcp.service -n 100
sudo journalctl -u org-roam-agent.service -n 100

# Export logs
sudo journalctl -u org-roam-agent.service --since today > agent.log
```

### Check Service Status

```bash
# Check if running
systemctl is-active org-roam-mcp.service
systemctl is-active org-roam-agent.service

# Detailed status
systemctl status org-roam-mcp.service
systemctl status org-roam-agent.service
```

## Configuration Locations

- **MCP Server**: `~/.local/bin/org-roam-mcp`
- **Agent JAR**: `/opt/org-roam-agent/embabel-note-gardener.jar`
- **Agent Config**: `/opt/org-roam-agent/application.yml` (optional override)
- **Systemd Services**: `/etc/systemd/system/org-roam-*.service`
- **Logs**: `journalctl -u org-roam-*`

## Troubleshooting

### Service Won't Start

```bash
# Check detailed status
systemctl status org-roam-mcp.service

# View full error logs
journalctl -xe -u org-roam-mcp.service

# Test manually
org-roam-mcp  # Should start server
curl http://localhost:8000  # Should respond
```

### MCP Server Can't Connect to Emacs

```bash
# Check Emacs server is running
emacsclient --eval '(+ 1 1)'

# Check Emacs server socket location
ls -la ~/.emacs.d/server/server

# Restart Emacs server
# In Emacs: M-x server-start
```

### Agent Can't Connect to MCP

```bash
# Check MCP is running
curl http://localhost:8000

# Check agent logs for connection errors
journalctl -u org-roam-agent.service -n 100 | grep -i "mcp\|connection"

# Verify MCP URL in agent service
systemctl cat org-roam-agent.service | grep MCP_BASE_URL
```

### Ollama Issues

```bash
# Check Ollama is running
ollama list

# Test Ollama API
curl http://localhost:11434/api/tags

# Check models are available
ollama list | grep "nomic-embed-text\|gpt-oss"
```

## Benefits of This Deployment

✅ **Version Control** - All code in Gitea
✅ **One-Command Install** - `./install.sh` does everything
✅ **Auto-Start** - Services start on boot
✅ **Auto-Restart** - Services restart on failure
✅ **Centralized Logging** - All logs in journald
✅ **Easy Updates** - `./scripts/update-deployment.sh`
✅ **Service Dependencies** - MCP starts before Agent
✅ **Rollback** - Keep backups, downgrade via git tags

## Files Created

- `install.sh` - Complete installation script
- `scripts/release.sh` - Create versioned releases
- `scripts/publish-mcp.sh` - Publish MCP to Gitea PyPI
- `scripts/update-deployment.sh` - Update existing deployment
- `DEPLOYMENT.md` - Comprehensive deployment guide
- This file - Quick reference

## Next Steps

1. ✅ Push code to Gitea
2. ✅ Run `./install.sh` on Proxmox
3. ✅ Start services
4. ✅ Test end-to-end

Your org-roam-ai will be running as system services with automatic restarts and centralized logging!

---

**See Also:**
- [DEPLOYMENT.md](DEPLOYMENT.md) - Full deployment documentation
- [ARCHITECTURE.md](ARCHITECTURE.md) - System architecture
- [agent/IMPLEMENTATION-PROGRESS.md](agent/IMPLEMENTATION-PROGRESS.md) - Latest changes
