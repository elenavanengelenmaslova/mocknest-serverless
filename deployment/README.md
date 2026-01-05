# MockNest Serverless Deployment Options

Following the [AWS Lambda Power Tuning](https://github.com/alexcasalboni/aws-lambda-power-tuning) approach, MockNest Serverless supports multiple deployment methods to suit different preferences and use cases.

## Available Deployment Methods

### ✅ [SAM (AWS Serverless Application Model)](./sam/)
**Status: Implemented and Tested**
- Direct deployment using AWS SAM CLI
- Best for development and testing
- Full control over deployment parameters
- Used by the development team

### ✅ [SAR (AWS Serverless Application Repository)](./sar/)
**Status: Implemented**
- One-click deployment from AWS Console
- Best for end users and quick deployments
- Can be integrated into existing CloudFormation stacks
- Recommended for most users

## Shared Resources

### [Shared Components](./shared/)
- GitHub Actions OIDC setup scripts
- Common CloudFormation templates
- Shared utilities and helpers

## Quick Start

### For End Users (Recommended)
Use the **SAR deployment** for the easiest experience:
```bash
cd deployment/sar
./deploy-sar-app.sh
```

### For Developers
Use the **SAM deployment** for development and testing:
```bash
cd deployment/sam
./deploy.sh default
```

## Choosing a Deployment Method

| Method | Best For | Complexity | Flexibility |
|--------|----------|------------|-------------|
| **SAR** | End users, quick deployment | Low | Medium |
| **SAM** | Development, testing | Medium | High |
| **AWS CDK** | CDK users, type safety | Medium | High |
| **Terraform CDK** | Terraform users, multi-cloud | High | Very High |

## Contributing

When adding new deployment methods:
1. Create a new folder under `deployment/`
2. Include a comprehensive README.md
3. Add deployment scripts and templates
4. Update this main README
5. Consider adding GitHub Actions workflows if needed