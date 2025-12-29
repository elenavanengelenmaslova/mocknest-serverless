#!/bin/bash
set -e

echo "Building MockNest Serverless..."

# Build the application
cd ../../
./gradlew clean build

# Build with SAM
cd deployment/aws
sam build

echo "Build completed successfully!"