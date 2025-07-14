#!/bin/bash

echo "=== Memory Usage Comparison ==="

# Test native Python
echo "Testing  native Python with uv run..."
/usr/bin/time -f "Max Memory: %M KB" uv run test.py 2>&1 | grep "Max Memory"

# Test Docker
echo "Testing Docker container..."
docker run 