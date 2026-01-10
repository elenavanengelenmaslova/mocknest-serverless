# MockNest Serverless Improvement Plan

## Current Status
MockNest Serverless has a working implementation but needs to achieve successful deployment before adding new features. The current priority is to get the serverless WireMock runtime fully deployed and tested in AWS.

## Phase 1: Deploy Successfully (Current Priority)
**Goal**: Get current implementation deployed and working in AWS

### 1.1 Fix Current Deployment Issues
- ✅ Fix YAML syntax error in GitHub Actions workflow (completed)
- [ ] Resolve any remaining GitHub Actions build issues
- [ ] Ensure SAM template deploys successfully to AWS
- [ ] Validate deployed MockNest instance works correctly

### 1.2 Complete Current Implementation Testing
- [ ] Run comprehensive test suite and achieve 90%+ coverage
- [ ] Fix any failing tests in the current implementation
- [ ] Validate all WireMock features work in serverless environment
- [ ] Test actual deployment in AWS environment

## Phase 2: Environment Refactoring (Post-Deployment)
**Goal**: Implement multi-instance deployment model

### 2.1 Conceptual Changes
**Current Problem**: Traditional dev→staging→prod environment model doesn't fit MockNest's use case
**Solution**: Instance-based deployment model where each MockNest is an independent testing tool

### 2.2 Refactoring Plan

#### Replace Environment Concept with Instance Names
```yaml
# Current (confusing):
Environment: dev/staging
stack_name = "mocknest-serverless-dev"

# Proposed (clear):
InstanceName: team-a-integration
stack_name = "mocknest-${InstanceName}"
```

#### Update Resource Naming Strategy
```yaml
# All resources use instance-based naming:
S3 Bucket: mocknest-storage-${instance_name}-${account_id}
Lambda Function: mocknest-${instance_name}-runtime
API Gateway: mocknest-${instance_name}-api
Stack Name: mocknest-${instance_name}
```

#### Pipeline Parameter Changes
```yaml
# Current:
with:
  environment: default  # Confusing

# Proposed:
with:
  instance-name: team-a-integration
  temporary: false  # Lifecycle hint
```

#### SAM Configuration Restructure
```toml
# Current (environment-based):
[default]
[staging]

# Proposed (template-based):
[template]  # Default template for any instance
stack_name_prefix = "mocknest"

[shared-integration]  # Example permanent instance
# Custom overrides

[feature-template]  # Template for temporary instances
# Smaller resources, shorter retention
```

### 2.3 Implementation Tasks
1. **SAM Template Updates**:
   - Replace `Environment` parameter with `InstanceName`
   - Update all resource names to use instance-based naming
   - Add instance lifecycle management parameters

2. **Pipeline Updates**:
   - Update workflow parameters from `environment` to `instance-name`
   - Modify samconfig.toml parsing logic
   - Update deployment summaries to reflect instance concept

3. **Configuration Management**:
   - Restructure samconfig.toml for template-based configuration
   - Add support for instance-specific overrides
   - Implement naming validation and conflict prevention

## Phase 3: Enhanced Features (Future)
**Goal**: Add advanced deployment and management capabilities

### 3.1 Instance Lifecycle Management
- Automated cleanup for temporary instances
- Instance discovery and listing tools
- Cross-instance testing scenarios

### 3.2 Flexible Resource Management
- Instance-specific resource sizing
- Cost optimization per instance type
- Performance monitoring per instance

### 3.3 Multi-Instance Tooling
- CLI tools for managing multiple instances
- Instance health monitoring across deployments
- Centralized logging and metrics aggregation

## Benefits of This Approach

### Immediate Benefits (Phase 2)
1. **Clear Mental Model**: Each MockNest deployment is a testing tool, not an environment stage
2. **No Promotion Confusion**: Eliminates false impression of dev→staging→prod pipeline
3. **Multiple Instances**: Teams can deploy multiple MockNest instances without conflicts
4. **Flexible Naming**: Instance names reflect actual purpose (e.g., `team-a-integration`, `feature-auth-tests`)

### Long-term Benefits (Phase 3)
1. **Better Resource Management**: Different resource profiles per use case
2. **Lifecycle Flexibility**: Some instances temporary (feature branches), others permanent (team shared)
3. **Operational Clarity**: Clear understanding of what each deployment is for
4. **Scalability**: Easy to add new instances without architectural changes

## Implementation Priority

**Current Focus**: Phase 1 - Deploy Successfully
- All effort should focus on getting current implementation working in AWS
- No refactoring until we have a working deployment
- Document issues and improvements for Phase 2

**Next Steps**: Phase 2 - Environment Refactoring  
- Only begin after successful AWS deployment
- Implement instance-based model systematically
- Maintain backward compatibility during transition

**Future Work**: Phase 3 - Enhanced Features
- Add advanced instance management capabilities
- Implement operational tooling for multi-instance scenarios
- Optimize for production usage patterns

## Success Criteria

### Phase 1 Success
- [ ] GitHub Actions pipeline runs successfully
- [ ] SAM template deploys to AWS without errors
- [ ] Deployed MockNest instance responds to health checks
- [ ] Basic WireMock functionality works in deployed environment
- [ ] Test coverage meets 90% threshold

### Phase 2 Success
- [ ] Instance-based naming implemented across all resources
- [ ] Multiple instances can be deployed without conflicts
- [ ] Pipeline supports instance-name parameter
- [ ] samconfig.toml uses template-based configuration
- [ ] Documentation updated to reflect new model

### Phase 3 Success
- [ ] Instance lifecycle management working
- [ ] Multi-instance tooling available
- [ ] Performance monitoring per instance
- [ ] Operational procedures documented