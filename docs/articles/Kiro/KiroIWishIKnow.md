# Kiro in a Real Project: Things I Wish I Knew Before I Started
## Turning Kiro into a Lean, Mean Feature-Building Machine!

# What is Kiro?
I am using Kiro daily for building an agentic system for a startup. When I started I installed Kiro, and followed basic setup instructions for steering. I did not stop and research how I should best create steering docs, so therefore they had a lot of fluff, such as market position of the product, coparison with competitors etc. This kind of information might be useful if you are brainstorming new features, what to build but is not needed to actully implement features. 

Additionally, I thought I should pick the best model for everything. I thought an Opus 4.8 (at the time best available) will save me more time building. I did not set up and Skills or Hooks and did not have any filtering when to apply steering files so they would apply to everything. This hurried setup let me to:
1. Run out of Kiro credits
2. Have very long execution time for building featres
3. Security findings in my code, including too brawd IAM permissions, too brawd pipeline permissions
4. Corrupt git or build tool, untill i worked out how to avoid it with kiroignore i had to nuke the project and check it out again every time it happend
5. Steering going out of date with my product and therefore giving wrong / out of date / irrelevant context
6. When I would come to new insights while building a feature, was tricky to update requirements /desight and tasks in a structured way, although kiro does provide autogen of design and tasks. i could change requiremet but then tasks and design could go out of date


## Why set up Kiro ignore from the start

For a while I thought something was wrong with my machine. Every time Kiro renamed a class or moved a package around, my workspace started acting strangely afterwards. The IDE showed errors that were not there, Gradle builds failed for no reason, and git started reporting changes I never made. A couple of times I was sure my local repository was corrupted.

My repository was fine. Kiro was being *too* thorough. When it renamed or moved something, it also updated every reference it could find, and that included files inside `.git/`, the Gradle cache and the Kotlin build output. Those files are not yours to edit, and neither is the agent's. Git and the build tool manage them, and editing them by hand (or by agent) is a quick way to get a broken workspace.

The fix is a `.kiroignore` file. Don't stop at the examples in the docs. Also add the git internals and your build tool's caches. This is what I use in this project:

