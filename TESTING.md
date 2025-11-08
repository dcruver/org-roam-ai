# Testing Guide for org-roam-ai Installation

This guide explains how to test the org-roam-ai installation scripts in an isolated environment to ensure they work correctly for external users.

## Overview

The testing framework uses **nerdctl/containerd** to create clean, isolated Ubuntu environments that simulate a fresh user installation. This approach ensures:

- **Isolation**: Tests run in containers without affecting your local environment
- **Reproducibility**: Same environment every time, eliminating "works on my machine" issues
- **Realism**: Simulates what external users experience on first installation
- **Automation**: Run all tests with a single command

## Prerequisites

### Required
- **nerdctl** (containerd-based container runtime)
- **Ubuntu 22.04 base image** (pulled automatically)

### Checking Prerequisites

```bash
# Check nerdctl is installed
command -v nerdctl && nerdctl --version

# Expected output:
# /usr/local/bin/nerdctl
# nerdctl version 2.1.3 (or higher)
```

## Quick Start

```bash
# From the org-roam-ai project directory
cd /path/to/org-roam-ai

# Run all tests
./test-installation.sh
```

The script will:
1. Build a clean test environment (Ubuntu + Emacs + org-roam)
2. Run 5 comprehensive tests
3. Report results for each test
4. Provide a summary

## Test Suite

The test suite runs **5 comprehensive tests** that validate different aspects of the installation:

### Test 1: GitHub Installation (External User Simulation)

**Purpose**: Simulates what an external user experiences using the one-line installation command.

**What it tests**:
- Fetches `install-mcp-standalone.sh` from GitHub
- Runs installation in clean environment
- Verifies all files are created in correct locations
- Checks virtual environment setup
- Validates Emacs configuration

**Command simulated**:
```bash
curl -fsSL https://raw.githubusercontent.com/dcruver/org-roam-ai/main/install-mcp-standalone.sh | bash
```

**Validation checks**:
- `~/.org-roam-mcp/` virtual environment created
- `org-roam-mcp` command is available
- `~/.emacs.d/init-org-roam-mcp.el` configuration file exists
- `~/.emacs.d/init.el` loads the MCP configuration

### Test 2: Local Installation (Pre-Push Testing)

**Purpose**: Tests the local installation script before pushing changes to GitHub.

**What it tests**:
- Mounts local `install-mcp-standalone.sh` into container
- Runs installation from local file
- Same validation checks as Test 1

**Use case**: Run this before `git push` to catch issues early.

### Test 3: Uninstallation

**Purpose**: Verifies that uninstallation removes all files and leaves the system clean.

**What it tests**:
- Installs MCP server
- Runs `uninstall-mcp.sh --yes`
- Verifies all files are removed
- Checks no remnants remain

**Validation checks**:
- Virtual environment deleted
- Emacs config file removed
- No references in `init.el`
- Clean system state

### Test 4: Systemd Service Creation

**Purpose**: Validates the systemd service is created and configured correctly.

**What it tests**:
- Service file created at `/etc/systemd/system/org-roam-mcp.service`
- Service file contains correct paths
- Service is enabled (will start on boot)

**Validation checks**:
- Service file exists
- Correct virtual environment path in service
- Service enabled with systemctl

### Test 5: Elisp Functionality ✨ NEW

**Purpose**: Verifies that the installed Emacs packages can be loaded and their functions work.

**What it tests**:
- Emacs daemon starts successfully
- emacsclient can connect
- MCP configuration loads without errors
- org-roam package is loaded
- org-roam-directory is configured
- org-roam-api package is loadable
- org-roam-vector-search package is loadable

**Validation checks**:
- `emacsclient --eval "(+ 1 1)"` returns `2`
- `(load "~/.emacs.d/init-org-roam-mcp.el")` succeeds
- `(featurep 'org-roam)` returns `t`
- `(boundp 'org-roam-directory)` returns `t`
- Packages can be required without errors

**Note**: This is the most important test as it verifies the **actual functionality**, not just file existence.

## Running Tests

### Run All Tests

```bash
./test-installation.sh
```

### Run Specific Tests (Manual)

If you need to run individual tests for debugging:

```bash
# Build the test image first
nerdctl build -t org-roam-ai-test -f Dockerfile.test .

# Then run specific test commands manually
# (see test-installation.sh for exact commands)
```

### Test Output

Successful test output:
```
========================================
org-roam-ai Installation Testing Suite
========================================

This suite will run 5 comprehensive tests:
  1. GitHub installation (external user simulation)
  2. Local installation (pre-push validation)
  3. Uninstallation (cleanup verification)
  4. Systemd service creation
  5. Elisp functionality verification

...

========================================
All Tests Passed!
========================================

✓ GitHub Installation
✓ Local Installation
✓ Uninstallation
✓ Systemd Service
✓ Elisp Functionality

The installation is ready for external users!
```

