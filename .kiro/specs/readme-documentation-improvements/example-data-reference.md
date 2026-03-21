# Canonical Example Data Reference

**Purpose**: This document serves as the single source of truth for all example data used across MockNest Serverless documentation. All examples in README.md, README-SAR.md, and docs/USAGE.md must match the data defined here.

**Source**: All examples are extracted from the Postman collection at `docs/postman/AWS MockNest Serverless.postman_collection.json` (version as of March 2026).

**Important**: Only include examples that exist in the Postman collection. The Postman collection is the authoritative source of truth.

---

## Variable Naming Standards

### Environment Variables
Use these variable names consistently across all documentation:

- **API URL**: `MOCKNEST_URL` (preferred) or `AWS_URL`
- **API Key**: `API_KEY` (preferred) or `api_key`

### Usage in Examples
```bash
${MOCKNEST_URL}  # For API URL
${API_KEY}       # For API key
```

### Deployment Output Names
- **API Gateway URL**: `MockNestApiUrl` (the URL from deployment outputs)
- **API Key ID**: `MockNestApiKey` (the key ID from deployment outputs, not the actual key value)
- **Actual Key Value**: Retrieved from API Gateway console (not shown in deployment outputs)

---

## SOAP Calculator Example

### Overview
A SOAP web service mock that adds two integers (5 + 3) and returns 42 as the result.

### Mapping ID
```
76ada7b0-55ae-4229-91c4-396a36f18123
```

### Request Details
- **Method**: POST
- **URL Path**: `/dneonline/calculator.asmx`
- **Content-Type**: `text/xml`
- **Operation**: Add
- **Input A**: 5
- **Input B**: 3

### Request Body (XML)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xmlns:xsd="http://www.w3.org/2001/XMLSchema"
               xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Add xmlns="http://tempuri.org/">
      <intA>5</intA>
      <intB>3</intB>
    </Add>
  </soap:Body>