```gitignore
# Gradle cache and build artifacts
.gradle/
build/
.kotlin/

# Git index and internal files
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

Change it to fit your stack (`node_modules/`, `dist/`, `.venv/`, `target/`, ...), but do it on day one, before the first big refactoring.

> TODO: add how long it took before I found the cause / a screenshot of the weird git status

## Steering: generate it, keep it lean, keep correcting it

You don't have to write steering documents by hand. Kiro can generate them from your codebase. You can also start from a ready-made prompt like the [Kiro project init prompt](https://startups.aws.com/prompt-library/kiro-project-init) from the AWS Startups prompt library. Always **read the prompt before you run it** and adjust it to your project. A generic prompt gives you generic steering.

### Lean beats complete
My first steering docs read like a pitch deck: market position, competitor comparison, the long-term vision. That is useful when you are brainstorming what to build. It does not help an agent implement a feature, and it costs context on every single request. Steering should describe how *this* codebase works: the tech stack, the structure, the conventions and the rules that cannot be broken. In this project I ended up with just three files: `product.md`, `structure.md` and `tech.md`.

Also use the inclusion settings. Not every steering file has to apply to every task. Use `always` only for what is really global. Use file-match inclusion for things like "how we write tests" or "how we write infrastructure", and manual inclusion for the rest.

### Everything in `.kiro` is software you have to maintain
It is tempting to configure a lot: many steering files, many hooks, many skills. But everything in `.kiro` works like any other code or config. If you don't maintain it, it starts working against you. Kiro changes, the models change, and your project changes. Instructions that helped three months ago can push the agent in the wrong direction today. The smaller your setup, the cheaper it is to keep up to date. Lean is the way to go.

### Every correction is a steering update
Every time you have to correct what Kiro produced, ask yourself: *would a line in steering have prevented this?* If yes, add it, or ask Kiro to update the steering for you. That way every correction makes the next result better, and you don't have to repeat yourself in every chat.

## Hooks from AWS prompt

Agent hooks run an agent action automatically when something happens in the IDE, for example when a file is saved or created, when a prompt is submitted, when the agent finishes, or when you trigger them manually. They are a great way to add automatic checks to your workflow. (Recommended deep dive: [Mastering agent hooks in Kiro](https://builder.aws.com/content/3EsR3fitIynXrzAyxYqY1aKqz7B/mastering-agent-hooks-in-kiro-event-driven-automation-for-professional-workflows).)

### I had to fix security issues

A hook that reviews each new feature for security problems pays for itself quickly: missing input validation, overly broad IAM permissions, secrets in config, unsafe parsing of user input.

> TODO: the concrete security issues the hook caught in MockNest

### Cost is something we must think about when designing any feature

In a serverless project, every design decision has a price tag: Lambda memory and duration, S3 requests, Bedrock tokens, log volume. A hook that asks "what will this cost at scale?" for each feature makes you think about cost while you design, not when the bill arrives.

> TODO: example where the cost hook changed a design decision

### Hooks can slow you down if configured incorrectly

Each time a hook fires, it starts an agent run. That run takes time and uses credits. If you attach a heavy check to a broad trigger, for example "on every file save, for `**/*`", then a single spec task that touches 30 files triggers 30 security reviews. They queue up behind each other and compete with the actual work. The agent slows down and your credits disappear. It gets worse if the hook edits files itself: those edits can fire the hook again, and you end up in a loop.

What works for me:
- **Narrow the file patterns.** Only fire on the files the check is about (e.g. `**/*.kt` or infrastructure templates), not on everything.
- **Choose the right trigger.** Heavy reviews like security and cost don't need to run on every keystroke. Run them when the agent finishes a task, or trigger them manually before you open a PR.
- **Few, focused hooks.** One hook with one clear job is easier to reason about, and to maintain, than ten overlapping ones.

## Model selection

I started by picking the most expensive model for everything, because "the best model will save me the most time". In practice, **Auto** model selection worked just as well for my day-to-day feature work and was much more economical. Kiro picks the right model for the job. You don't need the top model to rename a field or write a unit test. I now leave it on Auto and only switch manually when I have a good reason.

## Keep features small

Break features down into story-sized chunks. A spec that produces a two-thousand-line change is not reviewable. You either skim it (and miss things) or you spend a day on it. Small features keep PRs small and code review workable.

Small features also keep you flexible. Ideas change while you build them. When a feature is small, changing the flow or the direction of the epic means throwing away a little work, not a week of it.

## Which flow to choose

Kiro offers different flows: spec-driven development for new features, plus separate flows for refactoring and bug fixes. Use the one that fits the work. A bug fix does not need a full requirements document.

One thing to watch out for: if you update the requirements or design *after* the tasks have been generated, make sure everything is back in sync. Ask Kiro to update the design and the task list too, or the tasks will implement the old idea.

## Run Kiro in parallel

You don't have to wait for one task to finish before you start the next. You can run Kiro for more than one project or feature at a time, for example in a separate window per project or per feature branch. While one spec is executing, you can review the result of another.

> TODO: describe my setup (separate windows / git worktrees)

## Connect AWS account

You can connect Kiro to your AWS account using the predefined setup prompt on the AWS account home page (Agent Toolkit for AWS). It gives your coding agent up-to-date AWS documentation, tested procedures and IAM guardrails for safe resource access, all configured through a single prompt in your IDE. Same advice as with any prompt: read it before you run it.

## Paying for Kiro
### I ran out of Kiro credits

At some point I ran out of Kiro credits. I didn't want to spend extra money, but I had some AWS credit vouchers. It turns out you can pay for your Kiro subscription through your AWS account, and so with AWS credits. This guide walks you through it: [How to pay for a Kiro subscription with AWS credits](https://builder.aws.com/content/3DPhbEdo3a6b2kO0OttZhapOnIZ/how-to-pay-kiro-subscription-with-aws-credits).

If you are building a startup, also check out [Kiro for Startups](https://kiro.dev/startups/).

## Summary

What I wish I had known on day one:
- Set up `.kiroignore` right away, including git internals and build caches.
- Generate steering, then keep it lean and update it every time you correct the agent.
- Treat everything in `.kiro` as code you have to maintain.
- Use hooks for security and cost checks, with narrow triggers so they don't slow you down.
- Leave model selection on Auto.
- Keep features story-sized.
- Run several Kiro sessions in parallel.
- Connect Kiro to your AWS account, and pay with AWS credits if you have them.

> TODO: link to MockNest Serverless and other projects; mention Reddit / Discord for reporting problems

---

## Reference notes (delete later)

## Connect AWS account
Give your coding agent current AWS knowledge and safe resource access

Agent Toolkit for AWS  provides up-to-date documentation, tested procedures, and IAM guardrails configured through a single setup prompt in your IDE.

Get setup prompt
Free
|
Works with Kiro, Claude Code, Codex and more


Install plugins for your Development env
add kiroignore, not only what it sais in docs but also git index, build tool cache
Defining steering documents: generation and template ai ask: https://aws.amazon.com/startups/prompt-library/kiro-project-init
Redefining steering documents
Spec-driven development / Refactor / Bug flows
When updating requirements or design after tasks are generated, make sure all in synch
Smaller features, keep PRs small, try to break down features in story size chunks
Reddit to report problems, althoug Discord also available
Summary and point again at my project and link to other projects
Pay with aws credits https://builder.aws.com/content/3DPhbEdo3a6b2kO0OttZhapOnIZ/how-to-pay-kiro-subscription-with-aws-credits
Kiro for startups https://kiro.dev/startups/
https://builder.aws.com/content/3Cfd1ooIQMvUrrUIzTZB8fP3OB1/set-up-kiro-right-build-a-portfolio-site-fast-the-configs-worth-your-time
Agend hooks: https://builder.aws.com/content/3EsR3fitIynXrzAyxYqY1aKqz7B/mastering-agent-hooks-in-kiro-event-driven-automation-for-professional-workflows
