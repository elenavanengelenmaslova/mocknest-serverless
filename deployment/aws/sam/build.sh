#!/bin/bash
set -e

echo "Building MockNest Serverless..."

# Navigate to project root
cd "$(dirname "$0")/../../.."

# Build unified Lambda JAR using the new mocknest module
echo "Building unified Lambda JAR..."
./gradlew clean :software:infra:aws:mocknest:shadowJar --no-build-cache

# Verify JAR exists
echo "Verifying JAR artifact..."
if [ ! -f "build/dist/mocknest-serverless.jar" ]; then
    echo "ERROR: mocknest-serverless.jar not found in build/dist/"
    exit 1
fi

JAR_SIZE=$(du -h build/dist/mocknest-serverless.jar | cut -f1)
echo "✓ Unified JAR: $JAR_SIZE"

# Check if JAR is under 100MB (SAR limit is 250MB, but we target 100MB)
JAR_SIZE_BYTES=$(stat -f%z build/dist/mocknest-serverless.jar 2>/dev/null || stat -c%s build/dist/mocknest-serverless.jar)
MAX_SIZE=$((100 * 1024 * 1024))
if [ $JAR_SIZE_BYTES -gt $MAX_SIZE ]; then
    echo "⚠️  WARNING: JAR size ($JAR_SIZE) exceeds 100MB target"
else
    echo "✅ JAR size is within 100MB target"
fi

# Navigate to SAM deployment directory
cd deployment/aws/sam

# Build with SAM
echo "Building SAM application..."
sam build

echo "Build completed successfully!"
echo "Built artifacts are ready for deployment."