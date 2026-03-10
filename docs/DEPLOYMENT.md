# MockNest Serverless Deployment Guide

This guide covers deploying MockNest Serverless using AWS Serverless Application Repository (SAR) or direct SAM deployment.

## AWS Serverless Application Repository (SAR)

### One-Click Deployment from AWS Console
1. **Open AWS Console** in your preferred region
2. **Navigate to** AWS Serverless Application Repository
3. **Search for** "MockNest-Serverless"
4. **Click Deploy** and configure parameters:
   - **DeploymentName**: `mocks` (default) - deployment instance identifier
   - **BedrockModelName**: `AmazonNovaPro` (default) - officially supported model
   - **BedrockInferenceMode**: `AUTO` (default) - automatic inference profile selection
   - **BucketName**: Leave empty for auto-generated name
   - **LambdaMemorySize**: `1024` (default) - adjust for performance needs
   - **LambdaTimeout**: `120` (default) - adjust for complex operations
5. **Deploy** - takes 2-3 minutes

### Deploy SAR App via Script
For automated deployments or CI/CD integration:

```bash
cd deployment/aws/sar

# Set the SAR application ID (get this after publishing to SAR)
export SAR_APP_ID="arn:aws:serverlessrepo:us-east-1:YOUR_ACCOUNT_ID:applications/MockNest-Serverless"

# Deploy with default parameters
./deploy-sar-app.sh

# Or deploy with custom parameters
DEPLOYMENT_NAME="test-mocks" \
BEDROCK_MODEL_NAME="AmazonNovaLite" \
LAMBDA_MEMORY_SIZE="2048" \
./deploy-sar-app.sh
```

### SAR Benefits
- ✅ **No local setup required** - deploy directly from AWS Console
- ✅ **Always latest version** - automatically updated
- ✅ **Deploy in any region** - uses your current console region
- ✅ **Parameter validation** - guided configuration
- ✅ **CloudFormation integration** - full stack management
- ✅ **Automatic region detection** - no manual region configuration needed

## Direct SAM Deployment (Development)

### Source-Based Deployment
```bash
# Deploy with defaults (to current AWS region)
./gradlew build
cd deployment/aws/sam
sam build
sam deploy --guided  # First time only
```

> **🌍 Region Selection**: 
> - **SAR**: Automatically deploys to your current AWS Console region
> - **Direct SAM**: Uses your current AWS CLI region or specify with --region

**Or for subsequent deployments:**
```bash
sam build && sam deploy
```

**Default Configuration:**
- **Region**: Uses AWS CLI default region or AWS_REGION environment variable
- **S3 Bucket**: Auto-generated unique name (`mocknest-serverless-{stack-name}`)
- **AI Features**: Enabled by default with automatic inference profile selection
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

## SAR Publishing (Maintainers Only)

### Pipeline-Based SAR Publishing

MockNest uses GitHub Actions workflows for SAR publishing with comprehensive testing:

#### Private SAR Testing Pipeline
**Purpose**: Test SAR deployment across multiple regions before public release

**Workflow**: `.github/workflows/sar-deploy.yml`

**Features**:
- Multi-region testing (us-east-1, eu-west-1, ap-southeast-1)
- Comprehensive functionality validation
- Private SAR sharing with test accounts
- Automatic cleanup after testing

**To Run**:
1. Go to **Actions** → **Private SAR Deployment and Testing**
2. **Run workflow** with:
   - Version: `0.2.0-beta.1` (beta version)
   - Test Account IDs: comma-separated AWS account IDs
   - Role Name: `GitHubOIDCAdmin` (your OIDC role)

#### Public SAR Publishing Pipeline
**Purpose**: Publish to public SAR after private testing passes

**Workflow**: `.github/workflows/publish-sar.yml`

**Features**:
- Direct public SAR publishing
- Automatic version handling
- GitHub release integration

**To Run**:
1. Go to **Actions** → **Publish to SAR**
2. **Run workflow** with:
   - Version: `0.2.0` (public version)
   - Region: `us-east-1` (recommended for public SAR)

#### Prerequisites for SAR Publishing
- GitHub OIDC role with SAR permissions
- GitHub secrets: `AWS_ACCOUNT_ID`, `TEST_AWS_ACCOUNT_ID`
- AWS account with Serverless Application Repository access

