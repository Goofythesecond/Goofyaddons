# Comment Style — for every piece of code the AI shows or writes

Goal: a beginner reads the comment and understands **why** the code is like this, in one go.

Based on Ellen Spertus' "Best practices for writing code comments" (Stack Overflow Blog,
2021) and the common "code says *what*, comments say *why*" rule. Adapted for this project.

---

## The 7 rules

1. **Say why, not what.** The code already shows *what* happens. Comment the reason,
   the rule from the game, or the thing that would surprise a reader.
   - ❌ `// loop over the slots`
   - ✅ `// Hypixel fills the menu a few ticks after it opens, so an empty slot 35 means "not loaded yet"`
2. **Don't repeat the code.** If the comment just reads the code out loud, delete it.
3. **Clear code first.** A good name beats a comment. If a comment is needed to explain a
   variable name, suggest a better name instead.
4. **Hard to explain = maybe bad code.** If you can't write a short, clear comment, say so —
   that is a hint the code should be simpler.
5. **Explain the weird stuff.** Magic numbers, slot indexes, reflection, string matching on
   server text, workarounds. Say what it depends on, so nobody "fixes" it by accident.
   - ✅ `// 16 = "Custom Amount" button in Hypixel's buy menu (checked in-game, Oct 2026)`
6. **Link the source.** Copied code, a wiki fact, a forum answer → put the link.
7. **Mark unfinished things honestly.** `// TODO: ...` with what is missing and why.
   For bug-fix code: `// FIX: <what broke> — <why this prevents it>`.

---

## Format

- Plain English, short sentences, no jargon without a quick explanation.
- One comment line above the code it explains. Avoid end-of-line comments longer than ~60 chars.
- For a function: one short line on top saying what goes in, what comes out, and anything
  that can go wrong (null, empty list, throws).
  ```java
  // Takes a lore line like "Unit price: 1,234.5 coins" and returns 1234.5.
  // Returns -1 if the line has no number (never throws).
  ```
- In **examples and simulations**, number the steps so the explanation text can point to them:
  ```java
  // (1) Collect every book of this level that is in the player inventory.
  // (2) If there are fewer than 2, there is nothing to combine yet.
  ```
- Keep comments true. If the code changes, the comment changes in the same edit.

## Don't
- Don't write essays above every line.
- Don't use comments to keep old code around (git keeps history).
- Don't write comments that only make sense to an AI ("as discussed above", "per your request").
