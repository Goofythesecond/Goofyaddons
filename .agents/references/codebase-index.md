# Codebase Index — GoofyAddons

Indexed at commit `9335bfd` (mod version `1.1.22-BETA`). Line numbers below are from that
commit. **If the file changed since, re-read it before quoting a line.**
Check with: `git log --oneline -1 -- <file>`.

---

## 1. What the mod is

A **client-side** Fabric mod for **Minecraft 26.1.2** (Java 25) used on **Hypixel SkyBlock**.
Its main feature, `BazaarFlipper`, automates flipping enchanted books on the Bazaar:

1. Read Bazaar prices from the Hypixel API and pick profitable books.
2. Place buy orders for low-level books (e.g. `Ultimate Wise I`).
3. Claim them, store them in the Ender Chest (or backpack).
4. Pull them out and combine pairs in an anvil (I+I → II, II+II → III … up to the sell level).
5. Put a sell offer up for the high-level book (e.g. `Ultimate Wise V`).
6. Watch for being outbid / undercut and re-place orders.

Everything runs once per client tick (20 times per second) on the game's main thread
(`Render thread` in logs), except the HTTP callbacks (see §6).

---

## 2. Build facts

| Thing | Value | Where |
| --- | --- | --- |
| Minecraft | `26.1.2` (unobfuscated, Mojang names) | `gradle.properties` |
| Fabric Loader | `0.19.2` | `gradle.properties` |
| Fabric API | `0.147.0+26.1.2` | `gradle.properties` |
| Loom plugin | `net.fabricmc.fabric-loom` `1.16-SNAPSHOT` | `build.gradle` |
| Java | 25 | `build.gradle` |
| Source sets | `main` (common) + `client` (split environment) | `build.gradle` → `splitEnvironmentSourceSets()` |
| Dev login | DevAuth (runtime only) | `build.gradle` |
| CI | Every push → build + GitHub release + Discord message | `.github/workflows/build.yml` |

Useful commands (ask before running): `./gradlew compileClientJava`, `./gradlew build`,
`./gradlew runClient`.

---

## 3. File map

