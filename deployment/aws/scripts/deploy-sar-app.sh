#!/bin/bash
set -euo pipefail

# Configuration
BUCKET_NAME=${SAM_BUCKET:-mocknest-sar-templates}
STACK_NAME=${STACK_NAME:-mocknest-serverless-sar}
REGION=${AWS_REGION:-eu-west-1}

echo "🚀 Deploying MockNest Serverless from SAR..."
echo "  Bucket: $BUCKET_NAME"
echo "  Stack: $STACK_NAME"
echo "  Region: $REGION"
echo ""

# Check if bucket exists, create if not
if ! aws s3 ls "s3://$BUCKET_NAME" --region $REGION >/dev/null 2>&1; then
    echo "📦 Creating S3 bucket: $BUCKET_NAME"
    aws s3 mb "s3://$BUCKET_NAME" --region $REGION
fi

# Package the SAR deployment template
echo "📦 Packaging SAR deployment template..."
sam package \
    --s3-bucket $BUCKET_NAME \
    --template-file scripts/deploy-sar-app.yml \
    --output-template-file packaged-sar.yml \
    --region $REGION

# Deploy the SAR app
echo "🚀 Deploying MockNest from SAR..."
sam deploy \
    --template-file packaged-sar.yml \
    --stack-name $STACK_NAME \
    --capabilities CAPABILITY_AUTO_EXPAND CAPABILITY_IAM \
    --region $REGION \
    --no-confirm-changeset

echo ""
echo "✅ MockNest Serverless deployed successfully from SAR!"
echo ""
echo "📋 Stack outputs:"
aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue,Description]' \
    --output table

echo ""
echo "🔑 To get your API key:"
echo "aws cloudformation describe-stacks --stack-name $STACK_NAME --region $REGION --query 'Stacks[0].Outputs[?OutputKey==\`MockNestApiKey\`].OutputValue' --output text"