## Understanding Test Results

### ✓ Test Passed
All validation checks succeeded. The functionality works as expected.

### ✗ Test Failed
One or more validation checks failed. The script will:
- Print which check failed
- Show error messages from the container
- Exit with non-zero status

## Troubleshooting

### nerdctl command not found

**Problem**: nerdctl is not installed or not in PATH.

**Solution**:
```bash
# Install nerdctl (example for Ubuntu)
# See: https://github.com/containerd/nerdctl
wget https://github.com/containerd/nerdctl/releases/download/v2.1.3/nerdctl-2.1.3-linux-amd64.tar.gz
sudo tar -C /usr/local/bin -xzf nerdctl-2.1.3-linux-amd64.tar.gz nerdctl
```

### Cannot connect to containerd

**Problem**: containerd daemon is not running.

**Solution**:
```bash
sudo systemctl start containerd
sudo systemctl enable containerd  # Start on boot
```

### Test builds but fails during installation

**Problem**: Installation script has bugs or missing dependencies.

**Debugging**:
1. Run the failed test manually with verbose output
2. Check what step failed
3. Fix the installation script
4. Re-run tests

### Emacs packages not loading in Test 5

**Problem**: straight.el packages take time to download and compile.

**Solution**:
- This is expected on first run
- The test shows warnings but doesn't fail
- Packages will be available after first Emacs daemon start

### Image build fails

**Problem**: Network issues or base image unavailable.

**Solution**:
```bash
# Pull base image manually
nerdctl pull ubuntu:22.04

# Rebuild with --no-cache
nerdctl build --no-cache -t org-roam-ai-test -f Dockerfile.test .
```

## Development Workflow

### Before Pushing Changes

Always run tests before pushing installation script changes:

```bash
# Make changes to install-mcp-standalone.sh or uninstall-mcp.sh
vim install-mcp-standalone.sh

# Run local installation test (Test 2)
./test-installation.sh

# If all tests pass, commit and push
git add install-mcp-standalone.sh
git commit -m "fix: update installation paths"
git push
```

### Adding New Tests

To add a new test to the suite:

1. **Create test function** in `test-installation.sh`:
```bash
test_my_new_feature() {
    print_header "TEST 6: My New Feature"

    nerdctl run --rm "${IMAGE_NAME}" bash -c '
        # Your test commands here
        echo "Testing my feature..."
    '

    local result=$?
    print_result "My New Feature" $result
}
```

2. **Call test in main()**:
```bash
# In main() function, add:
test_my_new_feature
```

3. **Update test count** in the intro message

4. **Update final summary** to include new test

### Modifying the Test Environment

To change the base environment (Dockerfile.test):

```bash
# Edit Dockerfile.test
vim Dockerfile.test

# Rebuild image
nerdctl build -t org-roam-ai-test -f Dockerfile.test .

# Run tests
./test-installation.sh
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Installation Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Install nerdctl
        run: |
          wget https://github.com/containerd/nerdctl/releases/download/v2.1.3/nerdctl-2.1.3-linux-amd64.tar.gz
          sudo tar -C /usr/local/bin -xzf nerdctl-2.1.3-linux-amd64.tar.gz nerdctl

      - name: Run installation tests
        run: ./test-installation.sh
```

## Test Environment Details

### Dockerfile.test

The test environment includes:
- **Base**: Ubuntu 22.04
- **Packages**: curl, git, python3, python3-pip, python3-venv, emacs-nox, systemctl
- **User**: testuser (non-root, with sudo access)
- **Emacs**: Configured with package.el + MELPA
- **org-roam**: Installed from MELPA
- **straight.el**: Installed (required by MCP installer)
- **Sample org-roam directory**: `~/org-roam` with test note

This simulates a typical user environment where Emacs and org-roam are already set up.

## What Tests DON'T Cover

These tests focus on installation correctness. They **do not** test:

- ❌ Ollama integration (requires running Ollama instance)
- ❌ Actual semantic search functionality (requires embeddings)
- ❌ MCP server runtime behavior (requires long-running daemon)
- ❌ n8n workflow integration
- ❌ Network connectivity to external services

For these, see the main project testing documentation in `DEVELOPMENT.md`.

## Conclusion

The testing framework ensures that:
1. ✅ Installation scripts work for external users
2. ✅ All files are created in correct locations
3. ✅ Uninstallation is clean
4. ✅ Systemd service is configured properly
5. ✅ **Elisp packages load and function correctly** ← Key validation

Run these tests before every release to maintain installation quality!
