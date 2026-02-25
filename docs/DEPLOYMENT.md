# MockNest Serverless Deployment Guide

This guide covers deploying MockNest Serverless using AWS Serverless Application Repository (SAR) or direct SAM deployment.

## AWS Serverless Application Repository (SAR)

### One-Click Deployment from AWS Console
1. **Open AWS Console** in your preferred region
2. **Navigate to** AWS Serverless Application Repository
3. **Search for** "MockNest Serverless"
4. **Click Deploy** and configure parameters:
   - **AppRegion**: `eu-west-1` (default)
   - **BedrockModelName**: `AmazonNovaPro` (default)
   - **BedrockInferencePrefix**: `eu` (default)
   - **BucketName**: Leave empty for auto-generated name
5. **Deploy** - takes 2-3 minutes

### Deploy SAR App via Script
For automated deployments or CI/CD integration:

```bash
cd deployment/aws/sar
chmod +x deploy-sar-app.sh
./deploy-sar-app.sh
```

You can also integrate the SAR app in your existing CloudFormation stacks using the template in `deployment/aws/sar/deploy-sar-app.yml`.

### SAR Benefits
- ✅ **No local setup required** - deploy directly from AWS Console
- ✅ **Always latest version** - automatically updated
- ✅ **Deploy in any region** - uses your current console region
- ✅ **Parameter validation** - guided configuration
- ✅ **CloudFormation integration** - full stack management

## Direct SAM Deployment (Development)

### Source-Based Deployment
```bash
# Deploy with defaults (to eu-west-1)
./gradlew build
cd deployment/aws/sam
sam build
sam deploy --guided  # First time only
```

> **🌍 Region Selection**: 
> - **SAR**: Automatically deploys to your current AWS Console region
> - **Direct SAM**: Defaults to eu-west-1 (supports all features including AI)

**Or for subsequent deployments:**
```bash
sam build && sam deploy
```

**Default Configuration:**
- **AppRegion**: eu-west-1 (Ireland)
- **S3 Bucket**: Auto-generated unique name (`mocknest-serverless-{stack-name}`)
- **AI Features**: Enabled by default
- **API Authentication**: API Key (auto-generated)

### Quick Region Override
If you prefer a different region, simply add `--region`:
```bash
sam deploy --region us-east-1  # US East
sam deploy --region us-west-2  # US West (Oregon)
sam deploy --region ap-southeast-1  # Asia Pacific (Singapore)
```

### Get Deployment Info
After deployment, get your endpoint details:
```bash
# Get API Gateway URL and API Key
sam list stack-outputs --stack-name mocknest-serverless
```

## Custom Deployments (Optional)

### Deploy to Different Region

#### Option 1: Update samconfig.toml

Edit `deployment/aws/sam/samconfig.toml`:

```toml
[default.deploy.parameters]
# Change this line to your preferred region
region = "us-east-1"
```

Then deploy:

```bash
sam build
sam deploy
```

#### Option 2: Override via Command Line

```bash
sam deploy --region us-east-1
```

#### Option 3: Environment Variable

```bash
export AWS_DEFAULT_REGION=us-east-1
sam deploy
```

## Regional Considerations

### Supported Regions

MockNest Serverless can be deployed to any AWS region that supports:
- AWS Lambda
- Amazon API Gateway
- Amazon S3

### AI Features Region Support

Ensure your chosen region supports Amazon Bedrock:

**Bedrock-supported regions** (as of 2025):
- us-east-1 (N. Virginia)
- us-west-2 (Oregon)
- us-west-1 (N. California)
- eu-west-1 (Ireland)
- eu-west-3 (Paris)
- eu-central-1 (Frankfurt)
- ap-southeast-1 (Singapore)
- ap-southeast-2 (Sydney)
- ap-northeast-1 (Tokyo)
- ap-south-1 (Mumbai)
- ca-central-1 (Canada Central)

**Note**: Bedrock availability and supported models may vary by region. Check the [AWS Bedrock documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-regions.html) for the most current regional availability.

