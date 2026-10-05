---
name: simulate
description: >-
  Statically simulates selected lines or a function of the GoofyAddons mod against many
  input cases (normal, edge, empty, null, wrong format, wrong type, huge, negative, stale
  state) to see where it fails, using trace tables. On request, writes a standalone runnable
  Java 25 test file in tests/. Use when the developer says simulate, "what if", "test these
  lines", "edge cases", or asks whether code works for certain data.
---

# Simulate (static test)

You run the code **in your head, on paper**, step by step, for many inputs, and show exactly
what each line does to the data. No game needed.

Two outputs:
- **A. Trace tables** — always.
- **B. Runnable test file** in `tests/` — only when the developer asks (the write prompts them).

---

## Step 0 — Check memory

Read `.agents/memory/INDEX.md`. Open entries for the code under test. A `fact` entry may tell
you which inputs **can't** happen in-game (skip or mark them), and a `not-a-bug` entry may
explain an intended behavior (mark that case PASS-by-intent). Cite used ones as `[mem:<id>]`.

## Step 1 — Pin down what we're simulating

Write this block first and confirm it if anything is unclear:

```
Under test:   path/File.java:START-END  (method name)
Inputs:       parameters + fields/state it reads
Outputs:      return value + fields/lists it changes + side effects (clicks, commands, chat)
Fakes:        what we replace, and with what (e.g. "slot 35 has an item" -> true/false)
```

Read the real code first. Simulate **the code as written**, not what it was meant to do.

## Step 2 — Build the case table

Pick cases from these groups. Only keep the ones that can actually happen or that teach
something. Usually 6–12 cases.

| Group | Examples for this project |
| --- | --- |
| Normal | `"Ultimate Wise I"`, level 1→5, 16 books, slot 13 |
| Boundary | exactly full (amountToOrder hits 0), first/last slot, `size() - 36` |
| Empty | empty `bookList`, empty `taskList`, no matching slot, empty lore |
| Missing / null | `player == null`, `screen == null`, `Map.get` → null, no `CUSTOM_DATA` |
| Wrong format (text) | `§` color codes, commas `1,234`, extra text `(+5)`, no digits, decimals, unicode |
| Wrong type / conversion | `Integer` null → `int`, `double` compared with `!=`, int overflow, `1 << n` with big `n`, `parseInt` on `""` or `"1.5"` |
| Negative / zero | amount `0`, `-1`, level `0` |
| Identity vs equality | equal record but a new object (`==` vs `.equals`) |
| Stale state | values left over from the last tick / last run (`-1` counters, flags not reset) |
| Ordering / timing | menu open but not loaded, chat message arrives before/after the click |
| Threads (if async) | callback changes a list while the tick loop reads it |

## Step 3 — Trace each interesting case

One table per case (short cases can share a table):

```
Case: "weird: amountOfBook = -1"
| # | Line | Code                                   | Values after this line                  |
|---|------|----------------------------------------|-----------------------------------------|
| 1 | 62   | if (amountOfBook == 0) return 0;       | amountOfBook=-1 → skip                  |
| 2 | 65   | int amount = parseBookLevel(level);    | amount=1                                |
| 3 | 66   | int totalAmount = amount * amountOfBook| totalAmount=-1                          |
| 4 | 68   | if (totalAmount > amountToOrder) ...   | -1 > 16 false → continue                |
| 5 | 70   | amountToOrder -= totalAmount;          | amountToOrder=17  ⚠ went UP             |
| 6 | 72   | for (i < amountOfBook)                 | 0 < -1 false → no books added           |
| 7 | 78   | return 0;                              | returns 0 ("success")                   |
Result: returns success, adds nothing, and amountToOrder grows. → FAIL (if -1 can reach here)
```

Mark the exact line where it goes wrong with ⚠. Say whether this input **can actually
happen** in the real flow (and where it would come from) — `CONFIRMED`/`LIKELY`/`GUESS`.

## Step 4 — Summary table

| Case | Expected (developer's intent) | Actual | Verdict |
| --- | --- | --- | --- |
| normal 1x I | 0, 15 left | 0, 15 left | PASS |
| amount -1 | rejected | success, 17 left | FAIL |

"Expected" is the developer's intent. If you're not sure what they intend, **ask** — don't
decide for them.

---

## B. Writing a runnable test in `tests/` (only on request)

Template: [examples/TaskAssignBookSim.java](examples/TaskAssignBookSim.java) — copy its shape.

Rules:
1. **One file per thing under test**: `tests/<WhatIsTested>Sim.java`. Java 25 compact source
   file (`void main()` at top level, `IO.println`). No JUnit, no frameworks, no build changes.
2. **Data-oriented** (see `dop-style.md`): `record Case(...)` for inputs + expected,
   `record Result(...)` for outcomes, `List<Case> cases()` as the input table, one `run(Case)`
   function, a loop that prints one row per case. Each case gets **fresh** state.
3. **Catch exceptions per case** and report them as `CRASH <exception>` rows — one crash must
   not stop the other cases.
4. **Comments** follow `comment-style.md`. Header must have: what is simulated, the
   `file:lines` it tests, the mode (real class / mirrored), and the exact run command.
5. **Real class vs mirrored logic:**
   - **Real class** if the class and everything it touches are plain Java. These load fine:
     `Book`, `BookList`, `Task`, `FlipItem`, `BazaarData`. Run with:
     ```sh
     ./gradlew compileClientJava
     java --class-path build/classes/java/client tests/<Name>Sim.java
     ```
   - **Mirrored** if it touches `Minecraft`, `GoofyConfig`, `FabricLoader`, `ChatUtils`, or
     menus (`InventoryScanner`, `ScoreboardUtils`, `BazaarFlipper`, `FlipCalculator`).
     Copy **only the pure logic** into a `static` function in the test, with the game data
     turned into plain parameters (e.g. the scoreboard line as a `String`). Put this above it:
     `// MIRRORS src/.../ScoreboardUtils.java:30-38 @ <git short hash> — re-copy if that code changes`
     Run with: `java tests/<Name>Sim.java`
6. **Never change `src/` to make something testable.** If it can't be tested without a
   change, explain what change would help, and let the developer decide.
7. After writing, tell the developer the exact command. Offer to run it — **ask first**.

## Don't
- Don't simulate code you haven't read.
- Don't "fix" the code inside the mirrored copy — mirror it exactly, bugs included.
- Don't write a test without being asked; trace tables are the default.
