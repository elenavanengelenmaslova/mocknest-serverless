# Task 4: Bug Condition Exploration Test Results

## Test Execution Summary

**Test Name:** `Given WSDL with multiple SOAP 1_2 port types and different service addresses When parsing Then all operations should have correct per-binding service address`

**Test Location:** `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/parsers/WsdlSpecificationParserTest.kt`

**Test Class:** `WsdlSpecificationParserTest.MultiplePortTypesMisattribution`

**Execution Date:** April 1, 2026

**Test Status:** ✅ FAILED (as expected - confirms bug exists)

## Bug Condition Confirmed

The test successfully confirmed the bug exists in the unfixed code. The bug condition is:

**When a WSDL contains multiple SOAP 1.2 port types with different service addresses, all operations are assigned the same service address (the first one found) instead of their binding-specific service address.**

## Counterexample Documentation

### Test Input
- **WSDL File:** `multi-porttype-soap12.wsdl`
- **Port Types:** 2 (UserPortType, ProductPortType)
- **Operations:** 2 (GetUser, GetProduct)
- **Service Addresses:**
  - UserPort binding → `/multiport/user`
  - ProductPort binding → `/multiport/product`

### Expected Behavior (Correct)
- GetUser operation should use `/multiport/user` (from UserBinding)
- GetProduct operation should use `/multiport/product` (from ProductBinding)

### Actual Behavior (Bug)
- GetUser operation uses `/multiport/user` ✓
- GetProduct operation uses `/multiport/user` ✗ (should be `/multiport/product`)

### Failure Message
```
org.opentest4j.AssertionFailedError: GetProduct operation should use ProductBinding service address /multiport/product ==> expected: </multiport/product> but was: </multiport/user>
```

## Root Cause Analysis

The bug occurs in `WsdlSpecificationParser.convertToAPISpecification()`:

1. The method calls `serviceAddressPath(compactWsdl)` once at line 127
2. This extracts the first service address from `compactWsdl.serviceAddress`
3. All operations in the loop use this same `endpointPath` value
4. There is no per-operation binding resolution

**Code Location:** `software/application/src/main/kotlin/nl/vintik/mocknest/application/generation/parsers/WsdlSpecificationParser.kt:127`

```kotlin
val endpointPath = serviceAddressPath(compactWsdl)  // Called once, used for all operations
val endpoints = compactWsdl.operations.map { operation ->
    EndpointDefinition(
        path = endpointPath,  // Same path for all operations
        // ...
    )
}
```

## Test Validation

This test encodes the **expected behavior** (correct per-binding service addresses). When the fix is implemented:

1. The test will PASS, confirming the bug is fixed
2. Each operation will have its correct binding-specific service address
3. No changes to the test code will be needed

## Next Steps

Proceed to Task 5: Write preservation property tests for SOAP 1.2 port type parsing (BEFORE implementing fix)
