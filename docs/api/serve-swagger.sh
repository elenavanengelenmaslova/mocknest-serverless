#!/bin/bash

# Serve OpenAPI spec with Swagger UI using Docker
# Usage: ./docs/api/serve-swagger.sh

echo "Starting Swagger UI server..."
echo "OpenAPI spec will be available at: http://localhost:8080"
echo "Press Ctrl+C to stop"

docker run -p 8080:8080 \
  -v $(pwd)/docs/api:/usr/share/nginx/html/api \
  -e SWAGGER_JSON=/usr/share/nginx/html/api/mocknest-openapi.yaml \
  swaggerapi/swagger-ui