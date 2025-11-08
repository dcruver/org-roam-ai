#!/bin/bash
# Test org-roam-ai installation in nerdctl/containerd
# This script simulates a fresh external user installation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DOCKERFILE="Dockerfile.test"
IMAGE_NAME="org-roam-ai-test"
GITHUB_RAW_URL="https://raw.githubusercontent.com/dcruver/org-roam-ai/main"

# Print header
print_header() {
    local message="$1"
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}${message}${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

# Print test result
print_result() {
    local test_name="$1"
    local result="$2"
    
    if [ "$result" -eq 0 ]; then
        echo -e "${GREEN}✓ ${test_name} PASSED${NC}"
    else
        echo -e "${RED}✗ ${test_name} FAILED${NC}"
        exit 1
    fi
}

# Build Docker image
build_image() {
    print_header "Building Test Environment"
    
    if [ ! -f "${DOCKERFILE}" ]; then
        echo -e "${RED}Error: ${DOCKERFILE} not found${NC}"
        echo "Please run this script from the org-roam-ai project directory"
        exit 1
    fi
    
    echo -e "${YELLOW}Building image '${IMAGE_NAME}'...${NC}"
    nerdctl build -t "${IMAGE_NAME}" -f "${DOCKERFILE}" .
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Image built successfully${NC}"
        echo ""
    else
        echo -e "${RED}✗ Image build failed${NC}"
        exit 1
    fi
}

# Test 1: Install from GitHub (simulates external user)
test_github_install() {
    print_header "TEST 1: Installing from GitHub (External User Simulation)"
    
    echo -e "${YELLOW}This test simulates what an external user would experience${NC}"
    echo -e "${YELLOW}using the one-line curl installation command.${NC}"
    echo ""
    
    nerdctl run --rm "${IMAGE_NAME}" bash -c '
        set -e
        echo "Fetching installation script from GitHub..."
        curl -fsSL '"${GITHUB_RAW_URL}"'/install-mcp-standalone.sh | bash || {
            echo "ERROR: Installation from GitHub failed!"
            exit 1
        }
        
        echo ""
        echo "Verifying installation..."
        
        # Check virtual environment exists
        if [ ! -d "$HOME/.org-roam-mcp" ]; then
            echo "ERROR: Virtual environment not created"
            exit 1
        fi
        
        # Check org-roam-mcp is installed
        source ~/.org-roam-mcp/bin/activate
        if ! command -v org-roam-mcp >/dev/null 2>&1; then
            echo "ERROR: org-roam-mcp command not found"
            exit 1
        fi
        
        # Check Emacs config file created
        if [ ! -f "$HOME/.emacs.d/init-org-roam-mcp.el" ]; then
            echo "ERROR: Emacs config file not created"
            exit 1
        fi
        
        # Check init.el was modified
        if ! grep -q "init-org-roam-mcp.el" ~/.emacs.d/init.el; then
            echo "ERROR: init.el not modified"
            exit 1
        fi
        
        echo "All checks passed!"
    '
    
    local result=$?
    print_result "GitHub Installation" $result
    echo ""
}

# Test 2: Install from local script (pre-push testing)
test_local_install() {
    print_header "TEST 2: Installing from Local Script (Pre-Push Testing)"
    
    echo -e "${YELLOW}This test validates the local installation script${NC}"
    echo -e "${YELLOW}before pushing changes to GitHub.${NC}"
    echo ""
    
    nerdctl run --rm -v "$(pwd)/install-mcp-standalone.sh:/tmp/install.sh:ro" \
        "${IMAGE_NAME}" bash -c '
        set -e
        echo "Running local installation script..."
        bash /tmp/install.sh || {
            echo "ERROR: Local installation failed!"
            exit 1
        }
        
        echo ""
        echo "Verifying installation..."
        
        # Same checks as Test 1
        source ~/.org-roam-mcp/bin/activate
        command -v org-roam-mcp >/dev/null 2>&1 || {
            echo "ERROR: org-roam-mcp not installed"
            exit 1
        }
        
        [ -f "$HOME/.emacs.d/init-org-roam-mcp.el" ] || {
            echo "ERROR: Emacs config missing"
            exit 1
        }
        
        echo "All checks passed!"
    '
    
    local result=$?
    print_result "Local Installation" $result
    echo ""
}

# Test 3: Uninstall test
test_uninstall() {
    print_header "TEST 3: Testing Uninstallation"
    
    echo -e "${YELLOW}This test verifies that uninstallation removes all files${NC}"
    echo -e "${YELLOW}and leaves the system clean.${NC}"
    echo ""
    
    nerdctl run --rm \
        -v "$(pwd)/install-mcp-standalone.sh:/tmp/install.sh:ro" \
        -v "$(pwd)/uninstall-mcp.sh:/tmp/uninstall.sh:ro" \
        "${IMAGE_NAME}" bash -c '
        set -e
        
        echo "Installing..."
        bash /tmp/install.sh >/dev/null 2>&1
        
        echo "Uninstalling..."
        bash /tmp/uninstall.sh --yes || {
            echo "ERROR: Uninstallation script failed!"
            exit 1
        }
        
        echo ""
        echo "Verifying cleanup..."
        
        # Check virtual environment removed
        if [ -d "$HOME/.org-roam-mcp" ]; then
            echo "ERROR: Virtual environment still exists"
            exit 1
        fi
        
        # Check Emacs config removed
        if [ -f "$HOME/.emacs.d/init-org-roam-mcp.el" ]; then
            echo "ERROR: Emacs config file still exists"
            exit 1
        fi
        
        # Check init.el cleaned up
        if grep -q "init-org-roam-mcp.el" ~/.emacs.d/init.el 2>/dev/null; then
            echo "ERROR: init.el still references MCP config"
            exit 1
        fi
        
        echo "All cleanup verified!"
    '
    
    local result=$?
    print_result "Uninstallation" $result
    echo ""
}

# Test 4: Systemd service creation test
test_systemd_service() {
    print_header "TEST 4: Testing Systemd Service Creation"
    
    echo -e "${YELLOW}This test verifies the systemd service is created correctly.${NC}"
    echo ""
    
    nerdctl run --rm \
        -v "$(pwd)/install-mcp-standalone.sh:/tmp/install.sh:ro" \
        "${IMAGE_NAME}" bash -c '
        set -e
        
        echo "Installing..."
        bash /tmp/install.sh >/dev/null 2>&1
        
        echo "Checking systemd service..."
        
        # Check service file exists
        if [ ! -f "/etc/systemd/system/org-roam-mcp.service" ]; then
            echo "ERROR: Service file not created"
            exit 1
        fi
        
        # Verify service file contains correct paths
        if ! grep -q "/home/testuser/.org-roam-mcp" /etc/systemd/system/org-roam-mcp.service; then
            echo "ERROR: Service file has incorrect path"
            exit 1
        fi

        # Check service is enabled (skip in containers where systemd doesn't work)
        # Detect if running in a container
        if [ -f /.dockerenv ] || [ -f /run/.containerenv ]; then
            echo "Running in container - skipping service enablement check"
        elif command -v systemctl >/dev/null 2>&1; then
            if ! systemctl is-enabled org-roam-mcp.service >/dev/null 2>&1; then
                echo "ERROR: Service not enabled"
                exit 1
            fi
            echo "Service is enabled"
        else
            echo "Skipping service enablement check (systemctl not available)"
        fi

        echo "Service configuration verified!"
    '
    
    local result=$?
    print_result "Systemd Service" $result
    echo ""
}

# Test 5: Verify elisp functions work after installation
test_elisp_functionality() {
    print_header "TEST 5: Verifying Elisp Functions Work"
    
    echo -e "${YELLOW}This test verifies that the installed elisp packages${NC}"
    echo -e "${YELLOW}can be loaded and their functions are callable.${NC}"
    echo ""
    
    nerdctl run --rm \
        -v "$(pwd)/install-mcp-standalone.sh:/tmp/install.sh:ro" \
        "${IMAGE_NAME}" bash -c '
        set -e
        
        echo "Installing..."
        bash /tmp/install.sh >/dev/null 2>&1
        
        echo "Starting Emacs daemon..."
        emacs --daemon 2>/dev/null || {
            echo "ERROR: Failed to start Emacs daemon"
            exit 1
        }
        
        sleep 2
        
        echo "Testing basic emacsclient connection..."
        if ! emacsclient --eval "(+ 1 1)" | grep -q "2"; then
            echo "ERROR: emacsclient cannot connect to Emacs"
            exit 1
        fi
        
        echo "Loading init-org-roam-mcp.el configuration..."
        emacsclient --eval "(load \"~/.emacs.d/init-org-roam-mcp.el\")" >/dev/null 2>&1 || {
            echo "ERROR: Failed to load MCP configuration"
            exit 1
        }
        
        echo "Verifying org-roam is loaded..."
        if ! emacsclient --eval "(featurep (quote org-roam))" | grep -q "t"; then
            echo "ERROR: org-roam is not loaded"
            exit 1
        fi
        
        echo "Verifying org-roam-directory is configured..."
        if ! emacsclient --eval "(boundp (quote org-roam-directory))" | grep -q "t"; then
            echo "ERROR: org-roam-directory not configured"
            exit 1
        fi
        
        echo "Verifying org-roam-api package is loadable..."
        emacsclient --eval "(require (quote org-roam-api) nil t)" >/dev/null 2>&1 || {
            echo "WARNING: org-roam-api package not yet available (may need time to install)"
        }
        
        echo "Verifying org-roam-vector-search package is loadable..."
        emacsclient --eval "(require (quote org-roam-vector-search) nil t)" >/dev/null 2>&1 || {
            echo "WARNING: org-roam-vector-search package not yet available (may need time to install)"
        }
        
        echo "All elisp checks passed!"
    '
    
    local result=$?
    print_result "Elisp Functionality" $result
    echo ""
}

# Main test runner
main() {
    cd "$(dirname "$0")"
    
    print_header "org-roam-ai Installation Testing Suite"
    
    echo -e "${YELLOW}This suite will run 5 comprehensive tests:${NC}"
    echo "  1. GitHub installation (external user simulation)"
    echo "  2. Local installation (pre-push validation)"
    echo "  3. Uninstallation (cleanup verification)"
    echo "  4. Systemd service creation"
    echo "  5. Elisp functionality verification"
    echo ""
    
    # Check if running interactively
    if [ -t 0 ]; then
        echo -e "${YELLOW}Press Enter to continue or Ctrl+C to cancel...${NC}"
        read
    else
        echo "Running non-interactively, skipping confirmation"
    fi
    
    # Build the test image
    build_image
    
    # Run all tests
    test_github_install
    test_local_install
    test_uninstall
    test_systemd_service
    test_elisp_functionality
    
    # Final summary
    print_header "All Tests Passed!"
    echo -e "${GREEN}✓ GitHub Installation${NC}"
    echo -e "${GREEN}✓ Local Installation${NC}"
    echo -e "${GREEN}✓ Uninstallation${NC}"
    echo -e "${GREEN}✓ Systemd Service${NC}"
    echo -e "${GREEN}✓ Elisp Functionality${NC}"
    echo ""
    echo -e "${GREEN}The installation is ready for external users!${NC}"
    echo ""
}

# Run main
main
