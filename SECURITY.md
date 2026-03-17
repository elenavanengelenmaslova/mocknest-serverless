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
- No persistent storage of sensitive request data

## Important Security Guidance

- **Mock Data Best Practice**: **Do not use real PII or sensitive production data in your mocks.** Always use realistic but synthetic test data. MockNest is designed for testing, not for handling production data.
- **AI Features**: When using AI mock generation, API specifications are processed by Amazon Bedrock. You can control data residency through the `BedrockInferenceMode` parameter (AUTO, GLOBAL_ONLY, or GEO_ONLY) combined with your deployment region. Choose GEO_ONLY if you have strict data residency requirements. Ensure your API specifications don't contain sensitive information.
- **S3 Storage**: Mock definitions are stored in S3 with server-side encryption enabled by default (AES-256). Follow AWS best practices for bucket access controls to protect your mock definitions from unauthorized access.

Thank you for helping keep MockNest Serverless and our users safe!