### Entry points
| File | Lines | What it does |
| --- | --- | --- |
| `src/main/java/com/goofy/goofyaddons/GoofyAddons.java` | 16 | Common entry. Holds `GoofyAddons.LOGGER` (SLF4J, name `goofyaddons`). |
| `src/client/java/com/goofy/goofyaddons/GoofyAddonsClient.java` | 43 | Client entry. Loads config, registers chat hook + keybinds, and every tick calls `FailsafeManager.onTick()` then `FeatureManager.onTick()`. Holding `\` reloads the config **every tick** while held (L29). J = start, K = stop. |
| `src/main/java/.../GoofyAddonsDataGenerator.java` | 11 | Empty datagen entry (template leftover). |
| `src/main/java/.../mixin/ExampleMixin.java` | 14 | Template mixin, not listed in `goofyaddons.mixins.json` (`"mixins": []`), so it is not applied. |

### Core plumbing
| File | What it does |
| --- | --- |
| `features/Feature.java` | Interface: `name, start, stop, pause, resume, onTick`. |
| `features/FeatureManager.java` | Singleton. Holds the list of features (only `BazaarFlipper`) and the `currentFeature`. `start(name)` / `stop()` / `pause()` / `resume()` forward to the current feature. |
| `failsafes/Failsafe.java` | Interface: `name, onTick`. |
| `failsafes/FailsafeManager.java` | Singleton. Runs each failsafe's `onTick` only while a macro is running. Registers only `ScheduledReboot`. |
| `failsafes/ScheduledReboot.java` | On chat containing `Scheduled Reboot` or `Game Update`: calls `FeatureManager.pause()`, sends `/Hub`, waits 10 s, sends `/Is`, waits 5 s, calls `resume()`. |
| `failsafes/AntiStuck.java` | Empty stub, not registered. |
| `event/ChatHook.java` | Registers `ClientReceiveMessageEvents.GAME`. Strips `§x` color codes, ignores overlay (action bar) messages and lines starting with `[GoofyAddons]`, then calls every hook whose pattern is **contained** in the text (case-sensitive). Hooks are stored in a static list and never removed. |
| `config/GoofyConfig.java` | Gson config at `<config dir>/goofyaddons.json`. Fields: `books`, `startKey`, `stopKey`, `speedMode`, `speedModeDelay`, `minActionDelay`, `maxActionDelay`, `firstPage` (`"ec"`), `secondPage` (`"ec 2"`). `load()` creates new `Book` objects every time it runs. |
| `keybinds/GoofyKeybinds.java` | Registers the J (start) and K (stop) key mappings. |
| `render/gui/GoofyGui.java` | Work-in-progress settings screen. Not opened anywhere yet. |

### Utils (all read the live game state through `Minecraft.getInstance()`)
| File | What it does |
| --- | --- |
| `utils/ChatUtils.java` | `clientMessage` (gray) and `debugMessage` (dark gray). Both send to **in-game chat** with the `[GoofyAddons]` prefix. They return early if `player == null`. |
| `utils/Clock.java` | Simple timer. `start(ms)` does nothing if already running. `shouldFire()` returns true **once** when time is up, then stops itself. |
| `utils/InventoryUtils.java` | `clickSlot(slot, shift)` → `gameMode.handleContainerInput(... PICKUP or QUICK_MOVE ...)` on the open menu. |
| `utils/ScoreboardUtils.java` | `getPurse()` reads the sidebar line containing `Purse`, strips everything except digits and `.`, parses a double. Returns `-1` when nothing is found. |
| `utils/InventoryScanner.java` | All slot searches. "Container" methods scan `0 .. slots.size()-36` (the open chest). "Inv" methods scan slots whose container is the player inventory. Searches match on custom name (`findContainer`, `findInv`, `getSellOrder`) or lore lines (`findLore*`, `matchingBook*`, `checkOrder`, `getUnitPrice`). `getLevel(slot)` reads `CUSTOM_DATA → enchantments → first key`. `isMenuLoaded(slot)` = "does this slot have an item". `findMisMatch` checks anvil slots 29 and 33. |

### Book flipper feature (`features/bookflipper/`)
| File | Lines | What it does |
| --- | --- | --- |
| `BazaarFlipper.java` | 1293 | The state machine (see §4). Holds all runtime state as fields. |
| `helper/Book.java` | 34 | `record Book(id, level, sellLevel, name, instaSellPercentage, instaBuyPercentage)`. `getLevel(i)` → product id like `ENCHANTMENT_ULTIMATE_WISE_1`. `getRomanLevel(i)` → `"Ultimate Wise I"`. `getQtyAmount(level)` → `2^(sellLevel-level)` (how many books of that level make one sell-level book). |
| `helper/BookList.java` | 12 | One physical book: `book`, `level` (final), `location` (mutable). **Location codes: `0` = player inventory, `1` = Ender Chest page 1 (`firstPage`), `2` = page 2 (`secondPage`).** |
| `helper/Task.java` | 95 | One flip in progress. Fields: `book`, `instaBuy`, `instaSell`, `amountToOrder` (starts at `getQtyAmount(book.level())`), `bookState`, `actionSchedule`, `bookList` (the books it owns, sorted by location). `assignBook(...)` returns `0` = success, `-1` = rejected (note: compares books with `!=`, L63). `isCombinable()` = two books with the same level exist. |
| `helper/FlipCalculator.java` | 105 | `Refresh()` fetches the bazaar API async and builds `flipItemsList` sorted by score. A `running` flag blocks overlapping refreshes. |
| `helper/FlipItem.java` | 4 | `record FlipItem(book, totalCost, score, instaBuy, instaSell)`. |
| `helper/BazaarData.java` | 4 | `record BazaarData(productID, sellPrice, sellVolume, buyPrice)` from `quick_status`. |
| `helper/BazaarMonitor.java` | 159 | Every 20 s (while it has items) fetches the API async and checks if our order is still the top one. If not → marks outbid and calls the **first** hook (`BazaarFlipper.handleOutbid`). |

---

## 4. The state machine (`BazaarFlipper`)

Two layers of state:

- **Macro state** `BazaarFlipper.State` — what the macro is doing *right now* (one at a time).
- **Task state** `Task.BookState` — where each book flip is in its life (one per task).

`IDLE` picks the task with the **highest rank** (`STATE_PRIORITY`, L82-91; bigger number wins):
`OUTBID 8 > SELECTED 7 > STORE 6 > SELL 5 > COMBINE 4 > ANVIL 3 > BAZAAR_ORDER_CHECK 2 > REPLACE_SELL 1`.
States not in the map (`IN_BUY_ORDER`, `SELL_ORDER`) are never picked — they wait for chat
messages or the monitor.

```
START ─► FETCHING ─► processData() ─┬─► STARTUP_CHECK (first run: scan EC page 1, page 2, inventory)
                                    │        └─► STARTUP_BAZAAR_CHECK (claim / check existing orders)
                                    └─► IDLE ◄──────────────────────────────┘
