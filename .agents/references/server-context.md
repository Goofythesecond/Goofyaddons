# Server & Runtime Context — Hypixel SkyBlock + Minecraft 26.1.2 / Fabric

How the mod's code meets the real world. Each fact has a source tag:
- `[LOG]` seen in the developer's own game logs (most trustworthy for formats)
- `[SRC]` read in the Minecraft 26.1.2 source jar
- `[WIKI]` Hypixel SkyBlock wiki / Hypixel docs
- `[COMMUNITY]` forums / third-party docs — **verify in-game before relying on it**

---

## 1. The big picture

- The mod is **client-only**. Hypixel is the server. The mod can only do what a player can:
  send commands, click slots in menus, type in signs, read chat/scoreboard/items.
- **Every menu is a server-made chest screen.** Click → packet goes to Hypixel → Hypixel
  sends a new screen or new items back. There is always lag in between (often 50-300 ms,
  sometimes much more). A screen can be open but **not filled with items yet**. That is
  why the code waits for a "loaded" slot (`isMenuLoaded`).
- Hypixel can change menu titles, slot positions, item names and chat messages in any update.
  When something "suddenly broke without code changes", check this first.

---

## 2. Bazaar mechanics `[WIKI]`

| Thing | Fact |
| --- | --- |
| Buy Order | You choose a unit price; it fills when someone sells at that price. Max 71,680 stackable / 256 unstackable items per order. |
| Sell Offer | You list items at a unit price; fills when someone buys. |
| Instant Buy | Buys from the cheapest sell offers. Quoted 4% above the cheapest offer; difference is refunded. |
| Instant Sell | Sells into the best buy orders. |
| Order limit | 14 buy orders + sell offers combined (28 with the Bazaar Flipper perk). |
| Tax | 1.25% on Bazaar sales (lower with the Bazaar Flipper perk). |
| Expiry | Orders and offers expire after 7 days. |
| Price protection | A confirm popup appears when buying for more than 2× the 7-day average, or instant-selling under 50% of it. Sell offers can't be listed above 10× the 7-day average. **This popup is an extra screen the state machine may not expect.** |
| Claiming | Filled orders are claimed from the Manage Orders menu (`/managebazaarorders`). |

Enchanted books: two books of the same level combine in an anvil into one book of the next
level (I + I → II). To make one level V book from level I books you need 2^(5-1) = 16 books
(this is `Book.getQtyAmount`).

---

## 3. Real chat messages `[LOG]`

The mod's `ChatHook` strips `§` color codes first. These are the plain texts after stripping.
`N` = a number (may contain commas, like `611,958`).

| Message | Matched by |
| --- | --- |
| `[Bazaar] Your Buy Order for Nx Ultimate Wise I was filled!` | `filled` hook → `handleFilledMessage` |
| `[Bazaar] Your Sell Offer for Nx Ultimate Wise V was filled!` | `filled` hook |
| `[Bazaar] Claimed Nx Ultimate Wise I worth N coins bought for N each!` | `Claimed` hook |
| `[Bazaar] Claimed N coins from selling Nx Ultimate Wise V at N each!` | `Claimed` hook (also!) |
| `[Bazaar] Buy Order Setup! Nx Ultimate Wise I for N coins.` | not hooked |
| `[Bazaar] Sell Offer Setup! Nx Ultimate Wise V for N coins.` | not hooked |
| `[Bazaar] Submitting sell offer...` | not hooked |
| `[Bazaar] Bought Nx Ultimate Wise I for N coins!` | not hooked (instant buy) |
| `[Bazaar] Sold Nx Ultimate Wise II for N coins!` | not hooked (instant sell) |
| `[Bazaar] Cancelled! Refunded N coins from cancelling Buy Order!` | not hooked |
| `[Bazaar] Cancelled! Refunded Nx Ultimate Wise V from cancelling Sell Offer!` | not hooked |
| `You have N unclaimed deliveries! ...` | not hooked (contains lowercase "unclaimed" — the `Claimed` hook is case-sensitive so it does not match) |

Reboot messages (`Scheduled Reboot`, `Game Update`) are what `ScheduledReboot` listens for.
They were **not** found in the local logs, so the exact wording is unverified.

Logs also show other servers' messages (e.g. `SHOP » You have sold ...`), so the developer
sometimes tests on other servers. Ask which server a log came from if it matters.

---

## 4. Hypixel API (`https://api.hypixel.net/v2/skyblock/bazaar`)

