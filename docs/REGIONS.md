# Deployment Regions

MockNest Serverless can be deployed to any AWS region that supports Lambda, API Gateway, and S3. However, AI-assisted mock generation features have specific regional requirements.

## Tested Regions

The following regions have been fully tested with MockNest Serverless, including AI features:

- **us-east-1** (N. Virginia)
- **eu-west-1** (Ireland)
- **eu-central-1** (Frankfurt)

## AI Features and Regional Availability

### Current Testing Status

AI-assisted mock generation has been tested with **Amazon Nova Pro** in the three regions listed above. Additional regions and models will be tested in future releases.

### Model Availability Requirements

To use AI-assisted mock generation features, the selected AI model must be available in your deployment region.

**Amazon Nova Pro Availability:**
- Check the [AWS Bedrock model availability documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-regions.html) for current regional availability
- Amazon Nova Pro is ready to use immediately in supported regions (no additional setup required)

**Other Models:**
- If using third-party models (e.g., Anthropic Claude), you may need to:
  - Enable model access in the Amazon Bedrock console
  - Accept terms and conditions
  - Verify the model is available in your chosen region

### Configuring AI Models

MockNest Serverless uses Amazon Nova Pro as the default model, but you can configure a different model during deployment:

```bash
sam deploy --parameter-overrides BedrockModelName=YourModelName
```

**Note**: Only Amazon Nova Pro has been officially tested. Other models may work but are experimental and not officially supported.

## Deploying to Other Regions

You can deploy MockNest Serverless to any AWS region, but be aware of the following:

### Core Runtime (No AI Features)

The core mock runtime (serving mocks, admin API) works in any region with:
- AWS Lambda
- Amazon API Gateway
- Amazon S3

These services are available in all standard AWS regions.

### With AI Features

If you want to use AI-assisted mock generation in a region not listed above:

1. **Verify Model Availability**: Check that your chosen AI model is available in the target region
2. **Test Thoroughly**: AI features have not been tested in regions outside the three listed above
3. **Report Issues**: If you encounter problems, please report them via [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues)

## Future Plans

We plan to expand testing to additional regions based on user demand and AWS service availability. Regions under consideration include:

- **us-west-2** (Oregon)
- **ap-southeast-1** (Singapore)
- **ap-northeast-1** (Tokyo)
- Additional EU regions

If you need support for a specific region, please open a feature request in [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues).

## Region Selection Best Practices

When choosing a deployment region, consider:

1. **Proximity**: Deploy close to your development team or test environments for lower latency
2. **AI Model Availability**: Ensure your chosen AI model is available if using AI features
3. **Data Residency**: Consider compliance and data residency requirements
4. **Cost**: Some regions have different pricing (though differences are typically minimal)

## Getting Help

If you experience issues deploying to a specific region:

1. Check the [Troubleshooting Guide](TROUBLESHOOTING.md)
2. Verify service availability in your region
3. Review CloudFormation deployment logs
4. Report issues via [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues)
