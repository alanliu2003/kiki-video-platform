# [AGENTS.md](http://AGENTS.md)

## Purpose

This repository is **kiki-video-platform**, a Bilibili-inspired full-stack video platform.

This file defines repository-wide rules for AI coding agents such as Cursor.

Agents must read and follow this file before making changes.

Milestone-specific prompts define the requested feature scope. This file defines the persistent engineering, safety, Git, configuration, testing, and documentation rules that apply across all milestones.

---

# 1. Core Working Principles

When working in this repository:

1. Inspect before editing.
2. Understand existing architecture before introducing new patterns.
3. Preserve working behavior unless the task explicitly requires changing it.
4. Prefer simple, explicit designs over speculative enterprise abstractions.
5. Do not implement future milestones early.
6. Do not claim something works unless it was actually verified.
7. Keep changes scoped to the current milestone.
8. Avoid unrelated refactors.
9. Do not silently change important architectural decisions.
10. Leave the repository in a clean, buildable state.

When a task has meaningful architectural tradeoffs, briefly explain the chosen approach before implementing it.

---



# 2. Mandatory Pre-Change Inspection

Before modifying code for any non-trivial task, inspect:

- current Git branch
- `git status`
- relevant existing modules/files
- existing implementation of the feature being changed
- relevant tests
- Flyway migrations
- environment/configuration examples
- architecture documentation
- existing Cursor rules
- this `AGENTS.md`

Do not assume paths or architecture from a prompt if the repository differs.

For milestone work, confirm that the branch is based on the latest intended `main`.

Do not rewrite history from previous milestones.

---



# 3. Environment Files — Critical Rule

`.env` files are user-owned local configuration.

## NEVER modify `.env`

Agents must never:

- create `.env`
- edit `.env`
- overwrite `.env`
- rename `.env`
- delete `.env`
- auto-format `.env`
- append values to `.env`

This applies to:

```text
.env
backend/.env
frontend/.env
any nested .env file
```

