# How I Built MockNest Serverless

## From AI-powered API mocking idea to AWS 10,000 AIdeas winner

![MockNestServerlessLogo.png](../images/MockNestServerlessLogo.png)

## What is MockNest Serverless?

Integration tests need stable dependencies. However, real external APIs rarely behave that way. External APIs are often unavailable, unreliable, or difficult to configure with the right test data in non-production environments. MockNest gives you a persistent, serverless mock server running in your own AWS account. In short, MockNest Serverless is:

- Open-source serverless API mocking for AWS.
- Deployed into your own AWS account rather than running behind a hosted control plane.
- AI-assisted mock generation from API specifications and natural-language instructions for REST, SOAP, and GraphQL.
- Supports callbacks and streaming.

### MockNest in Action

This demo shows how to set up consistent mock data across multiple endpoints of the same PetStore REST API, so that a client application can use the mocked API as if it were the real one.

The client application points to MockNest Serverless and calls two endpoints to generate a Pet Adoption Newsletter.

#### Steps in the Demo:
- Ask MockNest to generate mocks for four pets, including one that is available for adoption and has a picture.
- Import the generated mocks.
- Call the two mocked endpoints and verify that they return the expected responses.
- Call the client application, which is configured to use the mock endpoints instead of the real API, and generate a Pet Adoption Newsletter from the mocked responses.

[FinalClip4Take3.mp4](FinalClip4Take3.mp4)

Now you have seen what MockNest Serverless is in a nutshell, I will explain how I came up with the idea.

## The idea was already there

The idea for MockNest came from an earlier project where I had used a serverless approach to API mocking while working as a freelance engineer.

That implementation ran on Azure serverless compute, and it exposed two recurring problems: keeping mocks available across cold starts and maintaining large sets of mock mappings by hand. Those challenges made me think about building an open-source version for others who wanted to run a mock server on serverless infrastructure with persistent mocks, and about using AI to make mock creation and maintenance less manual.

The idea stayed on my backlog until AWS 10,000 AIdeas gave me a concrete reason to build it. This time, I would create an AWS-native version and make AI-assisted mock generation part of the design from the start.

The competition also gave me some useful constraints:

- Build with Kiro.
- Stay within the AWS Free Tier and available AWS credits.
- Work to a fixed deadline.

At first, my ambition was fairly modest: I hoped MockNest would make it into the first shortlist of 1,000 ideas so I could get the free Kiro credits and use them to build the open-source project. I honestly did not expect it to go much further.

Why did I think it had a good chance of making that first cut? I expected many submissions to be frontend-heavy applications focused on social-good use cases. MockNest was quite different, so I thought it might stand out and add some technical diversity to the pool.

Beyond that, though, I was much less confident. The next stage involved voting, and I was definitely not going to win a popularity contest with my backend tool...

## Building to a deadline

I work a 40-hour week and am _Married With Children_, so my free time was limited. I needed a deliberately staged plan to make sure I could meet the competition deadline:

1. **MVP** — core mocking engine + AI generation. First, make sure I had a valid product to submit and could continue in the competition.
2. **Quality** — tests + security. Add enough engineering depth and polish to give the project a stronger chance of progressing to the next stage.
3. **Evidence** — standards, checks, badges, and visible quality signals. Make the engineering effort obvious at a glance – no judge was going to sift through the entire codebase to work out whether it was well engineered. The same signals also help give potential users confidence that the open-source project is actively maintained, tested, and built with recognised engineering practices.
4. **Features** — work through the product backlog with whatever time remained. If I still had time, add more features that made MockNest more useful and gave it more of an edge over similar products on the market.

The repository also surfaced evidence such as release/version information, CI status, code coverage, CodeQL, OpenSSF Scorecard, OpenSSF Best Practices, and OpenAPI validation.

![Badg.webp](Badg.webp)

## How I used Kiro

I have not used Kiro before this competition. I mainly used IntelliJ because my programming language of choice is Kotlin. So first I read up on how Kiro is supposed to be used from Kiro's own documentation. Read about steering documents and started with them — product context, architecture, and engineering rules. I did not use Kiro the same way for every type of work:

- **Features** — spec-driven development. Create specs, review them, then generate design and tasks as my typical flow.
- **Bugs** — bug-fix workflow. In contrast, from feature building flow, it starts by recreating a bug with a unit test. Very useful in locating where the bug actually is instead of making any assumptions.
- **Experiments** — vibe coding when the direction was still uncertain. Nowadays, this option is no longer available; instead I use Quick Spec or Plan.

The principle was: **structure before code**. 

Every time Kiro generated something I needed to fix, I updated the steering. The goal was simple: make the next output better than the previous one.

Because Kiro is built on top of Visual Studio Code, I installed the JetBrains Kotlin plugin to get a better Kotlin development experience. Even then, it still did not feel quite the same as IntelliJ IDEA.

