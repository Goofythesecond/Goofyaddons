---
name: bug-hunt
description: >-
  Finds where and why the GoofyAddons mod breaks: crashes, stack traces, the macro getting
  stuck, wrong behavior, confusing logs. Use when the developer mentions a crash, error,
  "stuck", "broke", "doesn't work", pastes a log or crash report, or asks why something
  happens. Finds and explains the problem; the developer writes the fix.
---

# Bug Hunt

You **find and explain**. You do **not** write the fix. The developer rewrites the code.

If you can read files (Antigravity), read them yourself. If you are in a chat without file
access (Gemini Gem), ask the developer to paste exactly what you need: name the file,
the line range, or the log section.

---

## Step 0 — Check memory

Read `.agents/memory/INDEX.md`. Open entries whose files/symbols match the area of the bug.
- A `not-a-bug` entry covers your finding → **don't report it again**. If its files changed
  since it was saved, you may ask once: "M-00x may be outdated — still not a bug?"
- A `decision` entry covers the code → don't re-argue it without new evidence.
- Also skip leads from `codebase-index.md` §8 that a memory already dismissed.
Cite every entry you used as `[mem:<id>]`.

## Step 1 — Get the facts (ask only for what's missing)

| Need | Where it is |
| --- | --- |
| What happened vs. what should happen | ask |
| When / in which macro state | last `State switched from: X to: Y` line in chat or log |
| Log | `run/logs/latest.log` (older: `run/logs/*.log.gz`, read with `zcat`) |
| Crash report | `run/crash-reports/crash-<date>_<time>-client.txt` |
| Code version | `git log --oneline -5` |

Useful log searches (read-only, OK to run):
```sh
grep -n "State switched" run/logs/latest.log | tail -20
grep -n "\[GoofyAddons\]" run/logs/latest.log | tail -50
grep -n -i "exception\|error\|caused by" run/logs/latest.log | head -40
```

## Step 2 — Read the crash report (teach the developer how while you do it)

1. `Description:` line = what the game was doing.
2. First line after it = **exception type + message**. Java's "helpful NullPointerException"
   message names exactly which value was null, e.g.
   `Cannot read field "containerMenu" because "this.minecraft.player" is null`.
3. Go down the `at ...` lines. The **first line that starts with `com.goofy`** is where it blew
   up in the mod. The lines under it are who called it (the path that led there).
4. `Caused by:` blocks: the **last** one is usually the real root cause.

## Step 3 — Match the line numbers to the right code version

Crash reports and old logs point to the code **as it was at that time**.
```sh
# commit that was current when the crash happened (use the date/time from the file name)
git log -1 --before="2026-10-01 15:11" --format="%h %ad %s"
# show the lines around the crash at that commit
git show <hash>:src/client/java/com/goofy/goofyaddons/utils/InventoryScanner.java | sed -n '205,215p'
```
Then check if that code still exists today (it may already be fixed).

## Step 4 — Trace the data backwards

From the failing line, list every value involved and where it came from. Present it as a table:

| Value | Value at crash | Comes from | Could it be wrong because… |
| --- | --- | --- | --- |
| `task.bookList` | empty | `assignBook` / `handleItemAssigning` | nothing was assigned on the insta-buy path |

Keep going backwards until you reach the place where the value **first** became wrong.
That is the bug. The crash line is often just where it was noticed.

## Step 5 — Hypotheses (max 3, ranked)

For each: what goes wrong, evidence tag (`CONFIRMED` / `LIKELY` / `GUESS`), and **how to
confirm it** — a debug line (`debug-lines` skill), a `tests/` simulation (`simulate` skill),
or steps to reproduce in-game.

## Step 6 — Report (use this format)

```
## Summary
<1–2 lines, plain words>

## Where
`path/File.java:LINE` — <method>

## What goes wrong, step by step
1. ...
2. ...

## Evidence
- CONFIRMED: ...
- LIKELY: ...

## How to confirm
<one debug line or one simulation or one repro step>

## Fix direction (you write it)
<the idea in words: what must be true, what to check, where. No rewritten code.
 If the developer asks, show a tiny generic example of the technique, labeled EXAMPLE.>
```

---

## Bug patterns that fit this codebase (check these first)

| Pattern | Typical symptom | Where to look |
| --- | --- | --- |
| `player`/`level`/`screen` is null during world change, reboot, `/hub`, `/is` | NPE in `InventoryScanner`, `sendCommand` | any tick code; `ScheduledReboot` |
| `getFirst()` / `getLast()` / `.get(0)` on an empty list | `NoSuchElementException`, `IndexOutOfBounds` | `slot.getFirst()`, `bookList.getLast()` |
| `Map.get(...)` returns null, then auto-unboxed to `int` | NPE "Integer.intValue()" | `STATE_PRIORITY.get(...)` |
| Async HTTP callback changes shared lists | random `ConcurrentModificationException`, "sometimes" bugs | `FlipCalculator`, `BazaarMonitor` (see index §6) |
| Error inside async callback is silent | flag stays `true`, state never moves on | `FlipCalculator.running` |
| State not reset in `stop()` or on state change | works first run, breaks after restart | fields listed in `BazaarFlipper.stop()` vs all fields |
| `Clock.start` does nothing while running | delay "stuck" or skipped | patterns `clock.start` + `shouldFire` |
| Matching server text | stalls after a Hypixel update | titles, lore, chat (see `server-context.md`) |
| Parsing numbers from text | wrong numbers, `NumberFormatException` | `getPurse`, `checkOrder`, `getUnitPrice` |
| `==`/`!=` on objects instead of `.equals` | "should match but doesn't" | `Task.assignBook` |
| Flag set to false everywhere but never true | dead branch, failsafe never fires | `attemptedToClaim` |

Also read `.agents/references/codebase-index.md` §8 (unverified leads) and §7 (crash history).

## Don't
- Don't fix the code, even "just this one line". Offer the fix direction. (Only exception:
  the developer explicitly asks and confirms after the reminder — `GEMINI.md` rule 1.)
- Don't list 10 possible causes. Max 3, ranked, each with a way to confirm.
- Don't blame Hypixel or Minecraft until the mod's own code is ruled out.
