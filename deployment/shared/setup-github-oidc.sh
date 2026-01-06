#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Setting up GitHub Actions OIDC for MockNest Serverless${NC}"
echo ""

# Get parameters
read -p "Enter your GitHub organization/username: " GITHUB_ORG
read -p "Enter your GitHub repository name [mocknest-serverless]: " GITHUB_REPO
GITHUB_REPO=${GITHUB_REPO:-mocknest-serverless}

read -p "Enter AWS region [eu-west-1]: " AWS_REGION
AWS_REGION=${AWS_REGION:-eu-west-1}

STACK_NAME="mocknest-github-oidc"

echo ""
echo -e "${YELLOW}📋 Configuration:${NC}"
echo "  GitHub Org/User: $GITHUB_ORG"
echo "  GitHub Repository: $GITHUB_REPO"
echo "  AWS Region: $AWS_REGION"
echo "  Stack Name: $STACK_NAME"
echo ""

read -p "Continue with deployment? (y/N): " CONFIRM
if [[ ! $CONFIRM =~ ^[Yy]$ ]]; then
    echo "Deployment cancelled."
    exit 0
fi

echo ""
echo -e "${BLUE}🔧 Deploying OIDC CloudFormation stack...${NC}"

# Deploy the CloudFormation stack
aws cloudformation deploy \
    --template-file github-oidc-role.yaml \
    --stack-name "$STACK_NAME" \
    --parameter-overrides \
        GitHubOrg="$GITHUB_ORG" \
        GitHubRepo="$GITHUB_REPO" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region "$AWS_REGION"

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ OIDC setup completed successfully!${NC}"
    echo ""
    
    # Get outputs
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    ROLE_ARN=$(aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --region "$AWS_REGION" \
        --query 'Stacks[0].Outputs[?OutputKey==`GitHubActionsRoleArn`].OutputValue' \
        --output text)
    
    echo -e "${YELLOW}📝 Next Steps:${NC}"
    echo ""
    echo "1. Add this secret to your GitHub repository settings:"
    echo "   Secret Name: AWS_ACCOUNT_ID"
    echo "   Secret Value: $ACCOUNT_ID"
    echo ""
    echo "2. Your GitHub Actions workflows will use this role:"
    echo "   Role ARN: $ROLE_ARN"
    echo ""
    echo "3. The workflows are already configured to use OIDC authentication."
    echo ""
    echo -e "${GREEN}🎉 You're ready to deploy MockNest Serverless via GitHub Actions!${NC}"
    echo ""
    echo -e "${BLUE}💡 To deploy manually:${NC}"
    echo "   cd deployment/sam"
    echo "   ./deploy.sh default"
    echo ""
else
    echo -e "${RED}❌ OIDC setup failed. Check the error messages above.${NC}"
    exit 1
fi