#!/bin/bash

# Test script for audit and status commands

SAMPLES_DIR="$(pwd)/samples/notes"

echo "Testing Embabel Note Gardener"
echo "==============================="
echo ""
echo "Using samples directory: $SAMPLES_DIR"
echo ""

# Set notes path via environment variable
export ORG_ROAM_PATH="$SAMPLES_DIR"

# Run commands in sequence
# Pass Spring Boot properties before -jar, and shell commands via stdin
java -Dspring.shell.interactive.enabled=false \
     -Dspring.shell.command.script.enabled=true \
     -jar target/embabel-note-gardener-0.1.0-SNAPSHOT.jar <<EOF
status
audit
exit
EOF
