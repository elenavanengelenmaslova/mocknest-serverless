#!/bin/bash
set -e

ENVIRONMENT=${1:-default}

echo "Deploying MockNest Serverless to environment: $ENVIRONMENT"

# Navigate to SAM deployment directory
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

    # Detect deployed auth mode from stack outputs
    DEPLOYED_AUTH_MODE=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" \
        --query 'Stacks[0].Outputs[?OutputKey==`AuthMode`].OutputValue' \
        --output text 2>/dev/null || echo "API_KEY")

    API_URL=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --query 'Stacks[0].Outputs[?OutputKey==`MockNestApiUrl`].OutputValue' --output text 2>/dev/null || echo "")

    if [ "$DEPLOYED_AUTH_MODE" = "IAM" ]; then
        echo "🔐 Auth Mode: IAM (SigV4)"
        echo ""
        echo "🧪 Test your deployment (SigV4-signed request):"
        if [ -n "$API_URL" ]; then
            echo "  curl --aws-sigv4 \"aws:amz:\$(aws configure get region):execute-api\" \\"
            echo "       --user \"\$AWS_ACCESS_KEY_ID:\$AWS_SECRET_ACCESS_KEY\" \\"
            echo "       -H \"x-amz-security-token: \$AWS_SESSION_TOKEN\" \\"
            echo "       -X GET \"${API_URL}__admin/mappings\""
        fi
    else
        echo "🔑 Auth Mode: API_KEY"
        echo ""
        echo "🔑 To get your API key:"
        echo "  aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==\`MockNestApiKey\`].OutputValue' --output text"
        echo ""
        echo "🧪 Test your deployment:"
        if [ -n "$API_URL" ]; then
            echo "  curl -H \"x-api-key: \$(aws cloudformation describe-stacks --stack-name $STACK_NAME --query 'Stacks[0].Outputs[?OutputKey==\\\`MockNestApiKey\\\`].OutputValue' --output text)\" \\"
            echo "       -X GET \"${API_URL}__admin/mappings\""
        fi
    fi
else
    echo "❌ Deployment may have failed. Check CloudFormation console for details."
    exit 1
fi