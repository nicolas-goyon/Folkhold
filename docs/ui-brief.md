# Folkhold — Interface brief

This document describes all the interfaces of the Folkhold mods: screens, menus, HUD elements, messages and admin tools. It is the input for a designer or a design AI that makes new interface designs.

The brief gives the **functions**, the **data**, the **context** and the **constraints** of each interface. It does not give the layout. The designer selects the layout, the order of the elements, the grouping of the buttons, the menus and the sub-menus.

For the rules of the game, see the [design document](design.md).

## Contents

1. [Instructions for the designer](#1-instructions-for-the-designer)
2. [The game and its tone](#2-the-game-and-its-tone)
3. [Technical constraints](#3-technical-constraints)
4. [Shared components and visual language](#4-shared-components-and-visual-language)
5. [Inventory of the interfaces](#5-inventory-of-the-interfaces)
6. [Player interfaces: in-world information](#6-player-interfaces-in-world-information)
7. [Player interfaces: screens](#7-player-interfaces-screens)
8. [Player interfaces: planned](#8-player-interfaces-planned)
9. [Admin interfaces](#9-admin-interfaces)
10. [Settings](#10-settings)
11. [Reference values](#11-reference-values)

## 1. Instructions for the designer

### What is fixed

- The **functions**: each action in this brief must stay available in the new design.
- The **data**: each field in this brief must be visible in the new design, or available in one click or one hover.
- The **rules of the game**: who can do an action, and when. For example, a hostile player gets no quests.
- The **technical constraints** of section 3.

### What is free

- The layout of each screen: columns, panels, cards, lists, tables, grids.
- The navigation: tabs, sub-menus, drop-down lists, side bars, pages, breadcrumbs.
- The grouping of the actions and of the data. You can merge two screens into one, or split one screen into several screens.
- The entry points, if the new entry point is in the spirit of the game. For example, a key binding or an item for a screen.
- The visual style: colors, frames, textures, icons. Section 4 describes the current style. You can keep it or replace it.
- The replacement of chat messages with other feedback: toasts, notifications, status lines in a screen.

### What to produce

For each interface:

1. A wireframe at the minimum size (320 × 240 GUI pixels, see section 3).
2. A wireframe at a typical size (480 × 270 GUI pixels).
3. The list of the components and their states: normal, hover, focus, disabled, selected.
4. The empty state, the waiting state (a server answer is not received) and the error states.
5. The keyboard use.

For the complete set of interfaces:

1. A navigation map: how the player goes from one screen to another.
2. A component library that all screens share.
3. A short justification of the main layout choices.

### Priorities

1. The player understands the situation in less than 3 seconds: "Is this village safe for me? What can I do here?"
2. The player does frequent actions in one click: accept a quest, deliver items, trade.
3. Dangerous actions need a confirmation: pillage, abandon a quest, destroy a settlement (admin).
4. The admin screens show much data, but they stay readable at the minimum size.

## 2. The game and its tone

Folkhold is a set of mods for Minecraft Java Edition 1.21.1 (NeoForge). The world lives without the player:

- NPC **factions** have **villages**. The villages produce, consume and trade goods.
- **Caravans** travel on routes between the villages. **Traveling merchants** go from village to village.
- Each village has a **garrison**: guards, archers and a captain. The garrison defends the village.
- The player has a **reputation** with each faction and with each village. The reputation decides how the guards, the merchants and the quest givers behave.
- The villages give **quests**: deliveries of items and escorts of caravans.
- A village grows in **levels** (1 to 20). The levels group into **tiers**: Hamlet, Village, Town, City, Capital.

The player is one actor among others. The player can help a faction, trade with it, or attack it.

Setting and tone:

- Medieval, low fantasy, human cultures. The cultures are Valeholm, Ashmark and Sunreach. Each faction has a name, a color and a banner.
- Materials: wood, parchment, iron, gold leaf, wax seals, notice boards, ledgers.
- The interfaces are part of the world. A quest board looks like a notice board on a village square. A village heart shows the state of the village as a village hall would show it.
- The game is mostly single-player. Multiplayer stays possible: all player data uses the player UUID.

## 3. Technical constraints

### Screen size and scale

- Minecraft draws the interface in **GUI pixels**. The player selects a GUI scale (1, 2, 3, 4 or auto). One GUI pixel is 1 to 4 screen pixels.
- **Minimum usable area: 320 × 240 GUI pixels.** Each screen must fit in this area, with a margin of 6 to 10 GUI pixels.
- A large monitor at a small GUI scale gives more space, for example 960 × 540 GUI pixels. A screen can use more space when it is available, but it must not need it.

### Text

- The game font is a bitmap font. A line is 9 GUI pixels high. A capital letter is about 6 GUI pixels wide.
- There are **two languages: English and French**. French text is often 15 % to 30 % longer than English text. Each label must fit in the two languages.
- All text comes from translation keys. No text is drawn in a texture.
- Long text ends with "…" when it does not fit. A tooltip can show the full text.
- Names of villages and factions come from generated syllables. They can be up to about 20 characters.

### Input

- Mouse: left click, mouse wheel, hover for tooltips.
- Keyboard: Escape closes a screen. Tab moves the focus. Enter activates the focused button. The left and right arrow keys change the tab in the current tabbed screens.
- There is no controller support.

### Behavior

- The screens of the mod **do not pause the game**. In single-player, the world continues while a screen is open. A monster can attack the player. The design must not hide this risk: the screens stay compact, and the player closes them fast.
- **The server owns all data.** A screen shows the data that the server sends. When the player clicks an action button, the client sends the action to the server. The server checks the action, does it, and sends new data. The screen then shows the new data.
  - The design needs a waiting state between the click and the answer (usually less than 100 ms in single-player, more in multiplayer).
  - The server can refuse an action. The refusal comes as a message (see 6.5).
- A screen does not update by itself while it is open, except after an action of the player. The admin screen has a refresh button for this reason.
- A screen can keep the last tab, filter or selection for the next opening.

### Available graphic elements

- Vanilla widgets: button, text box, check box, slider, cycle button (a button that shows the next value on each click), tooltip, scroll bar.
- Item icons (16 × 16 GUI pixels) for each item of the game.
- Faction banners (vanilla banner patterns in the faction colors).
- Player heads and small entity models.
- Custom textures (sprite sheets) for frames, icons and backgrounds.
- Plain colored rectangles and lines.

## 4. Shared components and visual language

### Current visual language

The current screens use one style. You can keep it or replace it.

| Element | Current value |
| --- | --- |
| Panel | Dark brown, almost opaque (`#211B14`) |
| Frame | 1 pixel dark brown (`#2B1F12`) outside, 1 pixel gold (`#B8913F`) inside |
| Header | A darker band with the title in white, a gold line below |
| Tabs | Dark cells. The active tab is lighter, with a gold line on top and gold text (`#FFD37F`) |
| Rows | Alternate light stripes. Label on the left in beige (`#A89F8C`), value on the right in white |
| Scroll bar | 3 pixels wide, gold thumb |
| Empty state | Grey centered text, for example "No offers" |
| Footer | A "Done" button in the center |
| HUD lines | White text on a semi-transparent black band |

### Components that all screens need

- A panel with a title.
- Tabs or another section selector.
- A list of rows with a label and a value (key-value list).
- A list of entries with two lines of text and action buttons (quest list).
- A selectable list (settlement list of the admin screen).
- Filters: text search, number range, cycle selector, sort selector.
- Action buttons: normal, dangerous, disabled with a reason in a tooltip.
- A confirmation dialog for dangerous actions.
- Empty states and waiting states.
- Tags or badges: reputation tier, settlement tier, alert state, quest state, group state.
- A progress bar: experience, route progress, time left.
- A HUD line or HUD block for the in-world information.

### Colors with a meaning

- Each faction has a color. Use it for the faction name, the banner and the territory.
- The reputation tiers need a scale from danger to safety (see 11.1). Do not use the color alone: add a word or an icon.
- A settlement in ruins needs a different look from an active settlement (current: a muted brown text).

## 5. Inventory of the interfaces

Status: **Exists** = in the code now. **Rework** = exists, and an open issue asks for a new design. **Planned** = in the design document or in an open issue, not in the code.

### Player

| ID | Interface | Kind | Opened by | Status |
| --- | --- | --- | --- | --- |
| P1 | Territory title | Title in the center of the screen | Entry into a territory cell | Exists |
| P2 | Village message | Action bar | Approach to a village | Exists |
| P3 | Territory HUD | HUD, top right | Client option | Exists |
| P4 | Quest HUD | HUD, top left | Client option | Exists |
| P5 | Game messages | Chat | Game events | Exists |
| P6 | Village heart screen | Screen | Click on the village heart | Rework (#234) |
| P7 | Quest board screen | Screen | Click on the quest board or on the captain | Exists. Physical board changes (#224) |
| P8 | Bank pillage confirmation | Dialog | Click on a bank chest | Exists |
| P9 | Bank chest | Container | After P8 | Exists (vanilla chest screen) |
| P10 | Trade screens | Container | Click on a merchant | Exists (vanilla merchant screen) |
| P11 | Faction journal | Screen | Key binding or item | Planned (#103, J8) |
| P12 | Territories on maps | Map overlay | Map item, map mod | Planned (#102, #104, J8) |
| P13 | Map update (cartographer) | Screen or block | Block or item | Planned (#105, J8) |
| P14 | Escort tracker | HUD | Active escort quest | Planned (this brief) |
| P15 | Inn bed rental | Screen or dialog | Innkeeper or inn bed | Planned (design document) |
| P16 | Recruitment of escorts | Screen | Captain, ally tier | Planned (design document) |
| P17 | World anchor | Container | Click on the block | Planned (balance mod) |
| P18 | Event warning | HUD or title | Natural event near the player | Planned (events mod) |

### Admin (operator)

| ID | Interface | Kind | Opened by | Status |
| --- | --- | --- | --- | --- |
| A1 | Settlement admin | Screen | `/folkhold admin` | Rework (#223) |
| A2 | Caravans and quests overview | Screen | Button in A1 | Exists |
| A3 | Admin map | Screen | A1 | Planned (#233, J8) |
| A4 | Factions and relations | Commands only | `/folkhold factions` | Candidate for a screen |
| A5 | Player reputation and quests | Commands only | `/folkhold reputation`, `/folkhold quest` | Candidate for a screen |
| A6 | Simulation and profiler | Commands only | `/folkhold sim` | Candidate for a screen |

### Settings

| ID | Interface | Kind | Opened by | Status |
| --- | --- | --- | --- | --- |
| C1 | Client options | Config screen | Mods menu | Exists (generated by NeoForge) |
| C2 | Server and world options | Config screen | Mods menu | Exists (generated by NeoForge) |

## 6. Player interfaces: in-world information

These elements give information while the player plays. They do not take the mouse.

### P1. Territory title

**Context.** The player walks across the world. The territory of a faction starts before its villages. The title warns the player early: "You are now in the land of a faction that hates you."

**Trigger.** The player enters a territory cell (16 × 16 chunks) of a different owner. Overworld only.

**Data.**

- Faction territory: the faction name (in the faction color) and the reputation tier of the player with this faction.
- Unclaimed cell: the text "Unclaimed territory".

**Behavior.** Title in the center of the screen. Fade in 0.5 s, stay 2.5 s, fade out 1 s.

**Design questions.**

- Show the faction banner?
- Different look for a hostile faction?

### P2. Village message

**Context.** The player comes near a village (50 blocks from the village heart by default). The player must know before the walls if the guards will attack.

**Data.** The village name, the faction name, the level and the tier of the village, the reputation tier of the player in this village.

**Behavior.** Action bar (the line above the hot bar). The message shows once when the player enters the area.

### P3. Territory HUD

**Context.** Permanent information for the player who wants it. Off by default.

**Data.**

- Line 1: the faction that owns the territory and the reputation tier of the player. Nothing in an unclaimed cell.
- Line 2: the village near the player, if there is one.

**Position.** Top right of the screen. The design can propose another position. The HUD must not cover the vanilla elements: the boss bar at the top center, the effects at the top right, the hot bar at the bottom.

**Constraints.** Hidden when the player hides the GUI (F1) or opens the debug screen (F3).

### P4. Quest HUD

**Context.** The player has up to 3 active quests (configurable). The HUD reminds the player of the quests and of the time. Off by default.

**Data.** One line for each active quest: the text of the quest ("Deliver 16 × Bread", "Escort the caravan to Holmstead") and the minutes before the deadline.

**Position.** Top left of the screen.

**Design questions.**

- Show the village of the quest? An item icon for a delivery? The progress of an escort (see P14)?
- How to show a quest with less than 2 minutes left?

### P5. Game messages

The server sends these messages in the chat. The design can move some of them to toasts, notifications or a status line.

| Event | Message | Importance |
| --- | --- | --- |
| A neutral player touches a block of a village | "<Guard> Hey! Do not touch that. This is your only warning." | Danger |
| A friend comes near a guard | "Guard: Welcome, friend." (3 variants) | Flavor |
| The player is below the neutral tier and clicks a merchant | "Merchant: I do not trade with you." | Refusal |
| The player clicks an inhabitant | "Baker does not trade. The merchant of the village trades." | Help |
| The player clicks a hostile captain | "Captain: I have no work for you." | Refusal |
| The player clicks a quest board outside an active village | "This quest board is not in an active village." | Refusal |
| Quest accepted, abandoned, completed (with the reward), failed | "Quest completed: Deliver 16 × Bread. Reward: 24 emeralds." | Result |
| Quest refused: tier too low, too many quests, quest gone, items missing | "You need 16 × Bread." | Refusal |
| Bank chest warning (break attempt) | "This is the chest of the village bank. To break it is a pillage." | Danger |
| Bank pillaged | "You pillaged the bank of Holmstead." | Result |
| Caravan mule warning | "This mule carries the goods of a caravan. To open its chest is a raid." | Danger |
| Caravan raided | "You raided a caravan of Ashmark." | Result |
| Village destroyed | "Holmstead of Ashmark is destroyed." | Result |

## 7. Player interfaces: screens

### P6. Village heart screen

**Context.** The village heart is a block in the center of each faction village (the central banner). The player clicks it to learn about the village. The player can be a visitor, a trader, a quest taker or an attacker who studies the defense.

**Goal of the new design (#234).** Give the player what is useful first: the welcome, the reputation, the quests, the merchants and the services. Keep the village information (level, economy, territory) in a second part.

**Entry point.** Right click on the village heart. The heart is also the target of an attack: to destroy the village, the player kills the captain and breaks the heart.

**Data.**

| Group | Field | Example |
| --- | --- | --- |
| Identity | Village name (title) | Holmstead |
| Identity | Faction (name in its color) | Ashmark |
| Identity | Culture | Valeholm |
| Identity | Level and tier | 7 (Village) |
| Identity | Experience to the next level | 340 / 900, or "maximum level" |
| Identity | Alert state | calm, alert, under assault |
| Identity | Position | X 1204, Z -380 |
| Reputation | Reputation tier in this village | friend |
| Reputation | Effective value | 420 / 1000 |
| Reputation | Value with the village, and its weight | 600 (weight 70 %) |
| Reputation | Value with the faction, and its weight | 0 (weight 30 %) |
| Garrison | For each unit type (guard, archer, captain): alive, configured, dead | 4 / 5 alive, 1 dead |
| Economy | Population and maximum population | 18 / 24 |
| Economy | Number of jobs | 6 |
| Economy | Food covered | 85 % |
| Economy | Faction treasury | 1250 emeralds |
| Economy | Main stocks: item, quantity, sell price | Bread: 120 (sells at 0.40) |
| Territory | Claimed territory cells | 9 |

**Data for the new player-first part** (some data needs new server code):

- A welcome line that depends on the reputation tier.
- What the player can do here, with the current reputation:
  - trade (from neutral).
  - quests (from neutral, some quests from friend).
  - shelter at night.
  - inn (Town tier or higher, friend).
- The number of quest offers, with a link to the quest board screen (P7).
- The merchants of the village, and the best offers or the shortages (the items that the village needs).
- What the player must not do: pillage, attack, break blocks.

**Actions.**

- Close.
- Change the section.
- Planned: open the quest screen of this village.

**States.**

- No faction (a heart outside a faction village): only the village and garrison data.
- No settlement data: no economy and no territory section.
- Empty section: "No information".

**Current implementation.** A panel of 276 × 196 GUI pixels with one tab for each section: Village, Garrison, Reputation, Economy, Territory. Each tab is a key-value list. The screen keeps the last tab.

### P7. Quest board screen

**Context.** Each faction village has a quest board on its plaza, between the heart and the south gate. The captain of the village gives the same quests. The player comes to the board to find work, to deliver items and to follow the active quests.

**Entry points.**

- Right click on the quest board. The board becomes a large notice board of 3 × 3 blocks (#224). A click on each of the 9 blocks opens the screen.
- Right click on the captain of the village. A captain does not talk to a hostile player.

**Data: offers of the village.** For each offer:

- What to do: "Deliver 16 × Bread", "Escort the caravan to Holmstead".
- The kind: delivery or escort. Later: hunt, diplomacy, defense, colonization, rally (see 8.6).
- The reward: emeralds and reputation.
- The time to complete the quest, in minutes.
- The minimum reputation tier, if it is above neutral: "tier friend".
- For a delivery: the item (an icon is possible), the quantity, and the quantity that the player has in the inventory (new data).

**Data: active quests of the player.** For each active quest (all villages):

- What to do.
- The reward in emeralds.
- The village of the quest.
- The minutes before the deadline.
- For an escort: the state of the caravan (new data, see P14).

**Actions.**

| Action | On | Condition | Result |
| --- | --- | --- | --- |
| Accept | An offer | Tier high enough. Fewer active quests than the maximum (3 by default). The offer still exists. | The quest becomes active. An escort caravan leaves. |
| Deliver | An active delivery of this village | The player has all the items | The items go from the inventory to the stock. The reward comes. |
| Abandon | An active quest | None | The quest fails. Needs a confirmation in the new design. |
| Close | The screen | None | — |

**States.**

- No offers: "No offers".
- No active quests: "No active quests".
- Action refused: a message (see P5). The screen stays open.
- After each action, the server sends the new lists.

**Rules to show.**

- A quest offer expires after a delay (default 40 minutes).
- A delivery asks for at most 32 items.
- An escort: the caravan waits for an escort for 6 minutes. Then it leaves without the player.

**Current implementation.** A panel of 300 × 210 GUI pixels. Two tabs: "Offers (n)" and "My quests (n)". Each entry has two lines (what to do, then reward and time) and small buttons on the right (Accept, Deliver, Abandon).

**Design questions.**

- A "notice board" look with pinned papers, to match the physical board?
- A detail view for one quest (map position of the destination, route length, cargo value)?

### P8. Bank pillage confirmation

**Context.** Each village has a bank. The bank chests show the stocks of the village and a part of the faction treasury. To open a bank chest is a pillage. The pillage has a large cost. The player must understand it before the action.

**Entry point.** Right click on a bank chest of an active village.

**Data.**

- Title: "Pillage the bank?"
- Message: "This is a pillage. Your reputation with this village and with its faction becomes very negative."
- Possible new data: the village name and the faction. The current reputation, and the reputation after the pillage (-800 by default, sworn enemy). The alert of the garrison.

**Actions.** Confirm (dangerous). Cancel (default).

**Current implementation.** The vanilla confirmation screen ("Yes" and "No" buttons).

### P9. Bank chest

**Context.** After the confirmation, the bank chest opens. Its content is a view of the village stocks and of 40 % of the faction treasury (emeralds). When the player takes an item, the item leaves the stocks.

**Current implementation.** The vanilla chest screen with the title "Bank Chest".

**Design questions.** Optional: a header with the village name and a warning. The container grid must stay a vanilla container (slots of 18 × 18 GUI pixels, player inventory below).

### P10. Trade screens

**Context.** Three kinds of merchant sell items to the player:

| Merchant | Where | Stock | Prices |
| --- | --- | --- | --- |
| Village merchant | In the village | The real stock of the village | Local price from stock and need. Sell = price × 1.15. Buy = price × 0.85. |
| Caravan merchant ("Caravan of Holmstead") | On a route | The cargo of the caravan | Prices of the origin village |
| Traveling merchant | Near a village, then on the road | 6 rare items, small quantities, slow restock | Fixed prices (see 11.6) |

**Rules.**

- No trade below the neutral tier.
- Friends get a discount (×0.9 by default). Allies get a larger discount (×0.8 by default).
- No magic restock. The number of offers comes from the village level.
- When the player sells an item, its local price goes down.

**Current implementation.** The vanilla merchant screen.

**Design questions.** A custom trade screen could show:

- the quantity in stock for each offer.
- the price trend (surplus or shortage).
- the discount of the reputation.
- the items that the village buys at a good price.

The vanilla screen stays acceptable. This screen has a low priority.

## 8. Player interfaces: planned

### P11. Faction journal (#103)

**Context.** The player discovers the world. The journal is the memory of the player: which factions exist, how they see the player, where their villages are. The player uses it to plan a trip.

**Entry point.** A key binding (for example J) or an item (a journal). To decide.

**Discovery rules.**

- The player discovers a faction when the player enters its territory.
- The player discovers a village when the player gets its village message (P2).

**Data: factions.**

- Name, color, banner, culture.
- Attitude to the player at the start: ally, neutral, hostile.
- Reputation of the player: tier and value (-1000 to +1000).
- Relations with the other factions: value (-100 to +100) and state (alliance, peace, tension, war).
- Number of discovered villages.

**Data: villages.**

- Name, faction, level and tier, state (active or ruins).
- Position, distance and direction from the player.
- Reputation with the village.
- Date of the last visit.
- Optional: the active quests of the player in this village.

**Actions.** Filter (faction, tier, reputation, state). Sort (distance, reputation, name). Select a village to see its details. Optional: put a marker on the map (P12).

**Lore.** Each culture has a short history. The journal can show the lore that the player found in books.

### P12. Territories on maps (#102, #104)

**Context.** The player sees the territories on a map.

**Functions.**

- Vanilla map item: border lines in the faction colors, markers (banners) for the known villages. The borders are thin and optional.
- Map mod (for example JourneyMap): territory polygons, village markers, on the world map and the minimap. Optional dependency.
- Only the discovered territories and villages.

**Design questions.** The look of the borders, the markers for a village (tier, state, reputation), the legend.

### P13. Map update (#105)

**Context.** The player updates a map without a new visit to the area.

**Options (to decide).**

- A cartographer block: the player puts a map in it and pays a cost (paper, emeralds or time). The block updates the map.
- A surveyor map: a map item that updates itself, for example each 10 minutes.

**Data for a cartographer screen.** The map slot, the cost, the area to update, the time left, a limit on the number of chunks.

### P14. Escort tracker

**Context.** During an escort quest, the player protects a caravan against monster ambushes. The player must stay near the caravan. This information has no interface now.

**Data.**

- The destination and the progress of the caravan on its route (blocks done / total).
- The distance between the player and the caravan, and the escort distance (48 blocks by default).
- A warning when the player is too far: the caravan stops. A countdown before the quest fails (2 minutes by default).
- The health of the merchant (when the merchant dies, the quest fails).
- The time before the deadline (40 minutes by default).
- Optional: a direction marker to the caravan.

**Kind.** HUD, part of the quest HUD or a separate element.

### P15. Inn bed rental

**Context.** From the Town tier, a neutral or friendly player can rent a bed at the inn for emeralds, to take shelter at night.

**Data.** The price, the duration, the bed position. **Actions.** Rent, cancel.

### P16. Recruitment of escorts

**Context.** At the ally tier, the player can recruit escorts (units of the faction) and get access to elite units.

**Data.** The available units, their cost, their stats, the duration. **Actions.** Recruit, dismiss. The rules are not decided.

### P17. World anchor (balance mod)

**Context.** A block that keeps 3 × 3 chunks loaded. It burns fuel (for example 1 coal block each hour). Each player has 2 anchors at most.

**Data.** Fuel slot, time left, the loaded area, the owner, the number of anchors of the player. **Actions.** Add fuel, turn on, turn off.

### P18. Event warning (events mod)

**Context.** Before an earthquake or a collapse, the mod announces the event: rumble sounds, animals that run away, NPC alerts. A visual sign can add to the sounds.

**Data.** The kind of event, the direction or the area, the time before the event. A stabilizer block protects a radius of 16 blocks: the warning can say if the player is in a protected area.

### 8.6. Future quest kinds

The quest board (P7), the quest HUD (P4) and the journal (P11) must accept more quest kinds later:

| Kind | Description |
| --- | --- |
| Hunt | Monsters (later bandits) attack a trade route |
| Diplomacy | Carry a message to improve the relation between two factions |
| Defense | An army threatens an allied village (phase 2) |
| Colonization | Escort settlers or deliver materials to found a hamlet |
| Rally | A village changes its banner and joins another faction |
| Reconstruction | Help a village after a natural event |

## 9. Admin interfaces

The admin interfaces are for an operator (permission level 2) or for the developer during tests. They show the complete state of the world, also the villages that no player found. The readers know the rules of the game. The data can be dense.

### A1. Settlement admin

**Context.** The operator finds a settlement, looks at its state, changes it and goes to it. The operator also runs the simulation by hand to test the balance.

**Entry point.** The command `/folkhold admin`. A player who is not an operator gets the message "You must be an operator to use the admin screen."

**Request of the rework (#223).**

- Fewer buttons on the screen at the same time. Group the actions in sub-menus: village, economy, garrison, world.
- Short labels, with a tooltip for the details. No text larger than its button, at all GUI scales.
- Keep the list of the settlements and the details.

**List of the settlements.**

| Element | Function |
| --- | --- |
| Counter | "12 / 48 settlements": visible and total |
| Text filter | Name, id (start of the id) or faction |
| Level range | Minimum and maximum level (numbers, empty = no limit) |
| State filter | All, Active, Ruins |
| Sort | By distance, by level, by name |
| Row | Name (muted color for ruins), level, distance in blocks ("Lv 7 · 230m") |
| Selection | A click on a row shows the details |

Other data of each settlement in the list (sent by the server, not shown now): faction, tier, position X and Z, population.

**Details of the selected settlement.**

| Field | Example |
| --- | --- |
| Name (title) | Holmstead |
| Id | `a3f9c2…` |
| Faction | Ashmark |
| State | Active, Ruins |
| Level | 7 (Village) |
| Experience | 340 / 900 |
| Position | 1204, 72, -380 (Y unknown before the heart is placed) |
| Heart | Loaded, Not loaded |
| Alert state | Calm, Alert, Assault (only when the heart is loaded) |
| Biome | `minecraft:taiga` |
| Claimed cells | 9 |
| Population | 18 / 24 |
| Inhabitants to show | 6 |
| Food | 85 % |
| Jobs working | 70 % |
| Jobs | One row for each job and its count ("Job: baker 2") |
| Faction treasury | 1250 |
| Stock | 640 / 2000 |
| Main stocks | The 8 largest stocks: item and quantity |
| Economy | "No job data" when the job data files are missing |

**Actions on the selected settlement.**

| Action | Group (proposal) | Needs a value | Condition | Dangerous |
| --- | --- | --- | --- | --- |
| Teleport to the heart (closes the screen) | Village | No | — | No |
| Set the level (experience = 0) | Village | Yes (1 to 20) | — | No |
| Add experience (negative = remove) | Village | Yes | — | No |
| Ruin: destroy the settlement, its stock goes into the bank chests | Village | No | — | Yes |
| Restore: active again, with heart, bank and garrison | Village | No | Ruins | No |
| Run one economic cycle now | Economy | No | — | No |
| Reset the economy: remove the stock, start again | Economy | No | — | Yes |
| Set the alert state: calm, alert, assault | Garrison | Yes (one of 3) | Heart loaded | No |
| Ring the bells | Garrison | No | Heart loaded | No |
| Set the stock of an item (command only now) | Economy | Item and count | — | No |
| Calculate a route to another settlement (command only now) | World | Target settlement | — | No |

**Global actions.**

| Action | Function |
| --- | --- |
| Refresh | Get the list and the details again |
| Tick all | One abstract tick for all active settlements and the world systems |
| Cycle all | One cycle (one game day of economy) for all active settlements |
| Caravans and quests | Open A2 |
| Done | Close |
| Run N ticks or N cycles (command only now) | `/folkhold sim tick|cycle <count>` |

**Feedback.** After each action, the server sends a message ("Holmstead is now level 8 (Village)") and the new list and details. Errors: "Unknown settlement", "The heart of Holmstead is not loaded".

**Current implementation.** A panel of up to 420 × 260 GUI pixels. Left column: filters on two lines, then the list. Right column: the details, then a grid of 4 × 3 action buttons with a value box. Footer: 5 global buttons. The screen keeps the filters and the selection for the next opening.

### A2. Caravans and quests overview

**Context.** The operator follows the mobile groups and the quests of the world: where the caravans are, if they are stuck, who has which quest.

**Entry point.** The button "Caravans and quests" of A1.

**Data: caravans and traveling merchants** (one list each).

| Field | Example |
| --- | --- |
| Short id (8 characters, for the commands) | `7b21e0af` |
| Origin → destination | Holmstead → Brindle |
| Has entities near a player (mark) | ● |
| State | waiting, traveling, stopped, arrived, lost |
| Progress | 420 / 1300 blocks |
| Cargo | 64 items |
| Position | X 1500, Z -220 |

The command `/folkhold caravan info` gives more data. The screen does not show it now:

- kind, faction, departure tick, entities kept.
- members (merchant, pack animals, guards) and their health.
- cargo items and payment in emeralds.

**Data: quests.**

| Field | Example |
| --- | --- |
| Village and quest | Holmstead: Deliver 16 × Bread |
| State | offered, accepted, completed, failed, expired |
| Player, or "offer" | Steve |
| Reward | 24 emeralds |

**Actions available as commands only** (candidates for buttons):

- Caravan: info, teleport to the group, remove the group, spawn a caravan from one settlement to another.
- Quest: list the quests of a player, give a delivery or an escort of the nearest village, complete a quest, fail a quest.

**Current implementation.** The tabbed screen of the village heart (P6) with three tabs: Caravans, Merchants, Quests. Each row is a label and a value. No actions.

### A3. Admin map (#233)

**Context.** The operator sees the world from above.

**Data.** The villages (active and ruins), the territories in the faction colors, the caravans and the traveling merchants with their routes, later the bandit camps. The coordinates of each element.

**Actions.** Pan, zoom, select an element to see its details (link to A1 or A2), teleport to an element. Filters by kind and by faction.

### A4. Factions and relations (commands now)

**Data.** For each faction: name, id, culture, attitude to the player, territory center. For each pair of factions: relation value and state.

**Possible screen.** A list of the factions, a relation matrix, the treasury and the villages of each faction.

### A5. Player reputation and quests (commands now)

**Data.** For one player: the reputation with each faction (value and tier), the active quests.

**Actions.** Set the reputation of a player with a faction (-1000 to +1000). Give, complete or fail a quest.

### A6. Simulation and profiler (commands now)

**Data.** For each system of the simulation: number of calls, average time, maximum time, last time, total time (ms). The number of routes in calculation.

**Actions.** Reset the measurements. Run N abstract ticks or N cycles.

## 10. Settings

### C1. Client options

Two options for each player:

| Option | Default |
| --- | --- |
| Territory HUD (P3) | Off |
| Quest HUD (P4) | Off |

Planned options: the position of each HUD, the escort tracker (P14), the size of the text.

### C2. Server and world options

NeoForge generates a screen from the config file. The design can propose a better screen. The groups and the options:

| Group | Options |
| --- | --- |
| Difficulty | Health multiplier, damage multiplier |
| Defender behavior | Patrol radius, chase radius, alert radius, monsters attack guards |
| Units (guard, archer, captain) | Health, attack damage, armor, movement speed, follow range |
| Villages | Village radius, monster spawn protection radius, delay before calm, village message distance, maximum inhabitants |
| Garrison | Guards, archers, captains, refill delay, activation radius, time of an archer on one tower |
| Captain | Morale aura radius, share of units that run away, rout duration |
| Reputation | Village weight, spread to allies, spread to enemies, value of each offense (kill or hit a unit, kill a monster near a village, offense in a village, offense window, destruction of a village, hit or kill a village cat, raid a caravan) |
| Simulation | Discovery radius, discovery checks per tick, interval of the abstract tick, ticks in one cycle, time budget |
| Levels | Base prosperity, maximum experience for each cycle, loss at the death of the captain, loss for a food shortage |
| Economy | Inhabitants for each job, food for each inhabitant, decay of the stocks, elasticity of the prices, start treasury, minimum treasury |
| Bank | Reputation after a pillage, experience loss for a pillage, share of the treasury in the banks |
| Mobile groups | Movement on each tick, maximum groups, entity distance and margin, route grid, maximum route length, maximum water depth, maximum cells for a route |
| Trade | New caravans on each tick, items in one caravan, new routes on each tick, relation gain for each caravan, traveling merchants, stay of a traveling merchant |
| Quests | Offer duration, delivery time, delivery reward factor and reputation, maximum items of a delivery, reward for friends only, active quests of a player, escort offers, escort wait, escort distance, time away, escort time, escort reward share and reputation |
| Merchants | Price factor for a friend, price factor for an ally |

## 11. Reference values

### 11.1. Reputation tiers

| Tier | Range | Effect |
| --- | --- | --- |
| Sworn enemy | -1000 to -600 | Attacked on sight, bounties, raids on the player base |
| Hostile | -600 to -200 | Attacked on sight in the territory. No quests, no trade |
| Neutral | -200 to +200 | Tolerated, warning before attack, basic trade, basic quests |
| Friend | +200 to +600 | Better prices, advanced quests, lodging, greetings, open gates |
| Ally | +600 to +1000 | Best prices, recruitment of escorts, elite units, support in war |

Effective reputation in a village = 70 % village reputation + 30 % faction reputation.

### 11.2. Settlement tiers

| Levels | Tier |
| --- | --- |
| 1–4 | Hamlet |
| 5–8 | Village |
| 9–12 | Town |
| 13–16 | City |
| 17–20 | Capital |

### 11.3. States

| Subject | Values |
| --- | --- |
| Alert state of a village | Calm, Alert, Under assault |
| Settlement state | Active, Ruins |
| Faction attitude | Ally, Neutral, Hostile |
| Relation between factions | Alliance, Peace, Tension, War |
| Quest | Offered, Accepted, Completed, Failed, Expired |
| Mobile group | Waiting, Traveling, Stopped, Arrived, Lost |
| Member of a group | Merchant, Pack animal, Guard |

### 11.4. NPCs

| NPC | Role | Status |
| --- | --- | --- |
| Inhabitant (baker, lumberjack, …) | Population, job. Does not trade | Exists |
| Merchant | Trade with the player | Exists |
| Guard | Melee defense | Exists |
| Archer | Ranged defense on the towers | Exists |
| Captain | Leader of the garrison, quest giver | Exists |
| Village cat | Keeps creepers away | Exists |
| Caravan merchant | Leads a caravan, trades on the road | Exists |
| Traveling merchant | Rare items | Exists |
| Rider, faction golem, healer | Other units | Planned |

### 11.5. Jobs

Farmer, gardener, baker, rancher, butcher, fisher, beekeeper, lumberjack, miner, digger, shepherd, forager, smelter, mason, dyer, papermaker, blacksmith, fletcher, carpenter, tanner, weaver, tinker, pastry cook, sawyer, gold smelter. Some jobs have variants for the biome (for example lumberjack for spruce or birch).

### 11.6. Items of the traveling merchant

Name tag, saddle, ender pearl, blaze rod, nautilus shell, golden apple, diamond, slime ball, glow ink sac, amethyst shard, spyglass, music disc "cat", cherry sapling, mangrove propagule, sea pickle, pointed dripstone. Each traveling merchant sells 6 of these items.
