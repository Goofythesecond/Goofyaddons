---
name: personalize
description: >-
  End-of-session wrap-up that makes the assistant learn from the developer. Reviews the
  conversation for corrections, dismissed findings, decisions where the developer overruled
  the AI, facts and preferences the developer taught, and saves them as small memory entries
  that future sessions load only when relevant. Also finds and retires outdated memories.
  Use only when the developer runs /personalize or says "wrap up" / "save what you learned".
---

# Personalize — learn from this session

Memory is how you get better for **this** developer. It must stay **small, true and
relevant**: one wrong or outdated memory makes you worse than no memory at all.

Files:
- `.agents/memory/INDEX.md` — generated list, read at the start of every task (keep it small)
- `.agents/memory/entries/M-xxx.md` — one memory each, opened only when relevant
- `.agents/memory/archive/` — retired memories, never loaded
- Template: [templates/entry.md](templates/entry.md)
- Tool: `python3 .agents/skills/personalize/scripts/memory.py` (`check`, `next-id`, `apply`, `index`)

Memory is only written here, during `/personalize`, never during normal work.

---

## Step 1 — Harvest the session (read-only)

Go back over **this whole conversation** and list moments of these 5 kinds:

| Type | Look for | Example (made up — not real facts) |
| --- | --- | --- |
| `not-a-bug` | You reported a problem; the developer said it isn't one and explained why | "slot 35 is always filled on Hypixel's order screen, so isMenuLoaded(35) is fine" |
| `decision` | You said "I don't agree"; the developer kept their solution | developer kept the counter-based move check over your event-based idea |
| `fact` | The developer taught you something about the game, server, or their setup | "/ec 2 opens page 2 only if the player bought it" |
| `correction` | You were wrong about code, an API, or a method name, and got corrected | you used a Yarn name that doesn't exist in 26.1.2 |
| `preference` | How the developer wants you to work or explain | "show the trace table before the explanation" |

Also include **your own mistakes** you noticed, even if the developer didn't point them out.

Then **filter hard**. Keep a moment only if all are true:
1. **It would change what you do next session.** If not, drop it.
2. **It's not already written down** in the code, a comment, git history, `codebase-index.md`,
   `server-context.md`, or an existing memory. (Existing memory → propose an **update** instead.)
3. **It's general enough to happen again.** One-off details of today's bug don't count.
4. **It contains nothing private:** no secrets, usernames, UUIDs, emails or home paths. The repo is public.

**Correct the source instead of remembering around it:** if the lesson is that a reference
file is **wrong** (e.g. `server-context.md` has the wrong chat format), propose an edit to
**that file**, not a memory.

## Step 2 — Check what's outdated (read-only)

```sh
python3 .agents/skills/personalize/scripts/memory.py check
```
It lists flagged entries (code files changed since the entry was saved, or not used for 30+
days) and any format problems. For each flagged entry, open it, re-read the code it names,
and form your own view: still true / needs update / outdated.

Also look for entries that **today's session contradicts**.

## Step 3 — Present everything and wait

Show one message with three tables, then **stop and wait** for the developer's answer:

```
### New memories
| # | Type | Summary (index line) | Files / symbols | Why it matters next time |
|---|------|----------------------|-----------------|--------------------------|

### Updates to existing memories / reference files
| # | Target | Change | Reason |

### Outdated memories (from the check + today's session)
| # | Id | Summary | Flag | My view: keep / update / archive — and why |

### Used this session
M-002, M-005   (cited as [mem:...] in my answers)

Reply e.g. "save 1,3 · update A · archive M-004 · keep M-002" (or "all").
```

Your view is a recommendation. **The developer decides.** If they disagree with your view on
an entry, do what they say (it's their memory of their project).

## Step 4 — Write what was approved

For each approved **new** memory:
1. Get the id: `python3 .agents/skills/personalize/scripts/memory.py next-id` (for several, count up from it).
2. Write `.agents/memory/entries/<id>.md` from the template (one file write = one permission prompt).
   - `summary`: one line, max ~90 characters. It's the only part loaded every task.
   - `files` / `symbols`: what it's about, so it's only opened when relevant. `[]` = general.
   - `commit`: output of `git rev-parse --short HEAD`. `created` / `last_used`: today.
   - Body: **What happened / What's true / Why** (quote the developer) / **The AI's side**
     (decision only) / **Outdated when**. Keep the whole entry under ~25 lines.

For approved **updates**: edit the entry or reference file (permission prompt each).

Then apply the bookkeeping in **one** command (one prompt), with the ids the developer chose:
```sh
python3 .agents/skills/personalize/scripts/memory.py apply --touch M-002,M-005 --confirm M-001 --archive M-004
```
- `--touch` = used this session · `--confirm` = re-checked and still true (resets the code-change flag)
- `--archive` = retire · `--recheck` = keep, but show it as doubtful · `--restore` = bring one back
It always rebuilds `INDEX.md`. If no ids need changes, run `memory.py index` so new entries appear.

## Step 5 — Report

```
Saved: M-007 (not-a-bug), M-008 (preference)
Updated: server-context.md §3 (Claimed message format)
Archived: M-004
Index: N live entries, ~T tokens (from `memory.py check`)
```
If the index grows past ~40 entries or ~1,500 tokens, suggest merging or archiving the least
useful ones next time.

---

## Don't
- Don't save anything the developer didn't approve.
- Don't save your guesses as facts. A memory says what the **developer** confirmed or decided.
- Don't write long entries. If it needs more than ~25 lines, it belongs in a reference file.
- Don't delete memory files. Archive them, so history stays in git.
- Don't edit `INDEX.md` by hand. The script generates it.
