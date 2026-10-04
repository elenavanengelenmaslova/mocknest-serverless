# Kiro in a Real Project: Things I Wish I Knew Before I Started

## Turning Kiro into a Lean, Mean Feature-Building Machine

I use Kiro every day to build an agentic system for a startup.

When I started, I did not research too much about how to best set up Kiro for the job: I read enough of the documentation to get going, created some steering files, picked the strongest model available, and started building.

It worked, features got built.

But my Kiro setup was nowhere near as efficient as it could have been.

I ran out of credits. Some operations took far longer than they should have. Kiro managed to touch files it really should not have touched. Security issues slipped in that should have been caught earlier. And as the system evolved, some of my carefully written steering became outdated and started giving the agent the wrong context.

Today my Kiro setup looks very different from the one I started with.

These are the things I wish I had known before I started.

---

## Set up `.kiroignore` before the first big refactoring

For a while I thought Kiro has a bug.

Every time Kiro renamed a class or moved a package around, my workspace started behaving strangely afterwards. gradle build would not run and git commands would take a very long time or timeout.

A couple of times I was forced to delete my project locally and checkout the remote version again to get things working again.

It was not a bug though, Kiro was simply being *too* thorough.

When it renamed or moved something, it also updated references in places it should never have touched, including files inside `.git/`, and Gradle caches.

Those files belong to Git and the build tool, not to me and not to the coding agent.

The fix was a `.kiroignore` file.

Do not stop at the examples from the documentation. Add the internals and caches that are specific to your stack as well.

This is what I use in this project:

```gitignore
# Gradle cache and build artifacts
.gradle/
build/
.kotlin/

# Git internals
.git/

# IDE and editor files
.vscode/
.idea/

# Other common exclusions
*.class
*.jar
*.war
*.log
```

Change it to match your stack:

```text
node_modules/
dist/
.venv/
target/
```

The important part is to do this on day one, before the first large rename, package move, or refactoring.

> **SCREENSHOT PLACEHOLDER — broken workspace / unexpected Git changes**  
> Add a screenshot showing the kind of strange Git status or IDE errors that happened before `.kiroignore` was configured.

---

## My first steering files were far too ambitious

When I first set up steering, I tried to give Kiro as much context as possible.

That sounded sensible.

My steering documents contained product positioning, competitor comparisons, long-term product ideas, and other background that might have been useful while brainstorming.

Most of it was useless while implementing a feature.

Worse, it was being pulled into requests where it added context cost without helping Kiro write better code.

I eventually reduced the setup to three main files:

- `product.md`
- `structure.md`
- `tech.md`

The goal became much simpler: tell Kiro how this codebase works, what conventions it must follow, and what rules it must not break.

More context is not always better context.

### Generate steering, but review it

You do not have to write steering documents from scratch.

Kiro can generate them from your codebase, and there are ready-made prompts such as the Kiro project initialization prompt from the AWS Startups prompt library.

But read any generated prompt or generated steering before accepting it.

A generic prompt gives you generic steering.

> **SCREENSHOT PLACEHOLDER — AWS Startups Kiro project init prompt**  
> Add a screenshot of the AWS Startups prompt library page showing the Kiro project initialization prompt.

### Use inclusion modes deliberately

Not every steering file should apply to every task.

Use:

- `always` for rules that really are global
- file-matching inclusion for things such as test conventions or infrastructure rules
- manual inclusion for context that is useful only occasionally

That keeps irrelevant instructions out of everyday requests.

> **SCREENSHOT PLACEHOLDER — Kiro steering inclusion mode**  
> Show the UI/config where `always`, file match, manual, or equivalent steering inclusion options are configured.

### Treat `.kiro` like software

It is tempting to keep adding more steering files, hooks, and skills.

But everything in `.kiro` becomes configuration you have to maintain.

Kiro changes. Models change. Your architecture changes. Your own project rules change.

A steering instruction that was useful three months ago can become misleading today.

The leaner the setup, the easier it is to maintain it.

### Every correction is a steering candidate

One habit helped me more than almost anything else.

Whenever I had to correct Kiro, I asked myself:

> Would a steering rule have prevented this?

If yes, I updated the steering.

That way each correction improved the next result instead of becoming something I had to explain again in another chat.

---

## Hooks are useful, but they can quietly burn time and credits

