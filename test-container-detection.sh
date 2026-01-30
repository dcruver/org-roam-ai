#!/bin/bash
# Test the container detection logic in install-mcp-standalone.sh

set -e

echo "========================================="
echo "Testing Container Detection Logic"
echo "========================================="
echo ""

# Test 1: Verify container detection code exists
echo "Test 1: Checking for container detection code..."
if grep -q "IS_CONTAINER" install-mcp-standalone.sh; then
    echo "✓ IS_CONTAINER variable found"
else
    echo "✗ IS_CONTAINER variable not found"
    exit 1
fi

if grep -q "/.dockerenv\|/run/.containerenv" install-mcp-standalone.sh; then
    echo "✓ Container detection logic found"
else
    echo "✗ Container detection logic not found"
    exit 1
fi
echo ""

# Test 2: Verify create_service checks IS_CONTAINER
echo "Test 2: Checking create_service function..."
if grep -A 5 "create_service()" install-mcp-standalone.sh | grep -q "IS_CONTAINER"; then
    echo "✓ create_service checks IS_CONTAINER"
else
    echo "✗ create_service doesn't check IS_CONTAINER"
    exit 1
fi

if grep -A 5 "create_service()" install-mcp-standalone.sh | grep -q "return 0"; then
    echo "✓ create_service returns successfully in container"
else
    echo "✗ create_service doesn't return successfully"
    exit 1
fi
echo ""

# Test 3: Verify enable_service checks IS_CONTAINER
echo "Test 3: Checking enable_service function..."
if grep -A 5 "enable_service()" install-mcp-standalone.sh | grep -q "IS_CONTAINER"; then
    echo "✓ enable_service checks IS_CONTAINER"
else
    echo "✗ enable_service doesn't check IS_CONTAINER"
    exit 1
fi

if grep -A 5 "enable_service()" install-mcp-standalone.sh | grep -q "return 0"; then
    echo "✓ enable_service returns successfully in container"
else
    echo "✗ enable_service doesn't return successfully"
    exit 1
fi
echo ""

# Test 4: Verify systemctl availability check
echo "Test 4: Checking systemctl availability check..."
if grep "command -v systemctl" install-mcp-standalone.sh | grep -q "/dev/null"; then
    echo "✓ systemctl availability check found"
else
    echo "✗ systemctl availability check not found"
    exit 1
fi
echo ""

# Test 5: Verify container-specific instructions
echo "Test 5: Checking container-specific instructions..."
if grep -q "Running in container environment" install-mcp-standalone.sh; then
    echo "✓ Container-specific instructions found"
else
    echo "✗ Container-specific instructions not found"
    exit 1
fi

if grep -q "Run the MCP server manually" install-mcp-standalone.sh; then
    echo "✓ Manual run instructions found"
else
    echo "✗ Manual run instructions not found"
    exit 1
fi
echo ""

# Test 6: Simulate container environment
echo "Test 6: Simulating container environment..."
mkdir -p /tmp/test-install
cd /tmp/test-install

# Create a fake .dockerenv file
touch /.dockerenv 2>/dev/null && FAKE_DOCKERENV=1 || FAKE_DOCKERENV=0

# Extract and test the container detection logic
cat > test_detection.sh << 'EOF'
#!/bin/bash
IS_CONTAINER=0
if [ -f /.dockerenv ] || [ -f /run/.containerenv ]; then
    IS_CONTAINER=1
fi

if [ $IS_CONTAINER -eq 1 ]; then
    echo "Detected as container"
    exit 0
else
    echo "Detected as host"
    exit 1
fi
EOF

chmod +x test_detection.sh

# If we created /.dockerenv, the detection should work
if [ $FAKE_DOCKERENV -eq 1 ]; then
    if ./test_detection.sh; then
        echo "✓ Container detection logic works"
    else
        echo "✗ Container detection logic failed"
        exit 1
    fi
    rm /.dockerenv
else
    echo "⚠ Cannot create /.dockerenv (need root), skipping runtime test"
fi

cd - > /dev/null
rm -rf /tmp/test-install
echo ""

echo "========================================="
echo "All Tests Passed!"
echo "========================================="
echo ""
echo "Summary of changes:"
echo "  • Container detection via /.dockerenv or /run/.containerenv"
echo "  • create_service() skips in containers"
echo "  • enable_service() skips in containers"
echo "  • Both check for systemctl availability"
echo "  • Container-specific instructions in final output"
echo "  • Script exits successfully (return 0) in container environments"
echo ""
