# Troubleshooting Guide

This guide helps you diagnose and resolve common issues with MockNest Serverless.

## Common Issues

### Region Mismatch
**Problem**: Resources are deployed in different AWS regions  
**Solution**: Ensure all AWS resources (Lambda, API Gateway, S3, Bedrock) are in the same region

### Permissions
**Problem**: Access denied errors when accessing S3 or Bedrock  
**Solution**: Verify IAM roles have necessary permissions:
- S3: `s3:GetObject`, `s3:PutObject`, `s3:ListBucket`
- Lambda: Execution role with CloudWatch Logs access
- Bedrock: `bedrock:InvokeModel` (if using AI features)

### Cold Starts
**Problem**: First requests are slow  
**Solution**: This is expected behavior with AWS Lambda. Subsequent requests will be faster. Consider:
- Using provisioned concurrency for production workloads
- Implementing health check warming strategies
- Accepting cold start latency for development/testing scenarios

## Logs and Monitoring

MockNest Serverless provides comprehensive logging through CloudWatch.

### Log Groups

**Log Groups Created:**
- `/aws/lambda/{stack-name}-runtime` - WireMock runtime and mock serving
- `/aws/lambda/{stack-name}-generation` - AI-assisted mock generation
- **Retention**: 7 days (configurable in SAM template)

### Viewing Logs via SAM CLI

```bash
# Runtime function logs
sam logs -n MockNestRuntimeFunction --stack-name mocknest-serverless --tail

# Generation function logs  
sam logs -n MockNestGenerationFunction --stack-name mocknest-serverless --tail
```

### Viewing Logs in AWS Console

1. Go to CloudWatch → Log groups
2. Find `/aws/lambda/mocknest-serverless-*` log groups
3. View recent log streams

**Note**: API Gateway access logs are disabled to simplify deployment. Lambda logs provide comprehensive application monitoring.

## Deployment Issues

### SAR Deployment Failures

**Problem**: CloudFormation stack creation fails  
**Solution**: Check CloudFormation events for detailed error messages:
1. Go to CloudFormation console
2. Find your stack
3. Check the "Events" tab for failure reasons

Common causes:
- Insufficient IAM permissions
- Resource limits exceeded
- Invalid parameter values

### Bedrock Access Denied

**Problem**: AI generation fails with access denied  
**Solution**: 
1. Ensure Amazon Bedrock is available in your deployment region
2. Enable model access in Amazon Bedrock console
3. Accept terms and conditions for the model (if required)
4. Verify IAM role has `bedrock:InvokeModel` permission

### API Key Issues

**Problem**: Cannot find or access API key  
**Solution**:
1. Go to API Gateway console
2. Navigate to "API Keys" section
3. Find your MockNest API key
4. Click "Show" to reveal the actual key value
5. Note: Deployment outputs show the key ID, not the actual key value

## Runtime Issues

### Mocks Not Persisting

**Problem**: Mocks disappear after Lambda cold start  
**Solution**: Ensure `"persistent": true` is set in your mapping definition:
```json
{
  "request": {...},
  "response": {...},
  "persistent": true
}
```

### Mock Not Matching Requests

**Problem**: Requests return 404 or don't match expected mocks  
**Solution**:
1. Verify the URL path matches exactly (including leading `/`)
2. Check request method (GET, POST, etc.)
3. Review matching criteria (headers, query parameters, body patterns)
4. Use `/__admin/requests` to see unmatched requests
5. Check CloudWatch logs for matching details

### AI Generation Timeouts

**Problem**: AI generation requests timeout  
**Solution**:
- Increase Lambda timeout in SAM template (default: 120 seconds)
- Simplify the generation request (fewer endpoints, simpler descriptions)
- Check Bedrock service limits and quotas

## Performance Issues

### Slow Response Times

**Problem**: Mocks respond slowly  
**Solution**:
1. Check Lambda memory allocation (increase if needed)
2. Review CloudWatch metrics for Lambda duration
3. Consider using provisioned concurrency for consistent performance
4. Verify S3 bucket is in the same region as Lambda

### High Costs

**Problem**: AWS costs higher than expected  
**Solution**:
1. Review [Cost Guide](COST.md) for optimization tips
2. Check CloudWatch metrics for request volume
3. Verify S3 lifecycle policies are configured
4. Monitor Bedrock usage (AI generation incurs separate costs)
5. Consider using multiple deployments to isolate costs

## Getting Help

If you're still experiencing issues:

1. **Check Documentation**: Review [README.md](../README.md), [USAGE.md](USAGE.md), and [README-SAR.md](../README-SAR.md)
2. **Search Issues**: Look for similar problems in [GitHub Issues](https://github.com/elenavanengelenmaslova/mocknest-serverless/issues)
3. **Report a Bug**: Create a new issue with:
   - Detailed description of the problem
   - Steps to reproduce
   - CloudWatch logs (if applicable)
   - SAM template parameters used
   - AWS region and Bedrock model configuration

## Additional Resources

- **OpenAPI Specification**: [docs/api/mocknest-openapi.yaml](api/mocknest-openapi.yaml)
- **Postman Collection**: [docs/postman/](postman/)
- **Architecture Documentation**: [.kiro/steering/](../.kiro/steering/)