I added hooks because I wanted more automated checks around generated code.

That part worked.

But hooks can also make Kiro feel unexpectedly slow if they fire too often.

Each hook starts an agent action. If a heavy security or cost review is triggered on every file save, and a feature modifies 30 files, you can easily end up with 30 queued reviews.

That is not a good use of time or credits.

The worst version is when a hook modifies files itself and those modifications trigger the hook again.

Now you have created a loop.

### Security checks were worth automating

One of the areas where automatic review was especially useful was security.

In a cloud-native project, generated code can look perfectly reasonable while still introducing problems such as:

- overly broad IAM permissions
- overly broad CI/CD permissions
- missing validation
- unsafe handling of external input
- secrets or sensitive values in configuration

I had security findings in the agentic system that made me realise this kind of review should happen systematically rather than only when I remembered to ask for it.

> **SCREENSHOT PLACEHOLDER — Kiro security hook configuration**  
> Show the hook that performs a security review, including its trigger and file scope.

> **EXAMPLE PLACEHOLDER — real security finding from the agentic system**  
> Add one concrete example, such as an overly broad IAM permission or pipeline permission, and show what Kiro originally generated versus the corrected version.

### Cost should be part of feature design

The same applies to cost.

In a serverless system, technical design decisions have direct cost consequences:

- Lambda memory and execution time
- S3 requests
- Bedrock tokens
- CloudWatch log volume
- data transfer
- retry behaviour

A cost review is most useful while the feature is being designed, not after the AWS bill arrives.

> **SCREENSHOT PLACEHOLDER — cost review hook**  
> Show the Kiro hook or prompt that asks for a cost impact review.

> **EXAMPLE PLACEHOLDER — cost review that changed a design decision**  
> Add a real example from the agentic system where the cost review caused you to change an implementation or architecture choice.

### What works better for me

I now keep hooks narrow:

- Use specific file patterns.
- Run heavy checks when a task finishes or manually before a PR.
- Keep hooks focused on one responsibility.
- Avoid hooks that run on every save unless they are genuinely lightweight.

---

## I stopped using the strongest model for everything

My original thinking was simple:

> The strongest model should save me the most time.

So I picked the most capable model available and used it for nearly everything.

That turned out to be unnecessary.

You do not need the most expensive model to rename a field, create a straightforward test, or update documentation.

For everyday feature work, Kiro's automatic model selection was good enough and much more economical.

I now normally leave model selection on Auto and only switch manually when I have a specific reason.

There is another subtle advantage: stronger models can sometimes be more argumentative about requirements and try to reinterpret them. For routine implementation work, that is not always what you want.

> **SCREENSHOT PLACEHOLDER — Kiro model selector**  
> Show Auto model selection alongside the manually selectable models.

---

## Keep features small enough that you can still review them

Kiro is perfectly capable of taking a large spec and generating a very large change.

The problem appears afterwards, when you have to review it.

A two-thousand-line AI-generated change is not really reviewable.

You either skim it and miss things, or spend a large amount of time checking code you did not write yourself.

I learned to keep features roughly story-sized.

Smaller features give you:

- smaller PRs
- easier code review
- faster feedback
- lower risk when the design changes
- less wasted work if you change direction

That last point matters more with AI-assisted development than I expected.

You often discover something while building that changes how the next part should work.

With a small feature, you throw away a little work.

With a giant spec, you can end up throwing away a week.

> **SCREENSHOT PLACEHOLDER — Kiro spec with small task breakdown**  
> Show an example requirements/design/tasks view where a feature has been split into manageable tasks.

---

## Use the flow that matches the work

I did not use the same Kiro workflow for everything.

For new features I typically used spec-driven development:

1. create requirements
2. review them
3. generate & review the design
4. generate & review tasks
5. execute the tasks

For bugs, I preferred the bug-fix workflow.

That flow starts from reproducing the bug, usually with a test, rather than assuming where the problem is.

That is much closer to how I want debugging to work.

For uncertain ideas or experiments, I prefer lighter planning instead of creating a full specification too early.

> **SCREENSHOT PLACEHOLDER — Kiro workflow choices**  
> Show the available Kiro options for spec-driven development, bug fixing, quick spec/plan, or equivalent current flows.

---

## Keep requirements, design, and tasks in sync

This one caught me out when a feature changed while I was building it.

