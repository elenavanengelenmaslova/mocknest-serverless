#!/bin/bash
set -e

echo "Building MockNest Serverless..."

# Navigate to project root
cd "$(dirname "$0")/../.."

# Build the application
echo "Building Kotlin application..."
./gradlew clean build

# Navigate to SAM deployment directory
cd deployment/sam

# Build with SAM
echo "Building SAM application..."
sam build

echo "Build completed successfully!"
echo "Built artifacts are ready for deployment."