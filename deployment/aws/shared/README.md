# GitHub Actions OIDC Role Setup

This directory contains the CloudFormation template and helper scripts for configuring GitHub Actions OIDC-based authentication with AWS for MockNest Serverless deployments.

## Overview

MockNest Serverless uses a two-role pattern:

- **GitHubActionsRole** — assumed by GitHub Actions via OIDC. Has minimal permissions to orchestrate SAM deployments (CloudFormation operations, scoped S3 access for artifacts, `iam:PassRole` to the execution role).
- **MockNestCloudFormationExecutionRole** — assumed by CloudFormation (via `--role-arn`) to create actual stack resources (Lambda, API Gateway, S3, SQS, IAM roles, log groups, Bedrock access).

---

## Quick Start (New Setup)

### 1. Deploy the OIDC stack

```bash
cd deployment/aws/shared
./setup-github-oidc.sh
```

The script prompts for your GitHub org/username, repository name, and AWS region. It deploys `github-oidc-role.yaml` which creates:
- The OIDC identity provider
- The GitHubActionsRole (deployment role)
- The MockNestCloudFormationExecutionRole (execution role)

### 2. Add GitHub secrets and variables

The script outputs your AWS Account ID and role name. Add them to your repository:

- **Secret** (Settings → Secrets and variables → Actions → Secrets):
  - Name: `AWS_ACCOUNT_ID`
  - Value: your 12-digit AWS account ID

- **Variable** (Settings → Secrets and variables → Actions → Variables):
  - Name: `OIDC_ROLE_NAME`
  - Value: `GitHubActionsRole` (or whatever role name you chose)

### 3. Configure GitHub Environments

Go to your repository → Settings → Environments and create:

| Environment | Used by | Recommended protections |
|-------------|---------|------------------------|
| `production` | `workflow-deploy-aws.yml` (via `main-aws.yml`) | Required reviewers, branch restriction to `main` |
| `sar-publish` | `workflow-sar-publish.yml` | Required reviewers, branch restriction to `main` |

### 4. Push to main

The `main-aws.yml` workflow will:
- Assume the `GitHubActionsRole` via OIDC
- Run `sam deploy` with the CloudFormation execution role
- Create all MockNest Serverless resources

### 5. Verify

```bash
./scripts/check-oidc-policy.sh
```

Exit code 0 confirms the configuration follows security best practices.

---

## Migration from the Old Permissive Role

If you previously deployed the old template (which used `StringLike`, `PowerUserAccess`, and wildcard permissions):

### 1. Deploy the updated template

```bash
cd deployment/aws/shared

aws cloudformation deploy \
    --template-file github-oidc-role.yaml \
    --stack-name mocknest-github-oidc \
    --parameter-overrides \
        GitHubOrg="YOUR_GITHUB_ORG" \
        GitHubRepo="YOUR_GITHUB_REPO" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region eu-west-1
```

This updates the stack in-place — hardens the trust policy, removes `PowerUserAccess`, and creates the execution role.

### 2. Pass the execution role to SAM deploy

Your `sam deploy` command needs `--role-arn` so CloudFormation uses the execution role:

```bash
sam deploy --role-arn <MockNestCloudFormationExecutionRoleArn>
```

Get the ARN from the stack outputs:

```bash
aws cloudformation describe-stacks \
    --stack-name mocknest-github-oidc \
    --query 'Stacks[0].Outputs[?OutputKey==`MockNestCloudFormationExecutionRoleArn`].OutputValue' \
    --output text
```

### 3. Configure GitHub Environments

See Step 3 in Quick Start above.

### 4. Verify

```bash
./scripts/check-oidc-policy.sh
```

### 5. Test a deployment

Push to `main` and confirm the workflow deploys successfully with the new two-role pattern.

---

## Updating an Existing Role's Trust Policy

If you only need to harden the trust policy on an existing role (without redeploying the full stack):

```bash
cd deployment/aws/shared
./update-existing-oidc-role.sh
```

This interactively updates the trust policy to use `StringEquals` on `main` only.

---

## Files

| File | Purpose |
|------|---------|
| `github-oidc-role.yaml` | CloudFormation template (OIDC provider + both IAM roles) |
| `setup-github-oidc.sh` | Interactive first-time setup script |
| `update-existing-oidc-role.sh` | Updates an existing role's trust policy |
| `README.md` | This file |

## Related Files

| File | Purpose |
|------|---------|
| `scripts/check-oidc-policy.sh` | Regression check — validates no permissive patterns |
| `.github/workflows/workflow-deploy-aws.yml` | Reusable deployment workflow |
| `.github/workflows/workflow-sar-publish.yml` | SAR publishing workflow |
