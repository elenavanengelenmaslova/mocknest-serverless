# Performance Guide

## Memory Configuration

Both Runtime and Generation functions default to **1024 MB** based on AWS Lambda Power Tuner analysis. This configuration provides optimal cost/performance balance for typical workloads.

**TODO**: Add Lambda Power Tuner visualization showing the cost/performance tradeoff analysis.

## Expected Performance

Performance characteristics with SnapStart enabled at 1024 MB:

| Mock Count | Cold Start | Warm Execution |
|------------|------------|----------------|
| 10 mocks   | TBD ms     | TBD ms         |
| 100 mocks  | TBD ms     | TBD ms         |
| 1000 mocks | TBD ms     | TBD ms         |

**Note**: Response payload size does not affect cold start times or memory usage because payloads are stored separately in S3 and streamed on demand.

## Tuning Your Deployment

To optimize memory configuration for your specific workload:

1. Deploy [AWS Lambda Power Tuning](https://serverlessrepo.aws.amazon.com/applications/arn:aws:serverlessrepo:us-east-1:451282441545:applications~aws-lambda-power-tuning) from SAR
2. Run the Step Functions state machine with your function ARN
3. Review the visualization to identify optimal memory setting
4. Update `LambdaMemorySize` parameter in your SAM deployment

The Power Tuner tests memory configurations from 512 MB to 3008 MB and provides a cost vs. performance visualization to help you choose the best setting for your use case.