</soap:Envelope>
```

### Response Details
- **Status**: 200
- **Content-Type**: `text/xml; charset=utf-8`
- **Result**: 42

### Response Body (XML)
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

### Mapping Definition (JSON)
```json
{
  "id": "76ada7b0-55ae-4229-91c4-396a36f18123",
  "priority": 1,
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
  "persistent": true
}
```

---

## GraphQL Pet Example

### Overview
A GraphQL mock that returns information about a pet named Buddy.

### Pet Details
- **ID**: "123"
- **Name**: "Buddy"
- **Species**: "dog"
- **Breed**: "Golden Retriever"

### Request Details
- **Method**: POST
- **URL Path**: `/graphql`
- **Content-Type**: `application/json`

### Request Body (JSON)
```json
{
  "query": "query { getPet(id: \"123\") { id name species breed } }"
}
```

### Response Details
- **Status**: 200
- **Content-Type**: `application/json`

### Response Body (JSON)
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

### Mapping Definition (JSON)
```json
{
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
}
```

---

## AI Generation Example (Petstore API)

### Overview
AI-generated mocks for a petstore API with 3 pets, demonstrating consistent data across multiple endpoints.

### API Details
- **API Name**: petstore
- **Specification URL**: https://petstore3.swagger.io/api/v3/openapi.json
- **Format**: OPENAPI_3
- **Namespace**: `/petstore`

### Pet 1: Buddy (The Dog)
```json
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
```

**Characteristics**:
- Dog with funny photo
- Status: available
- Tagged as "new" (tag id: 1, name: "new")
- Appears in findByStatus (available) results
- Appears in findByTags (new) results

### Pet 2: Max
```json
{
  "id": 2,
  "name": "Max",
  "status": "available",
  "photoUrls": [
    "https://example.com/max.jpg"
  ]
}
```

**Characteristics**:
- Status: available
- No tags (not a "new" pet)
- Appears in findByStatus (available) results

### Pet 3: Luna
```json
{
  "id": 3,
  "name": "Luna",
  "status": "available",
  "photoUrls": [
    "https://example.com/luna.jpg"
  ]
}
```

**Characteristics**:
- Status: available
- No tags (not a "new" pet)
- Appears in findByStatus (available) results

### Generated Endpoints

#### 1. Find Pets by Status (available)
- **Method**: GET
- **URL Path**: `/petstore/pet/findByStatus?status=available`
- **Response**: Array of all 3 pets (Buddy, Max, Luna)

#### 2. Find Pets by Tags (new)
- **Method**: GET
- **URL Path**: `/petstore/pet/findByTags?tags=new`
- **Response**: Array with only Buddy (the pet with "new" tag)

#### 3. Get Pet by ID (1)
- **Method**: GET
- **URL Path**: `/petstore/pet/1`
- **Response**: Buddy's details

#### 4. Get Pet by ID (2)
- **Method**: GET
- **URL Path**: `/petstore/pet/2`
- **Response**: Max's details

#### 5. Get Pet by ID (3)
- **Method**: GET
- **URL Path**: `/petstore/pet/3`
- **Response**: Luna's details

### AI Generation Request
```json
{
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
}
```

---

## Health Check Examples

### Admin Health Check

#### Request
- **Method**: GET
- **URL Path**: `/__admin/health`
- **Headers**: `x-api-key: ${API_KEY}`

#### Response (200 OK)
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

### AI Generation Health Check

#### Request
- **Method**: GET
- **URL Path**: `/ai/generation/health`
- **Headers**: `x-api-key: ${API_KEY}`

#### Response (200 OK)
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

---

## Administrative Operations Examples

### Get All Mappings

#### Request
- **Method**: GET
- **URL Path**: `/__admin/mappings`
- **Headers**: `x-api-key: ${API_KEY}`

#### Response (200 OK)
Returns a list of all mappings including the SOAP calculator mapping and petstore mappings.

### Get File Content

#### Request
- **Method**: GET
- **URL Path**: `/__admin/files/76ada7b0-55ae-4229-91c4-396a36f18123.json`
- **Headers**: `x-api-key: ${API_KEY}`

#### Response (200 OK)
Returns the SOAP response XML stored in the file.

### Delete All Mappings

#### Request
- **Method**: DELETE
- **URL Path**: `/__admin/mappings`
- **Headers**: `x-api-key: ${API_KEY}`

#### Response (200 OK)
```json
{}
```

### Import Mappings

#### Request
- **Method**: POST
- **URL Path**: `/__admin/mappings/import`
- **Headers**: `x-api-key: ${API_KEY}`
- **Body**: JSON object with `mappings` array

#### Response (200 OK)
Empty response with status 200.

---

## Consistency Rules

### Cross-File Consistency
When the same example appears in multiple documentation files:
1. Use identical request bodies
2. Use identical response bodies
3. Use identical parameter values
4. Use identical explanatory text where appropriate

### Variable Usage
Always use environment variable syntax in examples:
- `${MOCKNEST_URL}` for the API URL
- `${API_KEY}` for the API key
- Never hardcode actual URLs or keys in examples

### Terminology
- "API Gateway console" (not "AWS console" or "Gateway console")
- "deployment outputs" (not "stack outputs" or "CloudFormation outputs")
- "MockNestApiUrl" for the URL deployment output
- "MockNestApiKey" for the key ID deployment output
- "API key value" for the actual key from console

---

## Maintenance Notes

### When to Update This Document
- When the Postman collection is updated with new examples
- When API endpoints are added, modified, or removed
- When example data needs to change for clarity or accuracy

### Update Process
1. Update the Postman collection first (source of truth)
2. Update this reference document to match
3. Update all documentation files (README.md, README-SAR.md, USAGE.md) to match this reference
4. Run validation scripts to ensure consistency

### Validation
Before publishing documentation updates:
- Verify all examples exist in the Postman collection
- Verify variable names are consistent
- Verify example data matches across all files
- Test all cURL commands against a live deployment

---

**Last Updated**: March 2026
**Postman Collection Version**: AWS MockNest Example (ff6154b8-35cd-4919-9a03-38eb6401d6cd)
