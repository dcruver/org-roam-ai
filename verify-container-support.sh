#!/bin/bash
# Quick verification script for container support

echo "========================================="
echo "Container Support Verification"
echo "========================================="
echo ""

PASS=0
FAIL=0

# Test 1: Script syntax
echo -n "1. Checking script syntax... "
if bash -n install-mcp-standalone.sh 2>/dev/null; then
    echo "✓ PASS"
    ((PASS++))
else
    echo "✗ FAIL"
    ((FAIL++))
fi

# Test 2: Container detection code
echo -n "2. Checking container detection... "
if grep -q "IS_CONTAINER" install-mcp-standalone.sh && \
   grep -q "/.dockerenv\|/run/.containerenv" install-mcp-standalone.sh; then
    echo "✓ PASS"
    ((PASS++))
else
    echo "✗ FAIL"
    ((FAIL++))
fi

# Test 3: create_service handles containers
echo -n "3. Checking create_service()... "
if grep -A 5 "create_service()" install-mcp-standalone.sh | grep -q "IS_CONTAINER" && \
   grep -A 5 "create_service()" install-mcp-standalone.sh | grep -q "return 0"; then
    echo "✓ PASS"
    ((PASS++))
else
    echo "✗ FAIL"
    ((FAIL++))
fi

# Test 4: enable_service handles containers
echo -n "4. Checking enable_service()... "
if grep -A 5 "enable_service()" install-mcp-standalone.sh | grep -q "IS_CONTAINER" && \
   grep -A 5 "enable_service()" install-mcp-standalone.sh | grep -q "return 0"; then
    echo "✓ PASS"
    ((PASS++))
else
    echo "✗ FAIL"
    ((FAIL++))
fi

# Test 5: systemctl availability check
echo -n "5. Checking systemctl detection... "
if grep "command -v systemctl" install-mcp-standalone.sh | grep -q "/dev/null"; then
    echo "✓ PASS"
    ((PASS++))
else
    echo "✗ FAIL"
    ((FAIL++))
fi

# Test 6: Container instructions
echo -n "6. Checking container instructions... "
if grep -q "Running in container environment" install-mcp-standalone.sh && \
   grep -q "Run the MCP server manually" install-mcp-standalone.sh; then
    echo "✓ PASS"
    ((PASS++))
else
    echo "✗ FAIL"
    ((FAIL++))
fi

echo ""
echo "========================================="
echo "Results: $PASS PASS, $FAIL FAIL"
echo "========================================="
echo ""

if [ $FAIL -eq 0 ]; then
    echo "✓ All verification tests passed!"
    echo ""
    echo "The installation script now supports:"
    echo "  • Container environment detection"
    echo "  • Graceful systemd operation skipping"
    echo "  • Container-specific instructions"
    echo "  • systemctl availability checking"
    echo ""
    exit 0
else
    echo "✗ Some tests failed. Please review the script."
    exit 1
fi
