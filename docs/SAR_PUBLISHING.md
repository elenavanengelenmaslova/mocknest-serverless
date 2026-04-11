# AWS Serverless Application Repository (SAR) Publishing Guide

This guide covers how to publish MockNest Serverless to the AWS Serverless Application Repository, following the model of successful open-source SAR applications like AWS Lambda Power Tuner.

## Overview

MockNest Serverless will be published as a public SAR application, allowing users to deploy it with one click from the AWS Console without requiring local development tools.

## SAR Publishing via GitHub Actions

MockNest uses automated GitHub Actions workflows for SAR publishing with comprehensive multi-region testing.

### SAR Publish Pipeline

**Purpose**: Publish to public SAR and validate the deployment

**Workflow**: `.github/workflows/sar-publish.yml`

**Features**:
- Direct public SAR publishing
- Automatic version handling
- GitHub release integration
- Dual auth mode validation: API key and IAM modes tested independently
- Both validation runs must pass before release is considered successful

**To Run**:
1. Go to **Actions** → **CD - SAR Publish**
2. **Run workflow** with:
   - Version: `<current-version>` (from latest git tag)
   - Region: `eu-west-1`

### Prerequisites for SAR Publishing
- GitHub OIDC role with SAR permissions
- GitHub secrets: `AWS_ACCOUNT_ID`, `TEST_AWS_ACCOUNT_ID`
- AWS account with Serverless Application Repository access
- GitHub OIDC role (`GitHubOIDCAdmin`) must have `execute-api:Invoke` permission on deployed APIs (required for IAM auth mode validation)

### SAR Publishing Process
1. **Public Release**: Run the SAR Publish pipeline to make the application available
2. **Verification**: Confirm application appears in public SAR catalog

> **Note**: Every SAR release is validated in both API key mode and IAM mode as independent runs. Both must pass before a release is considered successful. See `.github/workflows/sar-publish.yml` for the pipeline configuration.

## SAR Application Metadata

### Application Information
- **Name**: MockNest Serverless
- **Description**: AWS-native serverless mock runtime for integration testing
- **Author**: MockNest Team
- **License**: MIT
- **Source Code URL**: https://github.com/elenavanengelenmaslova/mocknest-serverless
- **README URL**: https://github.com/elenavanengelenmaslova/mocknest-serverless/blob/main/README.md

### Labels/Tags
- `testing`
- `mocking`
- `integration-testing`
- `wiremock`
- `serverless`
- `api-testing`
- `lambda`

## SAR Template Requirements

### Template Metadata
The SAM template must include SAR-specific metadata:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: MockNest Serverless - AWS-native serverless mock runtime

Metadata:
  AWS::ServerlessRepo::Application:
    Name: MockNest-Serverless
    Description: AWS-native serverless mock runtime for integration testing with WireMock
    Author: MockNest Team
    SpdxLicenseId: MIT
    LicenseUrl: LICENSE
    ReadmeUrl: README.md
    Labels: ['testing', 'mocking', 'integration-testing', 'wiremock', 'serverless']
    HomePageUrl: https://github.com/your-org/mocknest-serverless
    SemanticVersion: 1.0.0
    SourceCodeUrl: https://github.com/your-org/mocknest-serverless
```

### Parameter Descriptions
All parameters must have clear descriptions for the SAR UI:

```yaml
  BucketName:
    Type: String
    Default: ''
    Description: |
      Custom S3 bucket name for mock storage (optional).
      Leave empty to auto-generate a unique bucket name.
```

## Publishing Process

MockNest uses **automated GitHub Actions workflows** for SAR publishing. Manual publishing is no longer recommended.

### Automated Publishing (Recommended)

#### 1. Public Release

Publish to public SAR:

```bash
# Via GitHub Actions UI:
# 1. Go to Actions → "CD - SAR Publish"
# 2. Click "Run workflow"
# 3. Enter:
#    - Version: <current-version> (from latest git tag)
#    - Region: eu-west-1 (or your preferred region)
```

**What it does**:
- Builds and packages the application
- Publishes to public SAR
- Makes application available in AWS Serverless Application Repository
- Validates deployment in both API key and IAM auth modes


## Regional Considerations

### Global vs Regional Applications
- **Global Application**: Published in eu-west-1, available in all regions
- **Regional Application**: Published per region, only available in that region

**Recommendation**: Publish as Global Application for maximum reach.

### Region-Specific Features
- Document Bedrock availability per region
- Provide clear guidance on AI feature limitations
- Test deployment in multiple regions

## Best Practices

### SAR Application Guidelines
1. **Clear Documentation**: Comprehensive README with examples
2. **Parameter Validation**: Use AllowedValues and clear descriptions
3. **Sensible Defaults**: Enable one-click deployment
4. **Open Source**: Full source code available on GitHub
5. **Community Engagement**: Respond to issues and contributions

### SAR-Specific Guidelines
1. **Template Size**: Keep under 1MB (use nested stacks if needed)
2. **Parameter Limits**: Maximum 60 parameters
3. **Clear Descriptions**: All resources and parameters documented
4. **Version Management**: Use semantic versioning
5. **Testing**: Test in multiple regions before publishing

## Maintenance

### Regular Updates
- Monitor for AWS service updates
- Update dependencies regularly
- Test with new AWS regions as they become available
- Respond to community feedback and issues

### Metrics and Monitoring
- Track SAR deployment metrics
- Monitor GitHub issues and discussions
- Collect user feedback for improvements