IDLE ─► OUTBID | BAZAAR_NAVIGATION | STORE | ANVIL | COMBINE | SELL | REPLACE_SELL ─► (back to IDLE)
SELL (no task left) ─► FETCHING
```

Every tick, before the `switch` (L144-149):
1. `selfRecoveryTrigger()` — counts ticks while `attemptedToClaim` is true; at 1200 ticks restarts.
2. `handleTaskStateChange()` — applies queued changes (`SELL_ORDER → REPLACE_SELL`, `IN_BUY_ORDER → OUTBID`), skipped while in `OUTBID` / `REPLACE_SELL`.
3. `lastStateCheck()` — on a macro state change: closes the open screen, resets `tick`, `attemptedToClaim`, the clock, and prints `State switched from: X to: Y` in chat. Entering `FETCHING` clears the list and calls `flipCalculator.Refresh()`.
4. `bazaarMonitor.onTick()`.

### The per-state pattern
Almost every state repeats this shape:
```
if (screen == null) clock.start(random delay)
if (screen == null && clock.shouldFire()) send command (/ec, /managebazaarorders, /bz ..., /anvil)
if (title contains "X") clock.start(delay)
if (title contains "X" && slot N has an item && clock.shouldFire()) click something
```
"Slot N has an item" (`isMenuLoaded`) is used as "the menu finished loading".

### Task flow (typical)
`SELECTED` → (buy order placed) `IN_BUY_ORDER` → chat "was filled" → `OUTBID` state claims it →
`STORE` (into EC) → `ANVIL` (pull pairs out of EC) → `COMBINE` (anvil merge) → `SELL` →
`SELL_ORDER` → chat "Sell Offer … was filled" → `REPLACE_SELL` → … → task removed.

`ActionSchedule` remembers what to do *after* the current step:
`SELECTED_COMBINE_STORE_BUYORDER`, `SELECTED_STORE_BUYORDER`, `ANVIL_SELL`, `STORE_ANVIL`, `NONE`.

### "Did the item move?" counters
`STORE`, `ANVIL` and `COMBINE` click a slot, then on later ticks compare how many matching
books are in the inventory vs the container now (`store_Counter`, `store_Counter_2`,
`anvil_Counter`, `anvil_Counter_2`, `combine_Counter`, `combine_Counter_2`). `-1` means
"not measured yet". This is how they decide a shift-click actually worked.

---

## 5. Things the code depends on from the server

These must match Hypixel exactly or the macro stalls (details in `server-context.md`):

- **Commands:** `/ec`, `/ec 2`, `/managebazaarorders`, `/bz <name>`, `/anvil`, `/Hub`, `/Is`.
- **Screen titles (substring match):** `Ender Chest`, `Jumbo Backpack`, `Greater Backpack`,
  `Bazaar`, `Order`, `How many do you want`, `How much do you want to pay`,
  `At what price are you selling`, `Confirm`, `Anvil`, and the book name itself.
  ⚠ Titles are checked with `screen.getTitle().toString()` (L1050), not `.getString()`.
- **Hard-coded slots:** order screen 10/15 (insta-buy vs buy order), 16 (custom amount / sell),
  12 (price option), 13 (confirm), 22 (anvil combine), 29 + 33 (anvil inputs),
  "loaded" checks on 8, 35 and 53.
- **Item names/lore:** `BUY Ultimate Wise I`, `SELL Ultimate Wise V`, `Cancel Order`,
  lore `You have N`, lore `Unit price: N`, lore line equal to `Ultimate Wise I`.
- **Chat hooks:** `filled` (order filled), `Claimed` (items claimed),
  `Scheduled Reboot` / `Game Update`.
- **Sign input:** amount is written into the sign by reflection on
  `AbstractSignEditScreen.messages` (L1179), then the screen is closed.

---

## 6. Threads (important for bugs)

- `onTick` code runs on the main client thread.
- `FlipCalculator.Refresh()` and `BazaarMonitor.refresh()` use `HttpClient.sendAsync(...)`.
  Their `.thenApply/.thenAccept` callbacks run on **another thread**. They change
  `flipItemsList`, `bazaar`, `monitorItemList`, and (through the hook) `listOfTaskToChange`
  while the main thread may be reading them.
- Neither chain has `.exceptionally(...)` / `.handle(...)`, so an error inside a callback is
  not printed anywhere by default.

---

## 7. Crash history (from `run/crash-reports/`, local only — `run/` is gitignored)

Line numbers point to the code **at crash time**, not today's code.

| Exception | Where (at that time) | Seen |
| --- | --- | --- |
| `NoSuchElementException` from `ArrayList.getFirst()` | `BazaarFlipper.onTick` | many, Jul 2026 |
| `NullPointerException` unboxing `Map.get(...)` → `Integer` | `BazaarFlipper.removeDuplicateBooks` / priority lookup | Jul 2026 |
| `NullPointerException` `minecraft.player` is null | `InventoryScanner.isMenuLoaded` via `scheduler` | Sep–Oct 2026 |
| `NullPointerException` `tag` is null in `getLevel` | `InventoryScanner.getLevel` | Sep–Oct 2026 (null check now exists at L211) |

---

## 8. Leads for bug hunting (UNVERIFIED — confirm before claiming)

These came from a read-through. None of them have been run or tested. Treat each as a
`GUESS` until confirmed with a log, a debug line or a `tests/` simulation.

1. `attemptedToClaim` is set to `false` in several places but never set to `true`
   (`grep -n "attemptedToClaim" BazaarFlipper.java`). So the "wait for Claimed message"
   branches and `selfRecoveryTrigger()` may never run.
2. `FlipCalculator.Refresh()` sets `running = true` and only sets it back to `false` at the
   end of the success path. If the request or JSON parsing fails, would `FETCHING` wait forever?
3. HTTP callbacks change lists from another thread (§6).
4. `ScoreboardUtils.getPurse()` keeps every digit on the line. What does a purse line with
   extra text (for example a `(+N)` gain) turn into?
5. `Task.assignBook` compares `book != this.book` (identity). `GoofyConfig.load()` (the `\`
   key) creates new `Book` objects.
6. `BAZAAR_NAVIGATION` insta-buy path calls `activeTask.bookList.getLast()` (L433). Can
   `bookList` be empty there?
7. `pause()` / `resume()` in `BazaarFlipper` are empty, so `ScheduledReboot` doesn't actually
   pause the macro.
8. `randomizer()` → `SplittableRandom.nextInt(min, max)` throws if `min >= max` in the config.
9. `InventoryScanner.checkOrder` → `Integer.parseInt(digits)` throws if the line has no digits.
10. `BazaarMonitor.outbidScanner` assumes the product exists and `sell_summary`/`buy_summary`
    are non-empty (`.get(0)`).
11. `scheduler(...)` (L1279) is currently unused.
