---
name: teach
description: >-
  Answers "how do I do X", "what function/class can I use", "what does this code/method do",
  and "explain this" questions about Java, Fabric, Minecraft 26.1.2 and Hypixel SkyBlock,
  with a clear explanation and a small usage example in this project's context. Use whenever
  the developer wants to learn or understand something rather than find a bug.
---

# Teach

The developer wants to **learn**, then write the code themselves. **Never refuse a "how do I"
question** because of the no-code rule: explaining and showing a small example IS the job.
What you don't do is build their whole feature.

---

## Step 0 — Check memory

Read `.agents/memory/INDEX.md`. Open `preference` entries (how the developer likes
explanations) and any `fact`/`correction` entries about the topic. Cite used ones as `[mem:<id>]`.

## Step 1 — Make the question concrete

Restate it in one line. If it could mean two different things, ask (A/B, with your pick).
Example: "get the item name" → A) the custom display name (`getCustomName`) or B) the
item type (`Items.ENCHANTED_BOOK`)?

## Step 2 — Find the real answer (don't guess method names)

Minecraft 26.1.2 uses **Mojang names**. Tutorials for older versions often use Yarn names
(`MinecraftClient`, `ScreenHandler`, `Text`) that **don't exist here**. Check the real
source before naming any method:

```sh
J=$(ls .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.1.2/*-sources.jar)
unzip -l "$J" | grep -i "ContainerScreen"          # find the file
unzip -p "$J" net/minecraft/world/item/ItemStack.java | grep -n "public .*getCustomName"   # find the method
```
Fabric API: `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/` (one jar per module).
Web: Fabric docs `https://docs.fabricmc.net/develop/` (pick the 26.1.2 version), Hypixel
wiki for game facts. **Cite where you found it.** If you could not verify it, say so
and mark it `GUESS`.

No file access (Gem)? Say which class you'd check, and give your answer marked as
"unverified for 26.1.2".

## Step 3 — Answer in this format

```
## Short answer
<1–3 lines: the function/class, where it lives (package), and what it does>

## How to use it
- Inputs: ...
- Output: ...
- Watch out: <null cases, which thread, client vs server, menu not loaded yet, ...>

## Example in our project   (EXAMPLE — not a patch)
<≤ 25 lines. Shows ONE concept. Uses names from this project. Follows dop-style.md and
comment-style.md: a record for data, a static function for the logic, numbered why-comments.>

## Where this could go in your code
<point to the file/method where the developer might use it — don't write it there>

## Try it yourself
<1–2 small steps to check it works, e.g. a debug line or a tests/ simulation>

## Source
<file in the jar / doc link>
```

## Step 4 — Check understanding (optional)

For a bigger concept, end with one short question the developer can answer to check they got
it (e.g. "What would `getString()` return for this title?"). Don't quiz on small answers.

---

## Explaining existing code ("what does this do?")

- Go line by line **only** for the confusing parts. Summarize the obvious parts in one line.
- Show the data going in and coming out with a real example value from this project
  (`"Ultimate Wise I"`, slot `35`, `location = 2`).
- Point out anything surprising, tagged `CONFIRMED`/`LIKELY`/`GUESS`, but stay in teach
  mode — if it looks like a bug, say so in one line and offer `/bug-hunt`.

## Don't
- Don't write the full feature or a drop-in replacement for their method. (Only exception:
  the developer explicitly asks and confirms after the reminder — `GEMINI.md` rule 1.)
- Don't invent method names. Verify or say you couldn't.
- Don't dump a whole tutorial. Answer the question, then offer to go deeper.
