#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 Updating existing GitHub Actions OIDC role for MockNest Serverless${NC}"
echo ""

# Get parameters
read -p "Enter your existing GitHub Actions role name: " ROLE_NAME
read -p "Enter your GitHub organization/username: " GITHUB_ORG
read -p "Enter your GitHub repository name [mocknest-serverless]: " GITHUB_REPO
GITHUB_REPO=${GITHUB_REPO:-mocknest-serverless}

read -p "Enter AWS region [eu-west-1]: " AWS_REGION
AWS_REGION=${AWS_REGION:-eu-west-1}

echo ""
echo -e "${YELLOW}📋 Configuration:${NC}"
echo "  Role Name: $ROLE_NAME"
echo "  GitHub Org/User: $GITHUB_ORG"
echo "  GitHub Repository: $GITHUB_REPO"
echo "  AWS Region: $AWS_REGION"
echo ""

# Get current trust policy
echo -e "${BLUE}📖 Getting current trust policy...${NC}"
CURRENT_POLICY=$(aws iam get-role --role-name "$ROLE_NAME" --query 'Role.AssumeRolePolicyDocument' --output json)

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to get role. Please check the role name and your AWS permissions.${NC}"
    exit 1
fi

echo "Current trust policy retrieved."

# Create updated trust policy
echo -e "${BLUE}🔄 Creating updated trust policy...${NC}"

# Create a temporary file for the new policy
TEMP_POLICY=$(mktemp)

cat > "$TEMP_POLICY" << EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": [
            "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main",
            "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/feature/*",
            "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/bugfix/*",
            "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/hotfix/*",
            "repo:${GITHUB_ORG}/${GITHUB_REPO}:pull_request"
          ]
        }
      }
    }
  ]
}
EOF

echo ""
echo -e "${YELLOW}⚠️  This will update the trust policy for role: $ROLE_NAME${NC}"
echo -e "${YELLOW}   The new policy will REPLACE the existing one.${NC}"
echo ""
echo "New trust policy:"
cat "$TEMP_POLICY" | jq '.'
echo ""

read -p "Continue with the update? (y/N): " CONFIRM
if [[ ! $CONFIRM =~ ^[Yy]$ ]]; then
    echo "Update cancelled."
    rm "$TEMP_POLICY"
    exit 0
fi

# Update the role trust policy
echo -e "${BLUE}🔄 Updating role trust policy...${NC}"
aws iam update-assume-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-document "file://$TEMP_POLICY"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Role trust policy updated successfully!${NC}"
    
    # Get role ARN
    ROLE_ARN=$(aws iam get-role --role-name "$ROLE_NAME" --query 'Role.Arn' --output text)
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    
    echo ""
    echo -e "${YELLOW}📝 Configuration for GitHub Actions:${NC}"
    echo ""
    echo "1. Make sure this secret exists in your GitHub repository:"
    echo "   Secret Name: AWS_ACCOUNT_ID"
    echo "   Secret Value: $ACCOUNT_ID"
    echo ""
    echo "2. Your workflows will use this role ARN:"
    echo "   $ROLE_ARN"
    echo ""
    echo -e "${GREEN}🎉 Your existing OIDC role is now configured for MockNest Serverless!${NC}"
    echo ""
    echo -e "${BLUE}💡 To deploy:${NC}"
    echo "   cd deployment/sam"
    echo "   sam deploy --guided"
    echo ""
else
    echo -e "${RED}❌ Failed to update role trust policy.${NC}"
    rm "$TEMP_POLICY"
    exit 1
fi

# Clean up
rm "$TEMP_POLICY"