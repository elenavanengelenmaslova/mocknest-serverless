# Security Policy

## Supported Versions

We release patches for security vulnerabilities for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.2.x   | :white_check_mark: |
| < 0.2.0 | :x:                |

## Reporting a Vulnerability

We take the security of MockNest Serverless seriously. If you discover a security vulnerability, please report it responsibly.

### How to Report

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them via:
- **Email**: Create an issue at https://github.com/elenavanengelenmaslova/mocknest-serverless/issues and mark it as "Security" OR
- **GitHub Security Advisories**: Use the [Security tab](https://github.com/elenavanengelenmaslova/mocknest-serverless/security/advisories/new) to privately report vulnerabilities

### What to Include

Please include the following information in your report:
- Type of vulnerability
- Full paths of affected source file(s)
- Location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit it

### Response Timeline

- We will acknowledge receipt of your vulnerability report within **48 hours**
- We will provide a detailed response indicating the next steps within **5 business days**
- We will keep you informed of the progress towards a fix
- We will notify you when the vulnerability is fixed

### Disclosure Policy

- Please give us reasonable time to fix the vulnerability before making it public
- We will credit you in the security advisory (unless you prefer to remain anonymous)

## Security Best Practices for Users

When deploying MockNest Serverless:

1. **API Keys**: Always use strong API keys and rotate them regularly
2. **IAM Permissions**: Follow the principle of least privilege for AWS IAM roles
3. **Network Access**: Restrict API Gateway access to trusted networks when possible
4. **Updates**: Keep MockNest Serverless updated to the latest version
5. **Secrets**: Never commit AWS credentials or API keys to version control
6. **S3 Buckets**: Ensure S3 buckets have proper access controls and encryption

## Security Features

MockNest Serverless includes:
- API key authentication for all endpoints
- S3 server-side encryption for stored mocks
- IAM role-based access control for AWS resources

## Security and Quality Tooling

MockNest Serverless uses a comprehensive set of automated tools to maintain security and quality standards. These tools provide continuous monitoring, automated updates, and proactive security scanning.

### Badge-Based Security Tools

These tools are visible as badges in the README and provide public dashboards for transparency:

#### Snyk Vulnerability Scanning
- **Purpose**: Continuously monitors dependencies, code, and infrastructure for security vulnerabilities
- **Visibility**: Badge in README linking to public dashboard
- **Automation**: 
  - Scans on every pull request
  - Weekly scheduled scans
  - Automatic fix PRs for vulnerabilities
- **Dashboard**: https://snyk.io/test/github/elenavanengelenmaslova/mocknest-serverless
- **Maintenance**: Automated with manual review of fix PRs

#### CII Best Practices Certification
- **Purpose**: Demonstrates adherence to open source security and quality standards
- **Visibility**: Badge in README linking to public criteria compliance page
- **Certification Level**: Passing (required criteria met)
- **Project Page**: https://bestpractices.coreinfrastructure.org/projects/{project-id}
- **Maintenance**: Quarterly review and re-certification as needed

#### CodeQL Code Scanning
- **Purpose**: Static analysis for security vulnerabilities and code quality issues
- **Visibility**: Badge in README linking to security scanning results
- **Automation**: Runs on every push and pull request
- **Dashboard**: GitHub Security tab
- **Configuration**: `.github/workflows/github-code-scanning/codeql`

#### OpenSSF Scorecard
- **Purpose**: Automated security health metrics for open source projects
- **Visibility**: Badge in README linking to detailed scorecard
- **Metrics**: Security practices, dependency management, code review, etc.
- **Dashboard**: https://securityscorecards.dev/viewer/?uri=github.com/elenavanengelenmaslova/mocknest-serverless
- **Maintenance**: Automated, no manual intervention required

#### Code Coverage (Codecov)
- **Purpose**: Tracks test coverage to ensure code quality
- **Visibility**: Badge in README linking to coverage reports
- **Automation**: Runs on every push via CI/CD
- **Dashboard**: https://codecov.io/gh/elenavanengelenmaslova/mocknest-serverless
- **Configuration**: `codecov.yml`

### Background Automation Tools

These tools operate automatically without visible badges but are essential for security:

#### Dependabot
- **Purpose**: Automated dependency updates to address security vulnerabilities
- **Visibility**: No badge, operates via GitHub pull requests
- **Automation**:
  - Weekly scans for dependency updates
  - Automatic PRs for security updates
  - Grouped updates to reduce PR noise
- **Configuration**: `.github/dependabot.yml`
- **Maintenance**: Review and merge automated PRs

#### CodeRabbit AI Code Review
- **Purpose**: AI-powered code review for quality and security issues
- **Visibility**: No badge, provides PR comments
- **Automation**: Reviews every pull request automatically
- **Configuration**: `.coderabbit.yaml`
- **Maintenance**: Review AI suggestions during PR review process

### Configuration Files

- **Dependabot**: `.github/dependabot.yml`
- **CodeRabbit**: `.coderabbit.yaml`
- **CodeQL**: `.github/workflows/github-code-scanning/codeql`
- **Codecov**: `codecov.yml`
- **Kover (Coverage)**: Applied via Gradle plugin in `build.gradle.kts`

### Adding New Security Tooling

When considering new security tools or badges:

1. **Evaluate necessity**: Does it provide unique value not covered by existing tools?
2. **Check automation**: Can it run automatically without manual intervention?
3. **Verify public visibility**: Can results be made public for transparency?
4. **Consider maintenance**: What ongoing maintenance is required?
5. **Update documentation**: Add to this section when new tools are adopted

### Tool Maintenance Schedule

- **Snyk**: Automated weekly scans, manual review of fix PRs as needed
- **CII Best Practices**: Quarterly review and re-certification
- **CodeQL**: Automated, no manual maintenance
- **OpenSSF Scorecard**: Automated, no manual maintenance
- **Codecov**: Automated, no manual maintenance
- **Dependabot**: Review and merge PRs weekly
- **CodeRabbit**: Review suggestions during PR process

## Important Security Guidance

- **Mock Data Best Practice**: **Do not use real PII or sensitive production data in your mocks.** Always use realistic but synthetic test data. MockNest is designed for testing, not for handling production data.
- **AI Features**: When using AI mock generation, API specifications are processed by Amazon Bedrock. You can control data residency through the `BedrockInferenceMode` parameter (AUTO, GLOBAL_ONLY, or GEO_ONLY) combined with your deployment region. Choose GEO_ONLY if you have strict data residency requirements. Ensure your API specifications don't contain sensitive information.
- **S3 Storage**: Mock definitions are stored in S3 with server-side encryption enabled by default (AES-256). Follow AWS best practices for bucket access controls to protect your mock definitions from unauthorized access.

Thank you for helping keep MockNest Serverless and our users safe!
