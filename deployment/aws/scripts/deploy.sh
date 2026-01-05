#!/bin/bash
set -e

ENVIRONMENT=${1:-default}

echo "Deploying MockNest Serverless to environment: $ENVIRONMENT"

# Navigate to deployment directory
cd "$(dirname "$0")"

# Build first
echo "Building application..."
./build.sh

# Deploy with SAM
echo "Deploying with SAM..."
sam deploy --config-env "$ENVIRONMENT" --no-confirm-changeset

# Get stack outputs
echo "Retrieving deployment information..."
STACK_NAME=$(sam list stack-outputs --config-env "$ENVIRONMENT" --output json | jq -r '.[0].StackName' 2>/dev/null || echo "mocknest-serverless-$ENVIRONMENT")

if aws cloudformation describe-stacks --stack-name "$STACK_NAME" >/dev/null 2>&1; then
    echo ""
    echo "🚀 Deployment completed successfully!"
    echo ""
    echo "📋 Stack Information:"
    echo "  Stack Name: $STACK_NAME"
    echo "  Environment: $ENVIRONMENT"
    echo ""
    echo "📊 Stack Outputs:"
    aws cloudformation describe-stacks --stack-name "$STACK_NAME" \
        --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue,Description]' \
        --output table
    echo ""
    echo "🔑 To get your API key:"
    echo "  aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==\`MockNestApiKey\`].OutputValue' --output text"
    echo ""
    echo "🧪 Test your deployment:"
    API_URL=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --query 'Stacks[0].Outputs[?OutputKey==`MockNestApiUrl`].OutputValue' --output text 2>/dev/null || echo "")
    if [ -n "$API_URL" ]; then
        echo "  curl -H \"x-api-key: \$(aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==\\\`MockNestApiKey\\\`].OutputValue' --output text)\" \\"
        echo "       -X GET \"${API_URL}__admin/mappings\""
    fi
else
    echo "❌ Deployment may have failed. Check CloudFormation console for details."
    exit 1
fi