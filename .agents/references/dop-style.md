# Data-Oriented Style — for every piece of code the AI shows or writes

The developer thinks in **data and transformations**, not objects. Example code must look
like that. Based on the data-oriented programming principles described by Brian Goetz for
modern Java (records, sealed types, pattern matching), kept simple for a beginner.

---

## The 5 principles

1. **Data is just data.** Use a `record` for a group of values. No hidden state and no
   behavior beyond small helpers that only read the record's own fields.
   ```java
   record OrderLine(String bookName, int amount, double unitPrice) {}
   ```
2. **Functions transform data.** Logic lives in `static` functions: data in → new data out.
   They don't reach into global state (`Minecraft.getInstance()`, singletons). The caller
   reads the game state and passes it in.
   ```java
   static double totalCost(List<OrderLine> lines) { ... }
   ```
3. **Don't change data in place.** Build a new value instead of mutating the old one. If
   mutation is needed (game state, performance), keep it in one obvious place.
4. **Make the different cases explicit.** Use an `enum` or a `sealed interface` + records for
   "one of these" data, and an exhaustive `switch` (no `default`) so the compiler tells you
   when a case is missing.
   ```java
   sealed interface ClickResult permits Clicked, MenuNotLoaded, SlotMissing {}
   record Clicked(int slot) implements ClickResult {}
   record MenuNotLoaded() implements ClickResult {}
   record SlotMissing(String wanted) implements ClickResult {}
   ```
5. **Check data at the border.** Parse and validate once, where the data enters (chat text,
   lore, scoreboard, API JSON). After that, the rest of the code trusts it.
   Parsing returns a clear "failed" value instead of throwing deep inside the logic.

---

## Shape of an example

```java
// EXAMPLE — not a patch
// (1) Data: what we know about one slot.
record SlotInfo(int index, String name, List<String> lore) {}

// (2) Pure function: data in -> data out. No Minecraft calls here, so it can be tested in tests/.
static List<Integer> slotsWithLore(List<SlotInfo> slots, String wantedLine) {
    return slots.stream()
            .filter(s -> s.lore().contains(wantedLine))
            .map(SlotInfo::index)
            .toList();
}

// (3) Edge: the only place that touches the game. It turns game objects into plain data.
static List<SlotInfo> readSlots(AbstractContainerMenu menu) { ... }
```

Why this helps this project: step (2) can be simulated and tested in `tests/` without
starting Minecraft. Only step (3) needs the game.

## Don't
- No inheritance chains, no "Manager of Managers", no getters/setters just for the sake of it.
- No clever one-liners a beginner can't read. Clear beats short.
- Don't rewrite the developer's existing classes into this style unless asked — use the style
  in **examples** and **tests**.
