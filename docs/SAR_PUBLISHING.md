# AWS Serverless Application Repository (SAR) Publishing Guide

This guide covers how to publish MockNest Serverless to the AWS Serverless Application Repository, following the model of successful open-source SAR applications like AWS Lambda Power Tuner.

## Overview

MockNest Serverless will be published as a public SAR application, allowing users to deploy it with one click from the AWS Console without requiring local development tools.

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
Parameters:
  EnableAI:
    Type: String
    Default: 'false'
    AllowedValues: ['true', 'false']
    Description: |
      Enable AI-assisted mock generation features using Amazon Bedrock.
      Note: Requires Bedrock-supported region and incurs additional costs.
  
  BucketName:
    Type: String
    Default: ''
    Description: |
      Custom S3 bucket name for mock storage (optional).
      Leave empty to auto-generate a unique bucket name.
```

## Publishing Process

### 1. Prepare Release
```bash
# Ensure all tests pass
./gradlew test

# Build the application
./gradlew build

# Validate SAM template
sam validate --template deployment/sam/template.yaml

# Test deployment locally
sam build
sam deploy --guided
```

### 2. Create Release Package
```bash
# Package for SAR
sam package \
  --template-file deployment/sam/template.yaml \
  --s3-bucket your-sar-artifacts-bucket \
  --s3-prefix mocknest-serverless \
  --output-template-file packaged-template.yaml
```

### 3. Publish to SAR
```bash
# Publish to SAR
sam publish \
  --template packaged-template.yaml \
  --region us-east-1  # SAR requires us-east-1 for global apps
```

### 4. Update Application
For subsequent releases:
```bash
# Update semantic version in template.yaml metadata
# Then republish
sam publish --template packaged-template.yaml --region us-east-1
```

## Regional Considerations

### Global vs Regional Applications
- **Global Application**: Published in us-east-1, available in all regions
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