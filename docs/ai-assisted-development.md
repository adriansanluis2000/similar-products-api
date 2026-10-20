# AI-assisted development

I built this solution with an AI coding agent, Claude Code, working under my direction. I wrote the specification, reviewed every phase, took the decisions and approved every commit, push and tag. This page describes how that worked, including where the agent got things wrong and how they were caught.

## The method

Before any code, I wrote a specification for the agent. The specification itself stays out of the repository, as it was written for the agent and not as project documentation. It covered:

- the goals and what the reviewers of the test look at;
- the requirements of the statement, each traced to the phase and the test that checks it;
- the expected behaviour against every scenario of the mocks;
- the architecture, the performance and resilience targets, and a plan in seven phases, each with its suggested commits;
- the working rules for the agent.

The working rules shaped the whole project:

- **Phase by phase, with a gate at each end.** Every phase starts with a plan: what changes, which files, which new dependencies (with exact versions and why) and which decisions are open, each with a proposed default. Nothing starts until I approve the plan. Every phase ends with the verification output, a summary and a proposal of commits.
- **Nothing irreversible without explicit approval.** No commit, push or tag until I say so.
- **A history that tells the story.** Small, atomic commits in Conventional Commits format, each compiling and passing the tests, so the history can be bisected.
- **Versions from the source, not from memory.** The latest stable versions were checked on Maven Central or start.spring.io. If something was not compatible, the agent had to stop and propose an alternative.
- **Decisions written down.** Each relevant decision goes into an ADR in the same commit as the change.
- **Stop when reality disagrees.** If versions, the mocks or a library behave differently from the specification, stop and ask instead of improvising.

## How each commit was verified

The agent did the work of a phase in the working tree, but did not commit as it went. Before proposing the commits, it saved the state each commit would have as a Git tree, then extracted each tree into an empty folder and ran `./mvnw verify` there. Every commit in the history was built and tested on its own before it existed, and the commits were then created from exactly those trees.

Performance claims follow the same rule: every stage in [`performance.md`](performance.md) was measured with the load test of the statement, using a script written for that purpose, before its commit was proposed.

## Where review and data changed the course

The agent was fast and thorough, but not always right. These are the moments that mattered most.

- **A wrong assumption, caught before it was published.** In the first phase, the agent concluded that the statement's files used Windows line endings and built a careful `.gitattributes` to preserve them. It was a misreading caused by a quirk of `grep` in Git Bash: the files used Unix line endings all along. The agent found the mistake while verifying its own commits. Nothing had been pushed, so with my approval it rewrote those four local commits instead of leaving a false claim in the history.
- **Variance that was not variance.** Two runs of the same code gave very different latencies. Instead of averaging them, we kept measuring and found that the mock itself slows down as it serves requests. From then on, the script restarts it before every run, and the baseline was measured again under the same conditions.
- **Not optimizing what data says will not move.** After adding the cache, the slow scenarios still had a p95 at the time budget. The agent considered extra logic to avoid waiting for products known to be slow, but first checked InfluxDB per second. That showed only the first attempt at a slow product makes requests wait, which no logic can avoid. The extra code was never written, and the reason is recorded in the performance notes.
- **Tests that pass for the wrong reason.** A cache test first passed only because the fake upstream answered at once, which hid the behaviour that matters in production: serving the old value while the new one is fetched. The test was rewritten to keep the refresh pending, as a real HTTP call would.
- **A library that contradicted its documentation.** Spring AI's metadata says the MCP server uses streamable HTTP by default, but the endpoint answered 404. The condition report of Spring Boot showed that it falls back to the older SSE transport unless the protocol is set explicitly.
- **Problems in my machine, not in the code.** My antivirus intercepted HTTPS, which broke the Maven build inside Docker, and rewrote HTTP responses on `localhost`, which broke the MCP client. The agent did not work around them in the code. It traced the first to the antivirus's own certificate, and proved the second by repeating the call between two containers, where the response was correct. I then excluded the affected addresses in the antivirus. Both cases are in the README's troubleshooting section for anyone with the same setup.

## What I took from it

- The gates were worth their cost. Plans with explicit open decisions and proposed defaults made reviewing quick, and they are where most of my decisions were taken: Java 25, partial answers and status codes, cache lifetimes, what to leave out of the scope.
- Asking for evidence beats trusting fluent explanations. The best catches above came from asking for measurements, isolated builds and minimal reproductions, not from reading the code more carefully.
- The agent was most valuable on the work that is tedious to do well: checking versions, keeping every commit green, writing the ADRs while the context was fresh, and measuring every stage the same way.
