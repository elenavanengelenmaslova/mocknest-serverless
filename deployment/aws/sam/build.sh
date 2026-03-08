#!/bin/bash
set -e

echo "Building MockNest Serverless..."

# Navigate to project root
cd "$(dirname "$0")/../../.."

# Build both Lambda JARs using the new module structure
echo "Building Lambda JARs..."
./gradlew clean :software:infra:aws:runtime:shadowJar :software:infra:aws:generation:shadowJar --no-build-cache

# Verify both JARs exist
echo "Verifying JAR artifacts..."
if [ ! -f "build/dist/mocknest-runtime.jar" ]; then
    echo "ERROR: mocknest-runtime.jar not found in build/dist/"
    exit 1
fi

if [ ! -f "build/dist/mocknest-generation.jar" ]; then
    echo "ERROR: mocknest-generation.jar not found in build/dist/"
    exit 1
fi

echo "✓ Runtime JAR: $(du -h build/dist/mocknest-runtime.jar | cut -f1)"
echo "✓ Generation JAR: $(du -h build/dist/mocknest-generation.jar | cut -f1)"

# Navigate to SAM deployment directory
cd deployment/aws/sam

# Build with SAM
echo "Building SAM application..."
sam build

echo "Build completed successfully!"
echo "Built artifacts are ready for deployment."