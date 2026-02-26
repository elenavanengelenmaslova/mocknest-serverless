# Runtime Interfaces Package

This package is reserved for runtime-specific interfaces. Currently, the runtime capability uses the generic storage interfaces from `nl.vintik.mocknest.application.core.interfaces.storage`.

## Current Usage

The runtime capability currently depends on:
- `ObjectStorageInterface` from `application.core.interfaces.storage` - Generic storage interface used for WireMock mappings and files

## Future Extensions

As the runtime capability evolves, runtime-specific interfaces may be added here, such as:
- Runtime-specific configuration interfaces
- WireMock lifecycle management interfaces
- Runtime monitoring and metrics interfaces