At first, I missed IntelliJ — especially for debugging and the general Kotlin coding experience. But as my steering improved, Kiro generated code that needed less manual editing. The less time I spent changing the generated code myself, the less I missed IntelliJ.

Eventually, I stopped missing it altogether.

## When AI became a workflow

The first important design decision was to treat AI generation as a workflow rather than a single model call. I wanted the mocks to always be valid. In order to make mock generation useful, the user should not need to correct the mocks. Therefore, the mocks must be validated against the API spec, and if there are any invalid mocks I wanted to give the system a configurable amount of retries with enriched context containing validation errors.

![koog-strategy-graph.png](koog-strategy-graph.png)

In short:
- The model generates candidate mappings from the API specification and user request.
- MockNest validates those mappings. 
- Invalid mappings and the validation errors are sent into the correction step.
- Validation runs again, retries are bounded to avoid cost and performance issues.
- Only valid mocks are returned.

## Prompt design

Prompt design became more specialised as the project evolved.

- API-specific prompts for **REST**, **GraphQL** and **SOAP**. This separation helped keep prompts concise and to the point, resulting in better quality output.
- User instructions describe the scenario that should be mocked.
- A compressed API specification gives the model enough context without sending unnecessary data or exceeding the token limit.
- Validation errors are passed to the correction step.

![SecureExamplePrompt.png](SecureExamplePrompt.png)

The recurring problem was familiar from traditional software development: **change one thing, break another?**

## Automating the checks

As the prompts and workflow grew, I realised I needed to automate the testing processes beyond unit and integration tests.

I introduced several layers of automated verification:

- **Graph tests** — deterministic test of the graph covering all paths, with no model calls.
- **Bedrock evals** — to test the quality of the output if I changed any prompts or wanted to compare LLM models for my use case. These are manually triggered _remocal_ tests in order to control the model cost.
- **Post-deploy end-to-end tests** — run in the pipeline after deployment. These tests covered all the happy flows and gave the system a sanity check after deployment.

![EvalResults.png](EvalResults.png)

## A bit of luck and perseverance

Luck definitely played a part in getting me into the Top 300, but so did perseverance.

This round was based on votes, and I knew I was unlikely to get that many. What helped was that not all of the original 1,000 selected participants actually completed and submitted their projects. That gave me a realistic chance of getting through with the number of votes my project received.

The next round after 300 was selected by judges, the Final 50. This was where I felt I had a better chance, because by then I had put a lot of engineering effort into the project.

But I have never treated the judging periods as downtime. Once the submission was in, I carried on building. While waiting to hear whether MockNest had made the Final 50, I had already started working through the next items on my backlog. One of them was improving cold-start performance with SnapStart. That turned out to be one of the items judges named in the improvement points.

## MockNest made the Final 50 - with feedback
![AppFeedback.png](AppFeedback.png)

Making the Final 50 was not just a milestone. It changed the priorities of what I was going to build next. Rather than concentrating on innovative features like lenient mocks, I re-prioritised the backlog to address as many items as possible with the least risk for the deadline.

Apart from cold-start performance (which I was already implementing), feedback included:
- Stronger production-grade security.
- More differentiation from existing mocking platforms.
- Advanced features such as callbacks/webhooks, streaming and MCP.
- More real-world validation and adoption.

Trust, security, performance, and callback webhooks where the least risky features I could complete in the two weeks before the final deadline.

Streaming and MCP carried too much implementation risk for the time remaining, though in hindsight streaming was relatively straightforward and was feasible if I had spent a bit less time on polish. 

## And then I ran out of free Kiro credits...

There was still work to do, but the free Kiro credits were all used up. I had a choice of paying for it myself or using AWS credits I received at the beginning of the competition. You can actually use the AWS account to pay for Kiro Subscription, which was very handy in this case; I still had plenty of AWS credits left, enough to pay for a one-month Kiro subscription.

## Kiro – a lean, mean feature-building machine

I needed to make Kiro more efficient in using credits so I could complete the project without running out of the remaining credits. It needed to become _A Lean, Mean Feature-Building Machine_. So I read up on how to make credit usage more efficient, which I did not do before I started the whole competition. This was my short list:

- Clean up steering: use leaner, more relevant context.
- Use the default model instead of the most expensive one for everything. Sometimes simpler models are actually better as stronger models might disagree with your requirements. So default actually lets Kiro optimise the choice of model for the task.
- Skip the politeness, remove the fluff, and talk like a caveman/cavewoman.

## Priorities changed

With limited time left, I focused on changes that would kill more birds with fewer stones:

- **SnapStart + priming** for performance, which I was already working on.
- **IAM** for stronger security and an AWS-native authentication option which additionally set MockNest apart from cloud-hosted solutions.
- **Callbacks** because asynchronous API behaviour was a gap that was very easy to close. IAM authentication could also be used on callback URLs as an authentication option, allowing MockNest to make callbacks to the AWS native API. Not an exciting feature – but a very useful and feasible one.
- **Streaming and other features** remained out of scope for the judging deadline.

