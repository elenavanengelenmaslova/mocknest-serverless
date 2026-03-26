# MockNest Serverless - cURL Usage Guide

> **Note**: This guide should be kept in sync with the [Postman collection](postman/). The Postman collection is the authoritative source of truth for API examples.

## Introduction

This guide provides comprehensive cURL-based examples for using MockNest Serverless API. Whether you're integrating MockNest into CI/CD pipelines, automating test scenarios, or exploring the API from the command line, these examples will help you get started quickly.

MockNest Serverless is a serverless mock runtime that allows you to create and manage HTTP mocks for REST, SOAP, and GraphQL APIs. It runs on AWS Lambda and stores mock definitions persistently in Amazon S3.

You can manage mocks in two ways:
- **Manual Management**: Use the admin API (`/__admin/*` endpoints) to create, update, and delete mocks manually with full control over request matching and response behavior
- **AI-Assisted Generation**: Use the AI generation API (`/ai/*` endpoints) to automatically generate comprehensive mock suites from API specifications, leveraging Amazon Bedrock to create realistic, consistent mock data

## Table of Contents

- [Prerequisites](#prerequisites)
- [Setup](#setup)
- [Health Checks](#health-checks)
  - [Admin Health Check](#admin-health-check)
  - [AI Generation Health Check](#ai-generation-health-check)
- [Manual Mock Management](#manual-mock-management)
  - [REST API Mock Management](#rest-api-mock-management)
  - [SOAP Mock Management](#soap-mock-management)
  - [GraphQL Mock Management](#graphql-mock-management)
- [AI-Assisted Mock Generation](#ai-assisted-mock-generation)
  - [Generate Mocks from OpenAPI Specification](#generate-mocks-from-openapi-specification)
  - [Import Generated Mappings](#import-generated-mappings)
  - [Call Generated Pet API by Status](#call-generated-pet-api-by-status)
  - [Call Generated Pet API by Tags](#call-generated-pet-api-by-tags)
- [Administrative Operations](#administrative-operations)
  - [Get All Mappings](#get-all-mappings)
  - [Get File Content](#get-file-content)
  - [Delete All Mappings](#delete-all-mappings)
- [Next Steps](#next-steps)

## Prerequisites

Before using MockNest Serverless, you need:

1. **API Gateway URL**: The endpoint URL for your MockNest deployment
2. **API Key**: The API key value for authentication

Both values can be obtained from the **API Gateway console**:
- Navigate to API Gateway console in AWS
- Find your MockNest Serverless API
- Get the Invoke URL from the Stages section
- Get the API key value from the API Keys section (click "Show" to reveal)

Alternatively, the API Gateway URL is available in deployment outputs as `MockNestApiUrl`, but you'll still need to visit the API Gateway console to retrieve the actual API key value (the deployment output shows only the key ID).


## Setup

To simplify the examples in this guide, set environment variables for your API URL and key:

### Bash/Zsh Configuration

```bash
# Set the MockNest API URL (replace with your actual API Gateway URL)
export MOCKNEST_URL="https://your-api-id.execute-api.your-region.amazonaws.com/mocks"

# Set the API key (replace with your actual API key value from API Gateway console)
export API_KEY="your-api-key-value-here"
```

You can add these to your `~/.bashrc` or `~/.zshrc` file to make them persistent across terminal sessions.

### Verify Setup

Test that your environment variables are set correctly:

```bash
echo $MOCKNEST_URL
echo $API_KEY
```


## Health Checks

Health check endpoints allow you to verify that MockNest Serverless is running correctly and check the status of various components.

### Admin Health Check

Check the health of the core MockNest runtime and storage connectivity.

**Description**: Verifies that the admin API is accessible, the runtime is healthy, and storage (S3) connectivity is working.

**Command**:
```bash
curl -X GET "${MOCKNEST_URL}/__admin/health" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
```json
{
  "status": "healthy",
  "timestamp": "2026-03-13T05:39:33.882708215Z",
  "region": "eu-west-1",
  "version": "0.2.0",
  "storage": {
    "bucket": "mocknest-serverless-mockstorage-uqxz33qujmfh",
    "connectivity": "ok"
  }
}
```

**Key Parameters**:
- `x-api-key` header: Your API key for authentication
- Response `status`: Overall health status ("healthy" or "unhealthy")
- Response `storage.connectivity`: S3 bucket connectivity status

### AI Generation Health Check

Check the health of the AI-assisted mock generation service (if enabled).

**Description**: Verifies that the AI generation service is accessible and configured correctly with Amazon Bedrock.

**Command**:
```bash
curl -X GET "${MOCKNEST_URL}/ai/generation/health" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
```json
{
  "status": "healthy",
  "timestamp": "2026-03-13T05:39:29.324140231Z",
  "region": "eu-west-1",
  "version": "0.2.0",
  "ai": {
    "modelName": "AmazonNovaPro",
    "inferencePrefix": "eu",
    "inferenceMode": "AUTO"
  }
}
```

**Key Parameters**:
- `x-api-key` header: Your API key for authentication
- Response `ai.modelName`: The AI model being used for mock generation
- Response `ai.inferenceMode`: The inference mode configuration


## Manual Mock Management

MockNest Serverless supports creating and managing mocks for various API types including REST, SOAP, and GraphQL.

### REST API Mock Management

Create and test REST API mocks using query parameter matching.

#### Create REST API Mapping

**Description**: Creates a REST API mock for a "Bored API" activity endpoint that returns a social activity suggestion.

**Command**:
```bash
curl -X POST "${MOCKNEST_URL}/__admin/mappings" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
  "id": "76ada7b0-55ae-4229-91c4-396a36f18347",
  "priority": 2,
  "request": {
    "method": "GET",
    "urlPath": "/bored/api/activity",
    "queryParameters": {
      "type": { "equalTo": "social" }
    }
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "activity": "Escape the nulls of daily life — nerd out over MockNest on AWS Lambda",
      "type": "social",
      "participants": 1,
      "link": "https://awscommunityday.nl/",
      "price": 0.3,
      "accessibility": 0.4
    }
  },
  "persistent": true
}'
```

**Expected Response** (201 Created):
```json
{
  "id": "76ada7b0-55ae-4229-91c4-396a36f18347",
  "request": {
    "urlPath": "/bored/api/activity",
    "method": "GET",
    "queryParameters": {
      "type": {
        "equalTo": "social"
      }
    }
  },
  "response": {
    "status": 200,
    "bodyFileName": "76ada7b0-55ae-4229-91c4-396a36f18347.json",
    "headers": {
      "Content-Type": "application/json"
    }
  },
  "uuid": "76ada7b0-55ae-4229-91c4-396a36f18347",
  "persistent": true,
  "priority": 2
}
```

**Key Parameters**:
- `id`: Unique identifier for the mapping
- `request.queryParameters`: Query parameter matching criteria
- `response.jsonBody`: REST API JSON response
- `persistent`: Set to `true` to save the mapping to S3 storage

#### Test REST API Mock

**Description**: Calls the REST API mock to retrieve an activity suggestion.

**Command**:
```bash
curl -X GET "${MOCKNEST_URL}/mocknest/bored/api/activity?type=social" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
```json
{
  "activity": "Escape the nulls of daily life — nerd out over MockNest on AWS Lambda",
  "type": "social",
  "participants": 1,
  "link": "https://awscommunityday.nl/",
  "price": 0.3,
  "accessibility": 0.4
}
```

**Key Parameters**:
- `type` query parameter: Filter activities by type (e.g., "social", "education", "recreational")
- Response: JSON object with activity details

### SOAP Mock Management

Create and test SOAP web service mocks using WireMock's XML matching capabilities.

#### Create SOAP Mapping

**Description**: Creates a SOAP mock for a calculator service that adds two integers (5 + 3) and returns 42.

**Command**:
```bash
curl -X POST "${MOCKNEST_URL}/__admin/mappings" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
  "id": "76ada7b0-55ae-4229-91c4-396a36f18123",
  "priority": 1,
  "request": {
    "method": "POST",
    "url": "/dneonline/calculator.asmx",
    "bodyPatterns": [
      {
        "matchesXPath": "//*[local-name()=\"intA\" and text()=\"5\"]"
      },
      {
        "matchesXPath": "//*[local-name()=\"intB\" and text()=\"3\"]"
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "text/xml; charset=utf-8"
    },
    "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n  <soap:Body>\n    <AddResponse xmlns=\"http://tempuri.org/\">\n      <AddResult>42</AddResult>\n    </AddResponse>\n  </soap:Body>\n</soap:Envelope>"
  },
  "persistent": true
}'
```

**Expected Response** (201 Created):
```json
{
  "id": "76ada7b0-55ae-4229-91c4-396a36f18123",
  "request": {
    "method": "POST",
    "url": "/dneonline/calculator.asmx",
    "bodyPatterns": [
      {
        "matchesXPath": "//*[local-name()='intA' and text()='5']"
      },
      {
        "matchesXPath": "//*[local-name()='intB' and text()='3']"
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "text/xml; charset=utf-8"
    },
    "body": "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n  <soap:Body>\n    <AddResponse xmlns=\"http://tempuri.org/\">\n      <AddResult>42</AddResult>\n    </AddResponse>\n  </soap:Body>\n</soap:Envelope>"
  },
  "uuid": "76ada7b0-55ae-4229-91c4-396a36f18123",
  "persistent": true
}
```

**Key Parameters**:
- `id`: Unique identifier for the mapping
- `request.bodyPatterns`: XPath expressions to match SOAP request elements
- `response.body`: SOAP response XML
- `persistent`: Set to `true` to save the mapping to S3 storage

#### Test SOAP Mock

**Description**: Calls the SOAP mock to verify it returns the expected response.

**Command**:
```bash
curl -X POST "${MOCKNEST_URL}/dneonline/calculator.asmx" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xmlns:xsd="http://www.w3.org/2001/XMLSchema"
               xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Add xmlns="http://tempuri.org/">
      <intA>5</intA>
      <intB>3</intB>
    </Add>
  </soap:Body>
</soap:Envelope>'
```

**Expected Response** (200 OK):
```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <AddResponse xmlns="http://tempuri.org/">
      <AddResult>42</AddResult>
    </AddResponse>
  </soap:Body>
</soap:Envelope>
```

**Key Parameters**:
- `Content-Type: text/xml`: Required for SOAP requests
- Request body: SOAP envelope with operation parameters
- Response: SOAP envelope with operation result


### GraphQL Mock Management

Create and test GraphQL API mocks using JSON pattern matching.

#### Create GraphQL Mapping

**Description**: Creates a GraphQL mock that returns information about a pet named Buddy (a Golden Retriever).

**Command**:
```bash
curl -X POST "${MOCKNEST_URL}/__admin/mappings" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
  "request": {
    "method": "POST",
    "urlPath": "/graphql",
    "bodyPatterns": [{
      "matchesJsonPath": "$[?(@.query =~ /.*getPet.*/i)]"
    }]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "data": {
        "pet": {
          "id": "123",
          "name": "Buddy",
          "species": "dog",
          "breed": "Golden Retriever"
        }
      }
    }
  },
  "persistent": true
}'
```

**Expected Response** (201 Created):
```json
{
  "id": "generated-uuid",
  "request": {
    "method": "POST",
    "urlPath": "/graphql",
    "bodyPatterns": [
      {
        "matchesJsonPath": "$[?(@.query =~ /.*getPet.*/i)]"
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "data": {
        "pet": {
          "id": "123",
          "name": "Buddy",
          "species": "dog",
          "breed": "Golden Retriever"
        }
      }
    }
  },
  "uuid": "generated-uuid",
  "persistent": true
}
```

**Key Parameters**:
- `request.bodyPatterns`: JSONPath expression to match GraphQL queries
- `response.jsonBody`: GraphQL response data structure
- `persistent`: Set to `true` to save the mapping to S3 storage

#### Test GraphQL Mock

**Description**: Calls the GraphQL mock to retrieve pet information.

**Command**:
```bash
curl -X POST "${MOCKNEST_URL}/graphql" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
  "query": "query { getPet(id: \"123\") { id name species breed } }"
}'
```

**Expected Response** (200 OK):
```json
{
  "data": {
    "pet": {
      "id": "123",
      "name": "Buddy",
      "species": "dog",
      "breed": "Golden Retriever"
    }
  }
}
```

**Key Parameters**:
- `Content-Type: application/json`: Required for GraphQL requests
- Request body: GraphQL query string
- Response: GraphQL data response structure


## AI-Assisted Mock Generation

MockNest Serverless can generate comprehensive mock suites from API specifications using AI (when the AI generation feature is enabled).

### Generate Mocks from OpenAPI Specification

**Description**: Generates mocks for a petstore API from an OpenAPI specification. This example creates mocks for 3 pets with consistent data across multiple endpoints.

**Command**:
```bash
curl -X POST "${MOCKNEST_URL}/ai/generation/from-spec" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
  "namespace": {
    "apiName": "petstore",
    "client": null
  },
  "specification": null,
  "specificationUrl": "https://petstore3.swagger.io/api/v3/openapi.json",
  "format": "OPENAPI_3",
  "description": "Generate mocks for 3 pets from the Petstore OpenAPI specification, pets enpoints, only generate mocks for all GET endpoints of pets, return consistent data for these pets accross endpoints, 1 pet is a dog with this image: https://cdn-fastly.petguide.com/media/2022/02/16/8235403/top-10-funniest-dog-breeds.jpg, this dog is available and a new pet and should have a tag with id=1 and name=new, we need to have api call to get all new pets and its that dog, the other two pets are available also but are not new",
  "options": {
    "enableValidation": true
  }
}'
```

**Expected Response** (200 OK):
```json
{
  "jobId": "generated-job-id",
  "status": "completed",
  "mappings": [
    {
      "request": {
        "method": "GET",
        "urlPath": "/petstore/pet/findByStatus",
        "queryParameters": {
          "status": {
            "equalTo": "available"
          }
        }
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "jsonBody": [
          {
            "id": 1,
            "name": "Buddy",
            "status": "available",
            "photoUrls": [
              "https://cdn-fastly.petguide.com/media/2022/02/16/8235403/top-10-funniest-dog-breeds.jpg"
            ],
            "tags": [
              {
                "id": 1,
                "name": "new"
              }
            ]
          },
          {
            "id": 2,
            "name": "Max",
            "status": "available",
            "photoUrls": [
              "https://example.com/max.jpg"
            ]
          },
          {
            "id": 3,
            "name": "Luna",
            "status": "available",
            "photoUrls": [
              "https://example.com/luna.jpg"
            ]
          }
        ]
      }
    }
  ]
}
```

**Key Parameters**:
- `namespace.apiName`: Name prefix for the generated mock endpoints
- `specificationUrl`: URL to the OpenAPI specification
- `format`: Specification format (OPENAPI_3, OPENAPI_2, etc.)
- `description`: Natural language description guiding the AI generation
- `options.enableValidation`: Validate generated mocks against the specification

### Import Generated Mappings

**Description**: Imports the AI-generated mappings into MockNest to make them available for testing.

**Command**:
```bash
curl -X POST "${MOCKNEST_URL}/__admin/mappings/import" \
  -H "x-api-key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
  "mappings": [
    {
      "request": {
        "method": "GET",
        "urlPath": "/petstore/pet/findByStatus",
        "queryParameters": {
          "status": {
            "equalTo": "available"
          }
        }
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "jsonBody": [
          {
            "id": 1,
            "name": "Buddy",
            "status": "available",
            "photoUrls": [
              "https://cdn-fastly.petguide.com/media/2022/02/16/8235403/top-10-funniest-dog-breeds.jpg"
            ],
            "tags": [
              {
                "id": 1,
                "name": "new"
              }
            ]
          },
          {
            "id": 2,
            "name": "Max",
            "status": "available",
            "photoUrls": [
              "https://example.com/max.jpg"
            ]
          },
          {
            "id": 3,
            "name": "Luna",
            "status": "available",
            "photoUrls": [
              "https://example.com/luna.jpg"
            ]
          }
        ]
      }
    },
    {
      "request": {
        "method": "GET",
        "urlPath": "/petstore/pet/findByTags",
        "queryParameters": {
          "tags": {
            "equalTo": "new"
          }
        }
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "jsonBody": [
          {
            "id": 1,
            "name": "Buddy",
            "status": "available",
            "photoUrls": [
              "https://cdn-fastly.petguide.com/media/2022/02/16/8235403/top-10-funniest-dog-breeds.jpg"
            ],
            "tags": [
              {
                "id": 1,
                "name": "new"
              }
            ]
          }
        ]
      }
    }
  ]
}'
```

**Expected Response** (200 OK):
Empty response with status 200 indicating successful import.

**Key Parameters**:
- `mappings`: Array of WireMock mapping definitions to import
- Each mapping includes request matching criteria and response definition

### Call Generated Pet API by Status

**Description**: Calls the generated mock to retrieve all pets with "available" status.

**Command**:
```bash
curl -X GET "${MOCKNEST_URL}/petstore/pet/findByStatus?status=available" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
```json
[
  {
    "id": 1,
    "name": "Buddy",
    "status": "available",
    "photoUrls": [
      "https://cdn-fastly.petguide.com/media/2022/02/16/8235403/top-10-funniest-dog-breeds.jpg"
    ],
    "tags": [
      {
        "id": 1,
        "name": "new"
      }
    ]
  },
  {
    "id": 2,
    "name": "Max",
    "status": "available",
    "photoUrls": [
      "https://example.com/max.jpg"
    ]
  },
  {
    "id": 3,
    "name": "Luna",
    "status": "available",
    "photoUrls": [
      "https://example.com/luna.jpg"
    ]
  }
]
```

**Key Parameters**:
- `status` query parameter: Filter pets by status (e.g., "available", "pending", "sold")
- Response: Array of pet objects matching the status filter

### Call Generated Pet API by Tags

**Description**: Calls the generated mock to retrieve all pets with the "new" tag.

**Command**:
```bash
curl -X GET "${MOCKNEST_URL}/petstore/pet/findByTags?tags=new" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
```json
[
  {
    "id": 1,
    "name": "Buddy",
    "status": "available",
    "photoUrls": [
      "https://cdn-fastly.petguide.com/media/2022/02/16/8235403/top-10-funniest-dog-breeds.jpg"
    ],
    "tags": [
      {
        "id": 1,
        "name": "new"
      }
    ]
  }
]
```

**Key Parameters**:
- `tags` query parameter: Filter pets by tag name (e.g., "new", "featured")
- Response: Array of pet objects that have the specified tag


## Administrative Operations

MockNest Serverless provides administrative endpoints for managing mappings and files.

### Get All Mappings

**Description**: Retrieves all mock mappings currently configured in MockNest.

**Command**:
```bash
curl -X GET "${MOCKNEST_URL}/__admin/mappings" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
```json
{
  "mappings": [
    {
      "id": "76ada7b0-55ae-4229-91c4-396a36f18123",
      "request": {
        "url": "/dneonline/calculator.asmx",
        "method": "POST",
        "bodyPatterns": [
          {
            "matchesXPath": "//*[local-name()='intA' and text()='5']"
          },
          {
            "matchesXPath": "//*[local-name()='intB' and text()='3']"
          }
        ]
      },
      "response": {
        "status": 200,
        "bodyFileName": "76ada7b0-55ae-4229-91c4-396a36f18123.json",
        "headers": {
          "Content-Type": "text/xml; charset=utf-8"
        }
      },
      "uuid": "76ada7b0-55ae-4229-91c4-396a36f18123",
      "persistent": true,
      "priority": 1
    }
  ],
  "meta": {
    "total": 1
  }
}
```

**Key Parameters**:
- `x-api-key` header: Your API key for authentication
- Response `mappings`: Array of all configured mock mappings
- Response `response.bodyFileName`: The filename to use when retrieving file content (see "Get File Content" section below)
- Response `meta.total`: Total count of mappings

### Get File Content

**Description**: Retrieves the content of a specific file stored in MockNest (e.g., externalized response bodies).

**Note**: Use the filename from the "Get All Mappings" response above. Look for the `response.bodyFileName` field in the mapping output (e.g., `"bodyFileName": "76ada7b0-55ae-4229-91c4-396a36f18123.json"`).

**Command**:
```bash
curl -X GET "${MOCKNEST_URL}/__admin/files/76ada7b0-55ae-4229-91c4-396a36f18123.json" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
Returns the file content (format depends on the file type - JSON, XML, plain text, etc.).

**Key Parameters**:
- File path: The filename from `response.bodyFileName` in the "Get All Mappings" output
- `x-api-key` header: Your API key for authentication
- Response: Raw file content

### Delete All Mappings

**Description**: Deletes all mock mappings from MockNest. Use with caution as this operation cannot be undone.

**Command**:
```bash
curl -X DELETE "${MOCKNEST_URL}/__admin/mappings" \
  -H "x-api-key: ${API_KEY}"
```

**Expected Response** (200 OK):
```json
{}
```

**Key Parameters**:
- `x-api-key` header: Your API key for authentication
- Response: Empty JSON object indicating successful deletion


## Next Steps

Now that you're familiar with the basic cURL operations, here are some resources to help you explore further:

### Additional Resources

- **[OpenAPI Specification](api/mocknest-openapi.yaml)** - Complete API reference with all available endpoints and parameters
- **[Postman Collection](postman/)** - Pre-configured API requests for testing with Postman
- **[Main README](../README.md)** - Project overview, deployment instructions, and architecture details

### Keep Documentation in Sync

This cURL usage guide is maintained alongside the Postman collection. When the API changes:
1. Update the Postman collection first (source of truth)
2. Update this USAGE.md guide to match
3. Verify all examples work against a live deployment

### Getting Help

- **GitHub Issues**: Report bugs or request features at the project repository
- **Documentation**: Check the main README for troubleshooting tips
- **Community**: Join discussions and share your use cases

---

**Version**: 0.2.0  
**Last Updated**: March 2026  
**Postman Collection**: AWS MockNest Serverless (ff6154b8-35cd-4919-9a03-38eb6401d6cd)
