#!/bin/bash
set -e

# Configuration
GITEA_USER="dcruver"
GITEA_TOKEN="${GITEA_TOKEN:-}"
ARTIFACT_VERSION="0.1.0-SNAPSHOT"
DEPLOY_DIR="${DEPLOY_DIR:-/opt/org-roam-agent}"
JAR_NAME="embabel-note-gardener-${ARTIFACT_VERSION}.jar"

# Gitea Maven repository URL
GITEA_MAVEN_URL="https://gitea.cruver.network/api/packages/${GITEA_USER}/maven"
ARTIFACT_PATH="com/dcruver/embabel-note-gardener/${ARTIFACT_VERSION}/${JAR_NAME}"

echo "🚀 Deploying org-roam-agent to ${DEPLOY_DIR}"

# Check for token
if [ -z "$GITEA_TOKEN" ]; then
    echo "❌ GITEA_TOKEN environment variable not set"
    echo "Usage: GITEA_TOKEN=your_token ./deploy-agent.sh"
    exit 1
fi

# Create deployment directory
echo "📁 Creating deployment directory..."
sudo mkdir -p "$DEPLOY_DIR"

# Download JAR from Gitea
echo "⬇️  Downloading JAR from Gitea Maven registry..."
sudo curl -u "${GITEA_USER}:${GITEA_TOKEN}" \
    -o "${DEPLOY_DIR}/${JAR_NAME}" \
    "${GITEA_MAVEN_URL}/${ARTIFACT_PATH}"

# Create symlink to latest
echo "🔗 Creating symlink to latest version..."
sudo ln -sf "${DEPLOY_DIR}/${JAR_NAME}" "${DEPLOY_DIR}/embabel-note-gardener.jar"

# Set permissions
echo "🔒 Setting permissions..."
sudo chmod 755 "${DEPLOY_DIR}/${JAR_NAME}"

echo "✅ Deployment complete!"
echo ""
echo "Run with:"
echo "  java -jar ${DEPLOY_DIR}/embabel-note-gardener.jar"
echo ""
echo "Or with custom notes path:"
echo "  ORG_ROAM_PATH=/path/to/notes java -jar ${DEPLOY_DIR}/embabel-note-gardener.jar"