### Region-Specific Configuration Examples

#### US East (N. Virginia)
```bash
sam deploy --parameter-overrides AppRegion=us-east-1 BedrockInferencePrefix=us
```

#### US West (Oregon)
```bash
sam deploy --parameter-overrides AppRegion=us-west-2 BedrockInferencePrefix=us
```

#### Europe (Frankfurt)
```bash
sam deploy --parameter-overrides AppRegion=eu-central-1 BedrockInferencePrefix=eu
```

#### Canada (Central)
```bash
sam deploy --parameter-overrides AppRegion=ca-central-1 BedrockInferencePrefix=ca
```

#### Asia Pacific (Sydney)
```bash
sam deploy --parameter-overrides AppRegion=ap-southeast-2 BedrockInferencePrefix=ap
```

## Custom Configuration

### Custom S3 Bucket Name

```bash
sam deploy --parameter-overrides BucketName=my-mocknest-storage-bucket
```

### Bedrock Model Selection

```bash
sam deploy --parameter-overrides BedrockModelName=AmazonNovaLite
```

### Combined Configuration

```bash
sam deploy \
  --region eu-west-1 \
  --parameter-overrides \
    BedrockInferencePrefix=eu \
    BucketName=my-custom-bucket-name
```

## Post-Deployment

## SAR vs Direct Deployment Comparison

| Feature | SAR Deployment | Direct SAM Deployment |
|---------|----------------|----------------------|
| **Setup Required** | None | AWS CLI, SAM CLI, Java, Gradle |
| **Deployment Time** | 2-3 minutes | 5-10 minutes (including build) |
| **Customization** | Parameter-based | Full source code access |
| **Updates** | Automatic via SAR | Manual git pull + redeploy |
| **Use Case** | Production, quick testing | Development, customization |
| **Region** | Any (via console region) | Configurable via SAM |

**Recommendation**: Use SAR for production deployments, Direct SAM for development and customization.

```bash
# Get API Gateway URL
aws cloudformation describe-stacks \
  --stack-name mocknest-serverless \
  --query 'Stacks[0].Outputs[?OutputKey==`MockNestApiUrl`].OutputValue' \
  --output text

# Get S3 bucket name
aws cloudformation describe-stacks \
  --stack-name mocknest-serverless \
  --query 'Stacks[0].Outputs[?OutputKey==`MockStorageBucket`].OutputValue' \
  --output text

# Get API key from CloudFormation outputs
aws cloudformation describe-stacks \
  --stack-name mocknest-serverless \
  --query 'Stacks[0].Outputs[?OutputKey==`MockNestApiKey`].OutputValue' \
  --output text
```

### Update Postman Environment

After deployment, update your Postman environment variables:

1. **AWS_URL**: Use the API Gateway URL from CloudFormation outputs
2. **AWS_REGION**: Set to your deployment region
3. **api_key**: Use the API key from API Gateway

### Verify Deployment

```bash
# Test the health endpoint
curl -H "x-api-key: YOUR_API_KEY" \
  https://your-api-id.execute-api.REGION.amazonaws.com/prod/__admin/health
```

## Troubleshooting

### Common Issues

1. **Region Mismatch**: Ensure all resources are in the same region
2. **Bedrock Not Available**: If using AI features, verify Bedrock is available in your region
3. **Bucket Name Conflicts**: S3 bucket names must be globally unique

### Logs and Monitoring

```bash
# View Lambda logs
sam logs -n MockNestFunction --stack-name mocknest-serverless --tail

# View CloudFormation events
aws cloudformation describe-stack-events \
  --stack-name mocknest-serverless \
  --query 'StackEvents[0:10].[Timestamp,ResourceStatus,ResourceType,LogicalResourceId]' \
  --output table
```

## Clean Up

To remove all resources:

```bash
sam delete --stack-name mocknest-serverless
```

**Note**: This will delete the S3 bucket and all stored mock definitions. Make sure to backup any important mocks before deletion.