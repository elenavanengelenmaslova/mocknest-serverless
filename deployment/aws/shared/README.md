# GitHub Actions OIDC Role Setup

This directory contains the CloudFormation template and helper scripts for configuring GitHub Actions OIDC-based authentication with AWS for MockNest Serverless deployments.

## Overview

MockNest Serverless uses a two-role pattern:

- **GitHubActionsRole** — assumed by GitHub Actions via OIDC. Has minimal permissions to orchestrate SAM deployments (CloudFormation operations, object-level S3 access to the artifacts bucket, `iam:PassRole` to the execution role).
- **MockNestCloudFormationExecutionRole** — assumed by CloudFormation (via `--role-arn`) to create actual stack resources (Lambda, API Gateway, S3, SQS, IAM roles, log groups, Bedrock access).

This bootstrap stack also provisions the **SAM deployment artifacts bucket** (`MockNestArtifactsBucket`, named `mocknest-sam-artifacts-<account-id>-<region>`). SAM uploads packaged Lambda code, the packaged template, and the SAR `LicenseUrl`/`ReadmeUrl` artifacts there during `sam deploy`. Because the bucket is owned by this stack, the deploy pipeline never needs `resolve_s3` (no SAM-managed bucket) and `GitHubActionsRole` only needs object-level access (no `CreateBucket`/`DeleteBucket`).

> **The bootstrap stack is applied out-of-band by an admin — never by CI.** The CI workflows only *assume* `GitHubActionsRole`; they cannot create the role (or the artifacts bucket) they depend on. Any change to `github-oidc-role.yaml` (new permissions, the artifacts bucket, etc.) requires re-running the bootstrap deploy before the affected pipeline runs will work. See [Re-running the bootstrap stack](#re-running-the-bootstrap-stack).

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
- The SAM deployment artifacts bucket (`mocknest-sam-artifacts-<account-id>-<region>`, `DeletionPolicy: Retain`)

### 2. Add GitHub secrets and variables

The script outputs your AWS Account ID and role name. Add them to your repository (Settings → Secrets and variables → Actions):

- **Secret** (Secrets tab):
  - Name: `AWS_ACCOUNT_ID`
  - Value: your 12-digit AWS account ID

- **Variable** (Variables tab):
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

CloudFormation must use the execution role to create stack resources, so `sam deploy` needs `--role-arn`. **The reusable workflow `workflow-deploy-aws.yml` already does this automatically** — it derives the ARN as `arn:aws:iam::${AWS_ACCOUNT_ID}:role/MockNestCloudFormationExecutionRole` (account id from the `AWS_ACCOUNT_ID` secret) and passes both `--role-arn` and `--s3-bucket` (the artifacts bucket). It also passes `--s3-bucket mocknest-sam-artifacts-<account-id>-<region>` so no `resolve_s3`-managed bucket is needed.

For a manual deploy outside CI, pass it yourself:

```bash
sam deploy --role-arn <MockNestCloudFormationExecutionRoleArn> \
           --s3-bucket mocknest-sam-artifacts-<account-id>-<region>
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

## Re-running the bootstrap stack

The bootstrap stack (`mocknest-github-oidc`) is **not deployed by any CI pipeline** — it creates the OIDC provider, the IAM roles, and the artifacts bucket that the pipelines depend on, so it must be applied out-of-band by an admin with sufficient privileges.

**Re-run it whenever `github-oidc-role.yaml` changes**, for example after:
- adding/adjusting permissions on `GitHubActionsRole` or `MockNestCloudFormationExecutionRole`
- adding or renaming the artifacts bucket (`MockNestArtifactsBucket`)
- changing the trust policy (org/repo/branch)

If you skip this, deploys fail with errors like `S3 Bucket does not exist` (artifacts bucket not created yet) or `not authorized to perform: s3:PutObject` (role grant not live yet).

### Resources this stack manages

| Logical ID | Resource | Notes |
|------------|----------|-------|
| `GitHubOIDCProvider` | OIDC identity provider | Only created when `ExistingOIDCProviderArn` is empty |
| `GitHubActionsRole` | IAM role assumed by CI via OIDC | Deployment orchestration + object access to the artifacts bucket |
| `MockNestCloudFormationExecutionRole` | IAM role assumed by CloudFormation | Creates the actual app resources |
| `MockNestArtifactsBucket` | S3 bucket `mocknest-sam-artifacts-<account-id>-<region>` | SAM deploy artifacts; `DeletionPolicy: Retain` |

### Command

Re-run the helper script:

```bash
cd deployment/aws/shared
./setup-github-oidc.sh
```

…or apply non-interactively (preserve the existing `GitHubOrg`/`GitHubRepo` — passing different values rewrites the OIDC trust policy):

```bash
cd deployment/aws/shared

aws cloudformation deploy \
    --template-file github-oidc-role.yaml \
    --stack-name mocknest-github-oidc \
    --parameter-overrides \
        GitHubOrg="YOUR_GITHUB_ORG" \
        GitHubRepo="YOUR_GITHUB_REPO" \
        RoleName="GitHubActionsRole" \
    --capabilities CAPABILITY_NAMED_IAM \
    --region eu-west-1
```

To see the current parameter values before re-running:

```bash
aws cloudformation describe-stacks --stack-name mocknest-github-oidc \
    --region eu-west-1 --query 'Stacks[0].Parameters' --output table
```

Because the artifacts bucket uses `DeletionPolicy: Retain`, re-running is safe and never destroys existing artifacts.

---

## Files

| File | Purpose |
|------|---------|
| `github-oidc-role.yaml` | CloudFormation template (OIDC provider + both IAM roles + SAM artifacts bucket) |
| `setup-github-oidc.sh` | Interactive first-time setup script |
| `update-existing-oidc-role.sh` | Updates an existing role's trust policy |
| `README.md` | This file |

## Related Files

| File | Purpose |
|------|---------|
| `scripts/check-oidc-policy.sh` | Regression check — validates no permissive patterns |
| `.github/workflows/workflow-deploy-aws.yml` | Reusable deployment workflow |
| `.github/workflows/workflow-sar-publish.yml` | SAR publishing workflow |
