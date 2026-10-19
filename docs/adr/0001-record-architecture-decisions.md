# 0001. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

Reviewers read both the code and the git history of this repository. The code shows *what* was built, not *why*: which alternatives were weighed, which data backed a choice, or which trade-offs were accepted. That reasoning has to be written down next to the change it explains.

## Decision

We record every significant decision as a short Architecture Decision Record in `docs/adr/`. ADRs follow [Michael Nygard's format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) with an extra *Alternatives* section, as in the [template](template.md).

- Files are numbered in sequence: `NNNN-title-in-kebab-case.md`.
- An ADR is committed together with the change it describes.
- Accepted ADRs are not rewritten. A new ADR supersedes the old one, which only gets its status updated with a link to the new one.
- The [index](README.md) lists every ADR with its status.

## Alternatives

- **Decisions in the README only.** Simple, but the README describes the current state and loses why things changed.
- **Commit messages only.** Hard to find later, and too short for alternatives and consequences.
- **External wiki.** Lives apart from the code and drifts out of date.

## Consequences

- Each decision has a stable, linkable place, and the history shows when it was taken.
- Every relevant change carries the small cost of writing an ADR, so ADRs stay short.
- The README links to the ADRs instead of repeating them.
