---
name: debug-lines
description: >-
  Plans and (only with the developer's permission) adds or removes temporary debug/log
  lines in the GoofyAddons source so a bug can be traced, without flooding chat or the log
  and without crashing the game. Use when the developer says "add debug", "log this",
  "print the values", "I need more info in the logs", or asks to remove debug lines.
---

# Debug Lines

The goal of a debug line: **answer one question** about the bug ("which branch ran?",
"what was `location` here?") and be easy to find and delete afterwards.
This is the **only** case where you may edit `src/`, and only debug lines — never logic.

---

## Step 0 — Check memory

Read `.agents/memory/INDEX.md` for `preference` entries about logging (where, how much, chat vs
log). Cite used ones as `[mem:<id>]`.

## Step 1 — Plan first, write second

Before touching any file, show the plan and wait for "yes":

```
Question we're answering: <e.g. "Does STORE ever see the book leave the inventory?">
Lines to add (max 5):
  1. BazaarFlipper.java after L588  ->  <exact line of code>
  2. ...
How to read the result: grep "\[GA-DBG\]" run/logs/latest.log | tail -50
```

## Step 2 — The line format

Use the SLF4J logger that already exists, **not** chat:
```java
GoofyAddons.LOGGER.info("[GA-DBG] STORE move-check book={} lvl={} loc={} inv={}->{} cont={}->{}", // DEBUG(ai): does the move check fire?
        bookList.book.name(), bookList.level, bookList.location,
        store_Counter, slot.size(), store_Counter_2, containerCount);
```

Rules:
1. **Tag:** every message starts with `[GA-DBG]` and the macro state (`STORE`, `ANVIL`, ...).
   Then a short event name (`move-check`, `picked-task`, `click`).
2. **key=value pairs** for every value: `book=... lvl=... slot=...`. Easy to read and to grep.
3. **`{}` placeholders**, not `+` string building. The logger only builds the text if the
   line is actually logged.
4. **Marker comment** at the end of each added line: `// DEBUG(ai): <the question it answers>`.
   Removing them all later: `grep -rn "DEBUG(ai)" src/` → delete those lines.
5. **Log file, not chat.** `ChatUtils.debugMessage` goes to chat, and chat keeps only the last
   100 lines (`ChatComponent.MAX_CHAT_HISTORY`), so older lines scroll away. The logger writes
   to `run/logs/latest.log`, which keeps everything and can be searched.
   Chat is only for rare things the developer must see live.
6. Needs the import `com.goofy.goofyaddons.GoofyAddons` in client files. Mention it in the plan.

## Step 3 — Don't flood (the 20-ticks-per-second problem)

`onTick` runs 20 times a second. A log line in a state's body = 1,200 lines a minute.

| Use | Pattern |
| --- | --- |
| Log **events**, not ticks | Put lines where something **changes or is decided**: a state switch, a branch taken, a click sent, a value set, a `return` that skips work. |
| Log **on change** | Only log when the value differs from last time: keep the last logged value in a field and compare. |
| Log **every N ticks** for "is it still waiting?" | Add a separate counter: `private int dbgTicks = 0; // DEBUG(ai)` then `if (++dbgTicks % 100 == 0)` → once every 5 seconds. ⚠ Don't reuse `BazaarFlipper.tick`: it is reset to 0 every tick unless `attemptedToClaim` is true, so `tick % 100 == 0` would log **every** tick. |
| Log **once** per run | a `boolean` flag, reset in `stop()`. |

Example of "log on change" (EXAMPLE — data-oriented, one small record holds what was last logged).
It has **two parts in two different places**. Pasting both into `onTick` won't compile.
```java
// PART 1 — at class level in BazaarFlipper (next to the other fields, NOT inside a method)
record WaitInfo(String state, String waitingFor) {} // DEBUG(ai): what we last logged
private WaitInfo lastWait = null;                    // DEBUG(ai)

// PART 2 — inside onTick, in the branch you're watching
WaitInfo now = new WaitInfo(state.name(), "menu slot 35"); // DEBUG(ai): only log when the waiting reason changes
if (!now.equals(lastWait)) { GoofyAddons.LOGGER.info("[GA-DBG] {} waiting for={}", now.state(), now.waitingFor()); lastWait = now; } // DEBUG(ai)
```
Records compare by value (`equals`), so the line only fires when the state or the reason changes.

## Step 4 — A debug line must never break the game

- **Never call anything that can throw** inside a log line: no `getFirst()`, `getLast()`,
  `.get(0)`, `Integer.parseInt`, or `player.something` without checking. A debug line that
  crashes the game is worse than no debug line. Log `list.size()` and check before reading.
- **Never log whole objects or lists.** Log the size and the first few items:
  `bookList.size()={} first={}` with `list.isEmpty() ? "none" : list.getFirst().level`.
  Big `toString()` output floods the log, and objects that contain each other in their
  `toString()` can recurse until `StackOverflowError`. Classes without `toString()`
  (e.g. `BookList`, `Task`) only print `BookList@1a2b3c` anyway — log their fields.
- **No side effects.** Don't call methods that click, send commands, or change state just
  to log their result. Don't run expensive scans (`findLoreContainer`) only for a log line —
  reuse a value the code already computed.
- **Threads:** in HTTP callbacks (`FlipCalculator`, `BazaarMonitor`), add
  `thread={}` with `Thread.currentThread().getName()` so the log shows which thread ran it.
- **Exceptions:** log them with the exception as the **last argument**, so the full stack
  trace is printed: `GoofyAddons.LOGGER.error("[GA-DBG] refresh failed url={}", url, e);`
  (instead of `e.printStackTrace()`).

## Step 5 — Reading the results (teach the developer)

```sh
grep "\[GA-DBG\]" run/logs/latest.log | tail -50                  # last 50 debug lines
grep "\[GA-DBG\] STORE" run/logs/latest.log | tail -20             # one state only
grep -n "State switched\|\[GA-DBG\]" run/logs/latest.log | tail -80 # debug lines + state changes together
```
Then read the result together: what was expected, what showed up, what that tells us.

## Step 6 — Cleaning up

When the bug is understood, offer to remove the lines: show the list from
`grep -rn "DEBUG(ai)" src/` and remove only those lines (with permission). Remind the
developer that **CI builds and posts every push**, so debug lines shouldn't be pushed by accident.

## Don't
- Don't add more than 5 lines per round. Fewer, better-placed lines are easier to read.
- Don't change any logic, even "while you're there".
- Don't remove the developer's own `debug(...)` calls — only lines marked `DEBUG(ai)`.
