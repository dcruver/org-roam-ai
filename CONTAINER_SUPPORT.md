# Container Support for org-roam-ai Installation

## Overview

The `install-mcp-standalone.sh` script now includes container detection and graceful handling of systemd operations that don't work in container environments.

## Changes Made

### 1. Container Detection

The script detects container environments by checking for:
- `/.dockerenv` (Docker containers)
- `/run/.containerenv` (Podman/nerdctl containers)

```bash
IS_CONTAINER=0
if [ -f /.dockerenv ] || [ -f /run/.containerenv ]; then
    IS_CONTAINER=1
fi
```

### 2. Modified Functions

#### `create_service()`
- Checks `IS_CONTAINER` flag before attempting service creation
- Also checks for `systemctl` availability
- Returns `0` (success) when skipping in containers
- Provides helpful message about manual server startup

#### `enable_service()`
- Checks `IS_CONTAINER` flag before attempting service enablement
- Also checks for `systemctl` availability
- Returns `0` (success) when skipping in containers
- Avoids systemd errors that would cause installation to fail

### 3. Container-Specific Instructions

When running in a container, the final installation output provides:

```
Running in container environment:

1. Run the MCP server manually:
   ~/.org-roam-mcp/bin/org-roam-mcp

2. Or run in background:
   nohup ~/.org-roam-mcp/bin/org-roam-mcp > /tmp/mcp.log 2>&1 &

Note: systemd is not available in containers
```

### 4. Exit Behavior

The script now exits with code `0` (success) in container environments, even when systemd operations are skipped. This allows:
- Docker/Podman builds to succeed
- CI/CD pipelines to complete
- Automated testing to pass

## Testing

### Automated Tests

Two test scripts verify the container support:

1. **`test-container-detection.sh`** - Static analysis of script logic
   - Verifies container detection code exists
   - Checks that functions handle containers
   - Validates systemctl availability checks
   - Confirms container-specific instructions

2. **`test-install-flow.sh`** - Simulated installation flow
   - Runs installation with `IS_CONTAINER=1`
   - Verifies successful completion (exit code 0)
   - Checks output messages

Run tests with:
```bash
bash test-container-detection.sh
bash test-install-flow.sh
```

### Manual Testing

Test in a real container:
```bash
# Using Docker
docker run -it --rm ubuntu:22.04 bash
# Then run installation script

# Using Podman/nerdctl
nerdctl run -it --rm ubuntu:22.04 bash
# Then run installation script
```

## Backward Compatibility

The changes are fully backward compatible:
- Normal (non-container) installations work exactly as before
- systemd services are created and enabled on regular systems
- Only container environments get the special handling

## Use Cases

This enhancement supports:

1. **Docker/Podman Testing** - Installation tests in containers
2. **CI/CD Pipelines** - Automated testing without systemd
3. **Container Deployments** - Manual server startup in containers
4. **Development Environments** - Testing in isolated containers

## Implementation Details

### Container Detection Logic

The script checks for container marker files:
- `/.dockerenv` - Created by Docker runtime
- `/run/.containerenv` - Created by Podman/nerdctl

These are reliable indicators of container environments.

### Error Handling

The script handles multiple scenarios:
1. **In container** - Skip systemd, provide manual instructions
2. **systemctl not available** - Skip systemd, provide manual instructions
3. **Normal system** - Create and enable systemd service as usual

All scenarios exit successfully (code 0) after completing appropriate steps.

### User Experience

Users get clear, actionable feedback:
- Warning symbols (⚠) for skipped operations
- Checkmarks (✓) for successful operations
- Specific instructions based on environment
- No confusing error messages

## Future Enhancements

Potential improvements:
- Add support for other init systems (runit, OpenRC, etc.)
- Provide container-specific service management examples
- Add Docker Compose examples for production deployments
- Support for Kubernetes readiness/liveness probes
