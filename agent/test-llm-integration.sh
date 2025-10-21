#!/bin/bash
# Test script to verify LLM integration

echo "Starting application and running audit..."
echo "audit" | timeout 30 java -jar target/embabel-note-gardener-0.1.0-SNAPSHOT.jar 2>&1 | grep -A 20 "Audit completed"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ LLM integration test PASSED - audit completed successfully"
else
    echo ""
    echo "❌ LLM integration test FAILED"
fi
