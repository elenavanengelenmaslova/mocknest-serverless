#!/bin/bash
set -euo pipefail

# Configuration for deploying MockNest Serverless from SAR
STACK_NAME=${STACK_NAME:-mocknest-serverless-sar}
REGION=${AWS_REGION:-eu-west-1}
SAR_APP_ID=${SAR_APP_ID:-"arn:aws:serverlessrepo:us-east-1:YOUR_ACCOUNT_ID:applications/MockNest-Serverless"}

echo "🚀 Deploying MockNest Serverless from SAR..."
echo "  Stack: $STACK_NAME"
echo "  Region: $REGION"
echo "  SAR App: $SAR_APP_ID"
echo ""

# Check if SAR application ID is provided
if [[ "$SAR_APP_ID" == *"YOUR_ACCOUNT_ID"* ]]; then
    echo -e "❌ ERROR: Please set the SAR_APP_ID environment variable with the actual published application ARN"
    echo "   Example: export SAR_APP_ID='arn:aws:serverlessrepo:us-east-1:123456789012:applications/MockNest-Serverless'"
    echo "   Or pass it as parameter: SAR_APP_ID='arn:...' $0"
    exit 1
fi

# Create a temporary template for SAR deployment
TEMP_TEMPLATE=$(mktemp)
cat > "$TEMP_TEMPLATE" << EOF
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: Deploy MockNest Serverless from AWS Serverless Application Repository

Parameters:
  DeploymentName:
    Type: String
    Default: 'mocks'
    Description: Deployment instance identifier for this MockNest installation
  
  BucketName:
    Type: String
    Default: ''
    Description: Custom S3 bucket name for mock storage (optional, auto-generated if empty)
  
  LambdaMemorySize:
    Type: Number
    Default: 1024
    Description: Lambda function memory size in MB
  
  LambdaTimeout:
    Type: Number
    Default: 120
    Description: Lambda function timeout in seconds
  
  BedrockModelName:
    Type: String
    Default: 'AmazonNovaPro'
    Description: Bedrock model name for AI-powered mock generation
  
  BedrockInferenceMode:
    Type: String
    Default: 'AUTO'
    Description: Inference profile selection mode (AUTO, GLOBAL_ONLY, GEO_ONLY)
  
  BedrockGenerationMaxRetries:
    Type: Number
    Default: 1
    Description: Maximum retry attempts for AI mock generation

Resources:
  MockNestApp:
    Type: AWS::Serverless::Application
    Properties:
      Location:
        ApplicationId: $SAR_APP_ID
        SemanticVersion: 1.0.0
      Parameters:
        DeploymentName: !Ref DeploymentName
        BucketName: !Ref BucketName
        LambdaMemorySize: !Ref LambdaMemorySize
        LambdaTimeout: !Ref LambdaTimeout
        BedrockModelName: !Ref BedrockModelName
        BedrockInferenceMode: !Ref BedrockInferenceMode
        BedrockGenerationMaxRetries: !Ref BedrockGenerationMaxRetries

Outputs:
  MockNestApiUrl:
    Description: "MockNest Serverless API Gateway URL"
    Value: !GetAtt MockNestApp.Outputs.MockNestApiUrl
  
  MockNestApiKey:
    Description: "MockNest Serverless API Key"
    Value: !GetAtt MockNestApp.Outputs.MockNestApiKey
  
  MockStorageBucket:
    Description: "MockNest S3 Storage Bucket"
    Value: !GetAtt MockNestApp.Outputs.MockStorageBucket
  
  Region:
    Description: "AWS region where MockNest is deployed"
    Value: !GetAtt MockNestApp.Outputs.Region
  
  DeploymentName:
    Description: "Deployment instance identifier"
    Value: !GetAtt MockNestApp.Outputs.DeploymentName
EOF

# Deploy using SAM
echo "🚀 Deploying MockNest from SAR..."
sam deploy \
    --template-file "$TEMP_TEMPLATE" \
    --stack-name "$STACK_NAME" \
    --capabilities CAPABILITY_AUTO_EXPAND CAPABILITY_IAM \
    --region "$REGION" \
    --no-confirm-changeset \
    --parameter-overrides \
        DeploymentName="${DEPLOYMENT_NAME:-mocks}" \
        BucketName="${BUCKET_NAME:-}" \
        LambdaMemorySize="${LAMBDA_MEMORY_SIZE:-1024}" \
        LambdaTimeout="${LAMBDA_TIMEOUT:-120}" \
        BedrockModelName="${BEDROCK_MODEL_NAME:-AmazonNovaPro}" \
        BedrockInferenceMode="${BEDROCK_INFERENCE_MODE:-AUTO}" \
        BedrockGenerationMaxRetries="${BEDROCK_GENERATION_MAX_RETRIES:-1}"

# Clean up temporary template
rm "$TEMP_TEMPLATE"

echo ""
echo "✅ MockNest Serverless deployed successfully from SAR!"
echo ""
echo "📋 Stack outputs:"
aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue,Description]' \
    --output table

echo ""
echo "🔑 To get your API key:"
echo "aws cloudformation describe-stacks --stack-name $STACK_NAME --region $REGION --query 'Stacks[0].Outputs[?OutputKey==\`MockNestApiKey\`].OutputValue' --output text"
echo ""
echo "📖 Usage instructions:"
echo "1. Use the API URL and API Key from the outputs above"
echo "2. Set the x-api-key header with your API key for all requests"
echo "3. Access WireMock admin API at: <API_URL>/__admin/"
echo "4. Access AI generation API at: <API_URL>/ai/ (if Bedrock is enabled)"
echo "5. Create mocks at: <API_URL>/mocknest/"