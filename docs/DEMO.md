# MockNest Serverless Demo Script

Target demo length: ~5 minutes

This document contains the narration and the visual plan for recording the demo video.

The demo will be recorded in multiple segments and later combined during editing.

---

## 🎬 Clip 1 – The Problem

# 1. Hook – The Problem

## Narration

Is integration testing slowing you down?

When applications depend on external APIs, testing quickly becomes difficult.

Sometimes the API is simply not reachable from development environments.

Even when the API is available, test data is difficult to control, edge cases are hard to reproduce, and sandbox environments are often unreliable.

For serverless applications this becomes even more important, because most workflows depend on external services and integration testing often happens after deployment.

## On Screen

Show Postman.

Request:

`GET https://pet-api.example.com/pets`

Send request.

Response:

`Error: getaddrinfo ENOTFOUND pet-api.example.com`  
`Could not send request`

(Screen zoom will be added later during editing.)

---

## 🎬 Clip 2 – Introduce MockNest Serverless

# 2. Introduce MockNest Serverless

## Narration

MockNest Serverless helps solve this problem by allowing you to mock external APIs directly in your AWS account.

MockNest runs on AWS Lambda and supports mocking:

- REST APIs
- SOAP web services
- GraphQL APIs

## On Screen

Open MockNest GitHub README.

Show:

- MockNest logo
- project description
- repository overview

Slow scroll for a few seconds.

---

## 🎬 Clip 3 – Simple Mock Example

# 3. Simple Mock Example

## Narration

MockNest exposes a WireMock-compatible API, so creating a mock is straightforward.

Let’s quickly create a simple mock.

## On Screen

In Postman, show a request to:

`POST /__admin/mappings`

Send request.

Then call:

`GET /pets`

Show the mocked response.

## Narration

This works well for simple mocks.

But for larger APIs creating and maintaining mocks manually becomes tedious.

---

## 🎬 Clip 4 – Introduce AI Mock Generation

# 4. Introduce AI Mock Generation

## Narration

This is where AI-powered mock generation becomes useful.

MockNest integrates with Amazon Bedrock to generate mocks automatically.

Let’s look at an example.

Imagine we have a pet adoption application that sends a newsletter with pets currently available for adoption.

This application depends on an external API that provides pet information.

Instead of manually creating mocks, we can generate them using AI.

## On Screen

In Postman, show a request to:

`POST /ai/generation/from-description`

Briefly show the request description field.

Send request.

---

## 🎬 Clip 5 – Generated Mock

# 5. Generated Mock

## Narration

MockNest uses AI to generate realistic WireMock mappings automatically.

## On Screen

Show response containing generated mappings.

Scroll slightly.

Highlight key sections briefly.

Do not stay too long.

(Screen zoom will be added later during editing.)

---

## 🎬 Clip 6 – Application Using the Mock

# 6. Application Using the Mock

## Narration

Now let’s see how an application can use this generated mock.

Our pet adoption application generates a newsletter email showing pets available for adoption.

## On Screen

Call the newsletter endpoint.

Show the generated email preview.

Highlight:

- dog image
- pet description
- generated data

(Screen zoom will be added later during editing.)

## Narration

The application now behaves exactly as if it was calling a real external API.

---

## 🎬 Clip 7 – Installation

# 7. Installation

## Narration

MockNest Serverless can be installed with one click using the AWS Serverless Application Repository.

## On Screen

Open AWS SAR page.

Show the MockNest application.

Highlight the Deploy button.

Do not show the full deployment process.

---

## 🎬 Clip 8 – Closing

# 8. Closing

## Narration

MockNest Serverless combines a serverless mock runtime with AI-powered mock generation.

It allows teams to test integrations reliably without depending on external services.

You can deploy it directly into your AWS account and start generating mocks immediately.

For more details, see the GitHub repository.

---

# Recording Plan

Record the demo in separate segments:

1. Problem introduction
2. MockNest introduction
3. Manual mock example
4. AI mock generation
5. Generated mock result
6. Application using the mock
7. SAR page
8. Closing

These segments will be combined during editing.

---

# Tools

Video recording and editing tools used for this demo:

## ScreenFlow

- screen recording
- webcam overlay (face bubble)
- zoom effects
- editing
- subtitles

## YouTube

- video hosting
- thumbnail selection

## Assets used

- MockNest logo
- GitHub README
- Postman requests
- Newsletter email example