In the end I addressed every bullet that judges asked for, just not every item in that bullet.

## Adding callback support
A typical use case for callback mocks is asynchronous processing. Imagine we want to add a pet to a pet adoption app asynchronously, so _add pet_ endpoint would return 202 Accepted status on our REST endpoint, and shortly after we would get an event on Amazon EventBridge confirming that the pet was added to the system. This demo video shows this callback mock scenario in action:

- Create a callback mock with HTTP response code 202 and a callback URL with IAM authentication enabled
- Call the mocked endpoint to create a pet and receive response code 202
- Check Amazon EventBridge for event confirming that the pet has been created

[CallBackDemo.mp4](CallBackDemo.mp4)

## SnapStart + priming: the optimisation that broke the system

I had a performance-test pipeline already in place, so I could establish a baseline before enabling SnapStart and priming and then run exactly the same tests again.

![PerfTestPipeline.png](PerfTestPipeline.png)

The expectation was: 
* Run baseline test, measure performance
* Implement SnapStart and Priming
* Run the same test again and see performance improvement

However... The pipeline was taking a long time. API Gateway, and then Lambda executions started timing out.

The optimisation exposed an unexpected interaction:

![SnapStartPrimingIssue.png](SnapStartPrimingIssue.png)

My code eagerly created a Bedrock connection before the snapshot. After restore, that captured state caused the request to hang until API Gateway reached its 29-second timeout followed by Lambda execution timing out.

The fix was **lazy initialization**. I blogged about this in [this post](https://builder.aws.com/content/3GZwmqjXS4kP2NlKHUNTkqilUNF/lessons-learned-shipping-a-koog-bedrock-ai-agent-to-production-on-aws-lambda/).

## The Final Architecture

![architectureOverview.png](architectureOverview.png)

At a high level, the final architecture looked like this:

- **AI mock generation** — an admin calls the generation endpoint through **Amazon API Gateway**. The request is handled by the AI mock-generation **AWS Lambda**, which uses **Amazon Nova Pro via Amazon Bedrock** to generate the mocks.

- **Mock administration** — MockNest exposes a broader admin API through **API Gateway** and the Mock Admin **AWS Lambda**. This includes importing and managing mappings, working with the request journal, and other administrative operations. Persistent data is stored in **Amazon S3**.

- **Mock execution** — when a client calls a mock endpoint through **API Gateway**, the Mock Server **AWS Lambda** retrieves the matching response from **S3** and returns it to the client. Incoming requests are also written to the **S3-backed request journal**.

- **Asynchronous callbacks** — if a mock is configured with a callback, the Mock Server places a message onto **Amazon SQS**. A separate asynchronous **AWS Lambda** consumes that message and invokes the configured callback target using the configured authentication method.

## MockNest Serverless became one of the 20 AWS 10,000 AIdeas winners.
Of course, the closer I got to the final 20, the more I hoped MockNest would be one of the winners.

The announcement came during the Dutch school holidays, while we were away in Greece. The winners were announced at night, and I woke up sometime after the announcement had gone live.

The first thing I did was check my email...

Nothing.

Usually, I had received an email about progressing to the next stage before the official announcement appeared on the Builder Center, so no email felt like a bad sign. But I was hoping they just had not announced yet..

I searched for the announcement anyway and found the list of winners. With a sinking feeling, I started scrolling through it, already assuming that no email meant no win.

Then, near the bottom of the list, I saw my name. 

![WinnerAnoucement.png](WinnerAnoucement.png)

What had started as an excuse to finally build an idea from my backlog had ended up as one of the 20 winning projects of AWS 10000 AIdeas Competition, and my first open source project.

## What I would do differently

Even though the project won, in hindsight, I would still improve several aspects of the development process.

### 1. Use more relevant context and less fluff

More context is not better context. I would be more deliberate about what Kiro sees and when.

### 2. Start eval-driven development earlier

I added systematic AI evaluation later than I should have. I would establish representative scenarios and measurable quality checks earlier in the project. This would save me a lot of time and effort.

### 3. Spend a little less time on polish and push more features

The quality work mattered, but under a fixed competition deadline I would rebalance some of the time spent on repository polish and badges toward shipping extra capabilities such as streaming. I implemented streaming after the win.

## Try MockNest Serverless

If MockNest sounds useful for your own integration tests, you can deploy it into your AWS account and try it with your own APIs.

MockNest Serverless is open source and available on GitHub. You can:

- Deploy it directly from the AWS Serverless Application Repository.
- Generate mocks from OpenAPI, GraphQL, or WSDL specifications.
- Use the Postman collection or curl examples and API documentation to get started.
- Browse the source code and architecture.
- Report issues, suggest features, or contribute to the project.

And if you do try it, I would really like to hear how you used it and what you think MockNest should support next.
Check out MockNest Serverless from the open source GitHub repositor: https://github.com/elenavanengelenmaslova/mocknest-serverless