Kiro can generate requirements, then design, then tasks.

But if you later change the requirements and do not update the design and task list, the agent can continue implementing the old idea.

That gives you a strange situation where the documentation looks partly correct but the execution plan is stale.

Whenever I change requirements after task generation, I now explicitly ask Kiro to update the dependent design and tasks as well.

> **SCREENSHOT PLACEHOLDER — Kiro requirements/design/tasks**  
> Show the three related artefacts for a feature, ideally with the task list visible.

---

## Run Kiro in parallel when the work is independent

You do not have to wait for one long-running task before doing anything else.

I often work with multiple Kiro sessions in parallel for independent work.

For example:

- one window builds a feature
- another handles a different branch or project
- while one agent is working, I review the output of another

This works especially well when combined with separate branches or Git worktrees.

The important part is to keep the work isolated so that two agents are not modifying the same files at the same time.

> **SCREENSHOT PLACEHOLDER — multiple Kiro windows / worktrees**  
> Show your actual setup with two Kiro windows or two independent project/worktree sessions.

---

## Connect Kiro to AWS properly

If Kiro is helping you build on AWS, give it access to current AWS knowledge instead of relying only on model training data.

AWS provides an Agent Toolkit setup prompt that configures coding agents with up-to-date AWS documentation, tested procedures, and IAM guardrails.

The setup can be generated from the AWS account home page and used with Kiro and other coding agents.

As with any setup prompt: read it before running it.

Do not blindly paste infrastructure or permission configuration into an agent just because AWS generated it.

> **SCREENSHOT PLACEHOLDER — AWS account Agent Toolkit prompt**  
> Add a screenshot of the AWS account page showing **Agent Toolkit for AWS**, the **Get setup prompt** action, and the text that it works with Kiro / Claude Code / Codex or similar supported agents.

> **SCREENSHOT PLACEHOLDER — setup prompt inside Kiro**  
> Optional second screenshot showing the generated AWS setup prompt being used in Kiro.

---

## And then I ran out of Kiro credits

Eventually I ran out of Kiro credits.

That was the point where I finally spent time improving how I used Kiro instead of only focusing on the product I was building.

I still had AWS credit vouchers available, and fortunately Kiro subscriptions can be paid through an AWS account.

That meant I could use AWS credits instead of paying separately.

I did not want to spend extra money on top of what I already had, so this was a good fit.

> **SCREENSHOT PLACEHOLDER — Kiro subscription through AWS**  
> Show the AWS/Kiro subscription page or billing flow that demonstrates Kiro being paid through the AWS account.

If you are building a startup, it is also worth checking whether you qualify for Kiro for Startups.

---

## What my Kiro setup looks like now

Over time, my approach became much simpler.

I try to give Kiro exactly the context it needs, automate the checks that are worth automating, and avoid making the configuration itself complicated.

My current rules are:

- Set up `.kiroignore` immediately.
- Keep steering small and relevant.
- Use inclusion modes so context is loaded only when needed.
- Update steering whenever a repeated correction appears.
- Treat `.kiro` configuration as code that needs maintenance.
- Use focused hooks for security and cost.
- Avoid broad hooks that fire constantly.
- Leave model selection on Auto unless there is a reason not to.
- Keep features small enough to review.
- Use the workflow that matches the task.
- Keep requirements, design, and tasks synchronized.
- Run independent sessions in parallel.
- Connect Kiro to current AWS guidance.
- Use AWS credits for Kiro if you already have them available.

The biggest lesson was not that Kiro needed more configuration.

It needed **less, but better, configuration**.

The more deliberately I controlled context, scope, triggers, and feature size, the more useful Kiro became.

That is how it went from something I was experimenting with into what I wanted it to be from the start:

**a lean, mean feature-building machine.**

---

## Links and references

- Kiro project init prompt: https://startups.aws.com/prompt-library/kiro-project-init
- Mastering agent hooks in Kiro: https://builder.aws.com/content/3EsR3fitIynXrzAyxYqY1aKqz7B/mastering-agent-hooks-in-kiro-event-driven-automation-for-professional-workflows
- How to pay for Kiro with AWS credits: https://builder.aws.com/content/3DPhbEdo3a6b2kO0OttZhapOnIZ/how-to-pay-kiro-subscription-with-aws-credits
- Kiro for Startups: https://kiro.dev/startups/