#### SAR Publishing Process
1. **Private Testing**: Run private SAR pipeline to validate across regions
2. **Review Results**: Ensure all tests pass in all regions
3. **Public Release**: Run public SAR pipeline to make application available
4. **Verification**: Confirm application appears in public SAR catalog

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

The application automatically detects the deployment region and configures all services accordingly.

### AI Features Region Support

Ensure your chosen region supports Amazon Bedrock for AI-powered mock generation:

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

**Note**: MockNest automatically selects the optimal Bedrock inference profile based on your deployment region. No manual configuration required.

### Inference Profile Selection

MockNest uses intelligent inference profile selection:
- **AUTO mode** (default): Tries region-specific profile first, then global
- **GLOBAL_ONLY mode**: Forces global inference profile only
- **GEO_ONLY mode**: Forces region-specific inference profile only

The system automatically derives the geo prefix from your deployment region:
- `us-*` regions → `us` prefix
- `eu-*` regions → `eu` prefix  
- `ap-*` regions → `ap` prefix
- `ca-*` regions → `ca` prefix

## Custom Configuration

### Custom S3 Bucket Name

```bash
sam deploy --parameter-overrides BucketName=my-mocknest-storage-bucket
```

### Bedrock Model Selection

```bash
sam deploy --parameter-overrides BedrockModelName=AmazonNovaLite
```

### Inference Mode Configuration

```bash
# Force global inference profile only
sam deploy --parameter-overrides BedrockInferenceMode=GLOBAL_ONLY

# Force region-specific inference profile only  
sam deploy --parameter-overrides BedrockInferenceMode=GEO_ONLY
```

### Performance Tuning

```bash
# Increase Lambda memory for better performance
sam deploy --parameter-overrides LambdaMemorySize=2048

# Increase timeout for complex operations
sam deploy --parameter-overrides LambdaTimeout=300
```

### Combined Configuration

```bash
sam deploy \
  --region eu-west-1 \
  --parameter-overrides \
    DeploymentName=test-mocks \
    BedrockModelName=AmazonNovaLite \
    BedrockInferenceMode=AUTO \
    LambdaMemorySize=2048 \
    BucketName=my-custom-bucket-name
```

## Post-Deployment

### Get Deployment Information

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

## SAR vs Direct Deployment Comparison

| Feature | SAR Deployment | Direct SAM Deployment |
|---------|----------------|----------------------|
| **Setup Required** | None | AWS CLI, SAM CLI, Java, Gradle |
| **Deployment Time** | 2-3 minutes | 5-10 minutes (including build) |
| **Customization** | Parameter-based | Full source code access |
| **Updates** | Automatic via SAR | Manual git pull + redeploy |
| **Use Case** | Production, quick testing | Development, customization |
| **Region** | Any (via console region) | Configurable via SAM |
| **Publishing** | GitHub Actions pipeline | Not applicable |

**Recommendation**: Use SAR for production deployments, Direct SAM for development and customization.

## Troubleshooting

### Common Issues

1. **Region Mismatch**: Ensure all resources are in the same region
2. **Bedrock Not Available**: If using AI features, verify Bedrock is available in your region
3. **Bucket Name Conflicts**: S3 bucket names must be globally unique
4. **SAR Permissions**: Ensure your AWS account has SAR access for publishing

### Logs and Monitoring

```bash
# View Lambda logs
sam logs -n MockNestRuntimeFunction --stack-name mocknest-serverless --tail

# View CloudFormation events
aws cloudformation describe-stack-events \
  --stack-name mocknest-serverless \
  --query 'StackEvents[0:10].[Timestamp,ResourceStatus,ResourceType,LogicalResourceId]' \
  --output table
```

### Pipeline Troubleshooting

**Private SAR Testing Issues**:
- Check GitHub OIDC role has SAR permissions
- Verify `TEST_AWS_ACCOUNT_ID` secret is set
- Ensure Bedrock is available in test regions

**Public SAR Publishing Issues**:
- Verify private testing passed first
- Check SAR application appears in us-east-1
- Confirm application policy allows public access

## Clean Up

To remove all resources:

```bash
sam delete --stack-name mocknest-serverless
```

**Note**: This will delete the S3 bucket and all stored mock definitions. Make sure to backup any important mocks before deletion.