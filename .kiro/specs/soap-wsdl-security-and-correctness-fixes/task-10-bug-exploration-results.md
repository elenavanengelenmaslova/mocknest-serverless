# Task 10: Bug Condition Exploration Results

## Bug 4: Non-SOAP WSDL Silent Fallback

### Test Execution Date
2026-04-02

### Test Status
✅ **PASSED** - Bug has already been fixed in the current codebase

### Test Results

All 3 test scenarios passed successfully:

1. **HTTP-only WSDL rejection**: ✓ PASSED
   - Exception message: "No SOAP binding namespace found; non-SOAP WSDL bindings are not supported"
   - Parser correctly rejects WSDLs with only HTTP bindings

2. **SOAP 1.1 rejection**: ✓ PASSED
   - Exception message: "Only SOAP 1.2 is supported"
   - Parser correctly rejects SOAP 1.1 WSDLs with clear error message

3. **No bindings rejection**: ✓ PASSED
   - Exception message: "No SOAP binding namespace found; non-SOAP WSDL bindings are not supported"
   - Parser correctly rejects WSDLs with no binding elements

### Analysis

The current implementation in `WsdlParser.kt` already contains the fix for Bug 4. The `detectSoapVersion()` method:

```kotlin
private fun detectSoapVersion(root: Element): Pair<SoapVersion, List<String>> {
    // ... detection logic ...
    
    return when {
        hasSoap12 -> {
            if (hasSoap11) {
                warnings.add("WSDL contains both SOAP 1.1 and SOAP 1.2 bindings; selecting SOAP 1.2 only")
                logger.info { "Mixed SOAP versions detected; selecting SOAP 1.2, ignoring SOAP 1.1 bindings" }
            }
            Pair(SoapVersion.SOAP_1_2, warnings)
        }
        hasSoap11 -> {
            throw WsdlParsingException("Only SOAP 1.2 is supported")
        }
        else -> {
            throw WsdlParsingException(
                "No SOAP binding namespace found; non-SOAP WSDL bindings are not supported"
            )
        }
    }
}
```

This implementation:
- ✅ Throws `WsdlParsingException` for SOAP 1.1 bindings
- ✅ Throws `WsdlParsingException` for non-SOAP bindings (HTTP, etc.)
- ✅ Provides clear error messages indicating the issue
- ✅ Only accepts SOAP 1.2 bindings

### Conclusion

**Bug 4 has already been fixed.** The parser no longer silently defaults to SOAP 1.1 for non-SOAP WSDLs. Instead, it correctly throws exceptions with clear error messages.

The bug condition exploration test serves as a regression test to ensure this behavior is preserved in future changes.

### Test Files Created

1. `software/application/src/test/resources/wsdl/http-only-binding.wsdl` - Test WSDL with HTTP bindings only
2. `software/application/src/test/kotlin/nl/vintik/mocknest/application/generation/wsdl/NonSoapWsdlSilentFallbackBugTest.kt` - Bug condition exploration test

### Next Steps

Since Bug 4 is already fixed:
1. ✅ Task 10 is complete (bug exploration test written and passing)
2. Skip Task 11 (preservation tests) - not needed since bug is already fixed
3. Skip Task 12 (fix implementation) - already implemented
4. Proceed to next bug in the task list
