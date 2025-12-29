#!/bin/bash
set -e

ENVIRONMENT=${1:-default}

echo "Deploying MockNest Serverless to environment: $ENVIRONMENT"

# Build first
./build.sh

# Deploy with SAM
sam deploy --config-env $ENVIRONMENT

echo "Deployment completed successfully!"
echo "Check the CloudFormation outputs for API Gateway URL and other resources."