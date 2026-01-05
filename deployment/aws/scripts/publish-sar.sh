#!/bin/bash
set -euo pipefail

# Configuration for SAR publishing
S3_BUCKET=${SAR_PUBLISH_BUCKET:-mocknest-sar-publish}
REGION=${AWS_REGION:-us-east-1}  # SAR publishing typically done in us-east-1
VERSION=${SAR_VERSION:-1.0.0}

echo "📦 Publishing MockNest Serverless to AWS Serverless Application Repository..."
echo "  Bucket: $S3_BUCKET"
echo "  Region: $REGION"
echo "  Version: $VERSION"
echo ""

# Check if bucket exists, create if not
if ! aws s3 ls "s3://$S3_BUCKET" --region $REGION >/dev/null 2>&1; then
    echo "📦 Creating S3 bucket for SAR publishing: $S3_BUCKET"
    aws s3 mb "s3://$S3_BUCKET" --region $REGION
fi

# Build the application first
echo "🔨 Building MockNest Serverless..."
cd "$(dirname "$0")/.."
sam build

# Package the template for SAR
echo "📦 Packaging template for SAR..."
sam package \
    --template-file template.yaml \
    --output-template-file packaged.yml \
    --s3-bucket $S3_BUCKET \
    --region $REGION

# Publish to SAR
echo "🚀 Publishing to AWS Serverless Application Repository..."
sam publish \
    --template packaged.yml \
    --region $REGION \
    --semantic-version $VERSION

echo ""
echo "✅ MockNest Serverless published to SAR successfully!"
echo ""
echo "📋 Next steps:"
echo "1. Go to AWS Console > Serverless Application Repository"
echo "2. Find 'mocknest-serverless' in your applications"
echo "3. Make it public if you want others to use it"
echo "4. Update the ApplicationId in scripts/deploy-sar-app.yml with the published ARN"