- No API key needed for this endpoint `[COMMUNITY]`.
- Top-level: `success`, `lastUpdated` (ms timestamp), `products` (map of product id → product) `[SRC: the mod's own parsing]`.
- Product ids for books: `ENCHANTMENT_<NAME>_<LEVEL>`, e.g. `ENCHANTMENT_ULTIMATE_WISE_1`.
- Each product has `quick_status`, `sell_summary`, `buy_summary`.
  - `sell_summary` / `buy_summary`: up to the top 30 order groups, each with `amount`,
    `pricePerUnit`, `orders` `[COMMUNITY]`.
  - `quick_status`: `sellPrice`, `buyPrice`, `sellVolume`, `buyVolume`, `sellMovingWeek`,
    `buyMovingWeek`, `sellOrders`, `buyOrders`. Prices are **weighted averages of the top ~2%
    of volume**, not the single best order, and can be skewed by outliers `[COMMUNITY]`.
- ⚠ **Naming trap** `[COMMUNITY]`: the names are from the *other player's* point of view.
  Commonly reported: `sell_summary` = **buy orders** (where instant-sell goes, best = highest),
  `buy_summary` = **sell offers** (where instant-buy goes, best = lowest). So
  `quick_status.sellPrice` ≈ instant-sell price and `buyPrice` ≈ instant-buy price.
  **Always confirm against the in-game Bazaar before concluding a price bug.**
- Update rate and rate limits for this endpoint are not documented in what we found. The mod
  polls every 20 s (`BazaarMonitor.duration`) and on each `FETCHING`.

---

## 5. Minecraft 26.1.2 / Fabric runtime facts

| Fact | Source |
| --- | --- |
| 26.1 is **unobfuscated** and uses **Mojang names**. Fabric dropped Yarn. Old tutorials using Yarn names (`MinecraftClient`, `ScreenHandler`, `Text`, `ClientPlayerEntity`, `HandledScreen`) will not match. Here they are `Minecraft`, `AbstractContainerMenu`, `Component`, `LocalPlayer`, `AbstractContainerScreen`. | fabricmc.net 26.1 announcement + `[SRC]` |
| `ResourceLocation` is now `net.minecraft.resources.Identifier`. | code + `[SRC]` |
| Slot clicks: `MultiPlayerGameMode.handleContainerInput(containerId, slotNum, buttonNum, ContainerInput, player)`. `ContainerInput` has `PICKUP`, `QUICK_MOVE`, ... | `[SRC]` |
| `END_CLIENT_TICK` fires 20×/s on the main client thread (`Render thread` in logs). | Fabric API |
| To run code on the main thread from another thread: `Minecraft.getInstance().execute(Runnable)` (from `BlockableEventLoop`). | `[SRC]` |
| `minecraft.player` and `minecraft.level` are **null** while changing worlds/servers (reboots, `/hub`, `/is`, disconnects). Any tick code that touches them must check. | crash history |
| A chest menu's `slots` list = container slots first, then the 36 player slots (27 main + 9 hotbar). A 6-row chest = 54 + 36 = 90 slots. That is why the code uses `slots.size() - 36`. | `[SRC]` + code |
| `Component.getString()` = plain text. `Component.toString()` = a debug form of the component tree — not meant for matching. | `[SRC]` |
| In-game chat keeps only the last **100** messages (`ChatComponent.MAX_CHAT_HISTORY`). Older debug lines scroll away. | `[SRC]` |
| Every chat line (including the mod's own `[GoofyAddons]` messages) is also written to the log as `[Render thread/INFO] (Minecraft) [System] [CHAT] ...`. | `[SRC]` + `[LOG]` |
| `AbstractSignEditScreen` stores the sign text in a private `String[] messages` (4 lines). | `[SRC]` |

### Where things are
| What | Dev environment (this repo) | Normal player install |
| --- | --- | --- |
| Current log | `run/logs/latest.log` | `.minecraft/logs/latest.log` |
| Old logs | `run/logs/*.log.gz` (read with `zcat`) | `.minecraft/logs/` |
| Crash reports | `run/crash-reports/` | `.minecraft/crash-reports/` |
| Mod config | `run/config/goofyaddons.json` | `.minecraft/config/goofyaddons.json` |
| Minecraft source | `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.1.2/*-sources.jar` | — |
| Fabric API source | `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/<module>/<version>/*/*-sources.jar` | — |

---

## 6. Rules context `[WIKI/Hypixel rules]`

Hypixel's rules do not allow macros or automation that play the game for you. Accounts can be
banned. The developer knows their project. This assistant's scope is debugging and teaching
(see `GEMINI.md` rule 8).
