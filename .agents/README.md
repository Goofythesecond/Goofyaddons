# AI assistant setup (Google Antigravity)

An AI **debugging partner and teacher** for this mod. It finds problems, explains them,
answers "how do I…" questions and simulates code. **You write the code.**

## What's here

| File | Loaded | Purpose |
| --- | --- | --- |
| `../GEMINI.md` | always | Core rules: no code writing, no assumptions, ask before acting, clarify vague prompts, mode picker |
| `skills/bug-hunt/` | when needed / `/bug-hunt` | Read logs and crash reports, trace data backwards, report where + why |
| `skills/teach/` | when needed / `/teach` | "How do I / what function": verified answer + small example in this project |
| `skills/simulate/` | when needed / `/simulate` | Static trace tables for many inputs; optional runnable test in `tests/` |
| `skills/debug-lines/` | when needed / `/debug-lines` | Plan + add removable `// DEBUG(ai)` log lines that don't flood or crash |
| `skills/personalize/` | only `/personalize` | End-of-session wrap-up: saves what you taught it, retires outdated memories |
| `memory/INDEX.md` | start of each task | One line per memory (generated, don't edit). Full entries in `memory/entries/` load only when relevant |
| `references/codebase-index.md` | on demand | Map of every file, the state machine, data shapes, crash history, unverified leads |
| `references/server-context.md` | on demand | Hypixel Bazaar mechanics, **real chat formats from your logs**, API notes, MC 26.1.2 facts |
| `references/comment-style.md` | on demand | How comments in shown code must look |
| `references/dop-style.md` | on demand | Data-oriented style for shown code |
| `hooks.json` + `hooks/guard.py` | every write/command | Forces a permission prompt on **every** file write and on any command that could change something |
| `gem/` | — | Copy-paste version for a Gemini web Gem |

## Use it

Just talk normally, even vaguely ("it got stuck in STORE again"). It will restate what it
thinks you want and ask for anything missing. To force a mode, start with `/bug-hunt`,
`/teach`, `/simulate` or `/debug-lines`.

## Teach it (memory)

Work normally. Correct it, overrule it, explain why things aren't bugs. At the end of a
session, run **`/personalize`**. It will:
1. go over the session and propose memories (not-a-bug, decision, fact, correction, preference),
2. run `memory.py check` and flag outdated ones (their code changed, or unused 30+ days),
3. show you one table and **wait** — you pick what to save, update or archive,
4. write only what you approved (each write = a permission prompt).

Next session it reads `memory/INDEX.md` (~30 tokens per memory) and opens a full entry only
when it's about the code you're working on.

Check memory health anytime (read-only, no prompt):
`python3 .agents/skills/personalize/scripts/memory.py check`

## Check that the guard works (do this once)

Ask the agent: *"write the word hi into tests/guard-check.txt"*. You should get a permission
prompt starting with **GoofyAddons guard**. Deny it. If no prompt appears, the hook isn't
loaded. Check Antigravity's Customizations panel, and that `python3` is on your PATH.

## Keep it accurate

- `codebase-index.md` describes commit `9335bfd`. After big changes, ask:
  *"re-read src and tell me what's outdated in .agents/references/codebase-index.md"*. Review the
  suggested changes and approve the write.
- When Hypixel changes a menu or a chat message, update `server-context.md` §3 with the
  real line from `run/logs/latest.log`.
- If you use the Gem, re-upload changed files to it.
