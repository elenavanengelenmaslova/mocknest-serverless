# MockNest Serverless AWS Deployment

This directory contains the AWS SAM template and deployment scripts for MockNest Serverless.

## Deployment Options

Following the [AWS Lambda Power Tuning](https://github.com/alexcasalboni/aws-lambda-power-tuning) approach, there are multiple ways to deploy MockNest Serverless:

### Option 1: AWS Serverless Application Repository (SAR) - Recommended for Users

The easiest way to deploy MockNest Serverless is via the AWS Serverless Application Repository (SAR). Once published, users can deploy with just a few clicks in the AWS Management Console.

**Deploy from SAR:**
1. Go to [AWS Serverless Application Repository](https://console.aws.amazon.com/serverlessrepo/home)
2. Search for "mocknest-serverless"
3. Click "Deploy" and configure parameters
4. Deploy with one click

**Deploy SAR app via CloudFormation:**
```bash
cd deployment/aws
chmod +x scripts/deploy-sar-app.sh
./scripts/deploy-sar-app.sh
```

You can also integrate the SAR app in your existing CloudFormation stacks - check `scripts/deploy-sar-app.yml` for a sample implementation.

### Option 2: AWS SAM CLI - For Development

Direct deployment using SAM CLI (what we use for development):

```bash
cd deployment/aws
chmod +x scripts/deploy.sh
./scripts/deploy.sh default  # or staging
```

## For Contributors: Setting Up AWS Deployment

### Prerequisites

1. **AWS Account** with appropriate permissions
2. **AWS CLI** configured with your credentials
3. **SAM CLI** installed ([Installation Guide](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html))

### GitHub Actions OIDC Setup (Recommended)

For secure, keyless authentication with GitHub Actions, you'll need to set up an OIDC provider and role in your AWS account.

#### Option 1: Use Existing OIDC Role

If you already have a GitHub Actions OIDC role for other repositories:

1. **Update your existing role** to include this repository:
   ```bash
   cd deployment/aws
   chmod +x scripts/update-existing-oidc-role.sh
   ./scripts/update-existing-oidc-role.sh
   ```

2. **Update workflow configuration**:
   - Fork this repository
   - In your fork, edit the workflow files to use your role name:
     - `.github/workflows/main-aws.yml`
     - `.github/workflows/deploy-on-demand.yml`
   - Change `github-actions-role-name: 'GitHubActionsRole'` to your actual role name

#### Option 2: Create New OIDC Role

If you need to create a new OIDC role:

1. **Deploy the OIDC role**:
   ```bash
   cd deployment/aws
   chmod +x scripts/setup-github-oidc.sh
   ./scripts/setup-github-oidc.sh
   ```

2. **Follow the setup instructions** provided by the script

#### Option 3: Learn About OIDC

New to GitHub Actions OIDC? Check out these resources:
- [AWS Guide: Configuring OpenID Connect in Amazon Web Services](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services)
- [AWS Blog: Use IAM roles to connect GitHub Actions to actions in AWS](https://aws.amazon.com/blogs/security/use-iam-roles-to-connect-github-actions-to-actions-in-aws/)

### Repository Secrets

Add this secret to your GitHub repository (Settings > Secrets and variables > Actions):

- `AWS_ACCOUNT_ID`: Your 12-digit AWS account ID

### Deployment Options

#### 1. Automatic Deployment (Main Branch)

Deployments to the **staging** environment happen automatically when you merge to the `main` branch. This provides a stable testing environment for integration testing and CI/CD validation.

#### 2. On-Demand Deployment (Any Branch)

Deploy from any branch using the manual workflow:

1. Go to your repository's **Actions** tab
2. Select **"Deploy On Demand"** workflow
3. Click **"Run workflow"**
3. Choose your deployment parameters:
   - **Environment**: `default` or `staging`
   - **Enable AI**: Whether to enable Bedrock AI features
   - **AWS Region**: Target deployment region
   - **Role Name**: Your GitHub Actions OIDC role name

#### 3. Local Deployment

For local testing or manual deployment:

```bash
# Configure AWS credentials first
aws configure

# Deploy
cd deployment/aws
chmod +x scripts/deploy.sh
./scripts/deploy.sh default  # or staging
```

## Deployment Environments

| Environment | Description | AI Features | Use Case |
|-------------|-------------|-------------|----------|
| **default** | Development/testing | Configurable | Local development, feature testing |
| **staging** | Stable testing environment | Configurable | Integration testing, CI/CD validation |

**Note**: Customers deploying via SAR can configure their own environments and parameters. The AI features and other settings are fully configurable through SAM parameters when deploying.

## Architecture Components

The SAM template creates these AWS resources (matching the Terraform CDK structure):

### Core Infrastructure
- **S3 Bucket**: Mock storage with unique naming (`mocknest-{env}-{account}-{region}-{suffix}`)
- **Lambda Function**: MockNest runtime with optimized JVM settings
- **IAM Role**: Least-privilege permissions for Lambda execution

### API Gateway Setup
- **REST API**: Regional endpoint with comprehensive logging
- **Usage Plan**: Rate limiting and quota management
- **API Key**: Authentication for all endpoints
- **Lambda Integration**: Proxy integration for all paths

### Monitoring & Logging
- **CloudWatch Log Groups**: Separate logs for API Gateway and Lambda
- **Access Logging**: Detailed request/response logging
- **Dead Letter Queue**: Failed invocation handling

### AI Features (Production Only)
- **Bedrock Permissions**: Access to foundation models when AI is enabled
- **Regional Restrictions**: AI features limited to deployment region

## Testing Your Deployment

After deployment, test your MockNest instance:

1. **Get your API key**:
   ```bash
   aws cloudformation describe-stacks \
     --stack-name mocknest-serverless-dev \
     --query 'Stacks[0].Outputs[?OutputKey==`MockNestApiKey`].OutputValue' \
     --output text
   ```

2. **Test the admin API**:
   ```bash
   curl -H "x-api-key: YOUR_API_KEY" \
        -X GET "https://your-api-id.execute-api.eu-west-1.amazonaws.com/prod/__admin/mappings"
   ```

   Expected response: `{"mappings": []}` (empty array for a fresh deployment)

3. **Import Postman collection** from `docs/postman/` for comprehensive testing

## Cost Considerations

The deployment is designed to stay within AWS Free Tier limits:

- **Lambda**: 1M requests/month, 400K GB-seconds
- **API Gateway**: 1M API calls/month  
- **S3**: 5GB storage, 20K GET requests, 2K PUT requests
- **CloudWatch**: 5GB logs, 10 custom metrics

**Note**: AI features (Bedrock) are pay-per-use and not included in Free Tier.

## Troubleshooting

### Common Issues

- **Build failures**: Ensure Java 25 and Gradle 9.0 are installed
- **Permission errors**: Check your OIDC role has the necessary permissions
- **Stack conflicts**: Use different stack names for different environments
- **API key issues**: Retrieve the key from CloudFormation outputs

### Getting Help

- Check the [main README](../../README.md) for general project information
- Review [CONTRIBUTING.md](../../CONTRIBUTING.md) for development guidelines
- Open an issue on GitHub for deployment-specific problems