# Master Build Prompt — Triledge (To-Do / Spending / Trading)

App name: **Triledge**. Use it in the app label, splash/first-run screen, and about page.

You are building a lightweight, offline-first Android app in Kotlin. Read this entire brief before writing code. Where something is ambiguous, prefer the simplest implementation that satisfies the constraint, and ask rather than assume for anything touching money calculations or data storage.

## First open — onboarding
- On first launch, before anything else, ask the user's name in a single simple screen ("What should we call you?"). Store it locally only (Room or DataStore), never transmitted anywhere. Use it for light personalization in greetings/headers (e.g. "Hey {name}") — nothing more elaborate than that.

## Non-negotiable constraints
- **Kotlin + Jetpack Compose**, targeting current stable Android APIs. Keep the APK as small and dependency-light as realistically possible — this is a core selling point, not a nice-to-have.
- **100% local storage** for To-Do and Spending data, and for Trading data unless the user explicitly opts into broker-connect (still stored locally even then — the broker API is a data *source*, not a sync target). Use Room (SQLite).
- **No cloud, no analytics SDKs, no ad SDKs, no telemetry that phones home.** The only network calls this app ever makes are: (a) the optional AI chat, (b) the optional broker-connect fetch, (c) the optional AI-assisted voice-to-todo feature, and (d) fetching a thumbnail/preview image for a shared link on a to-do — all opt-in or clearly disclosed, off by default where applicable, in plain language before first use.
- **No SMS permission, no Call Log permission, no Accessibility Service, no lock-screen overlay.** Do not implement any form of automatic transaction detection from SMS/notifications. This is a deliberate, permanent decision, not a v1 shortcut — do not suggest adding it later.
- Minimal permission footprint overall. If you find yourself wanting a sensitive permission, stop and flag it instead of adding it.

## App shell
- Four destinations via bottom navigation (or top nav): **Home** (default landing screen on open), then a horizontally swipeable pager (`HorizontalPager` + `TabRow`) for **To-Do → Spending → Trading**, in that order. Users reach To-Do/Spending/Trading either via nav or by swiping between them once inside the pager; Home is always the first thing shown when the app opens.
- Fast-capture entry point: a home-screen widget (Glance API) and/or a persistent notification with quick-action buttons for "add expense" and "add trade," landing on the relevant entry screen. Do not attempt to build custom lock-screen or double-tap-gesture detection — that's out of scope (see Non-Goals).

## Home — Dashboard
This is the first screen the user sees on every open. Design it with real information hierarchy, not a wall of widgets — and with deliberate, positive psychology: celebrate progress, never guilt-trip about gaps or missed days.

- **Top of to-do list**: a compact preview of the next few most relevant items (pull from the Urgent and Today buckets — see To-Do categorization below), tappable straight into the To-Do tab. Not the full list — just enough to orient.
- **Statistics strip**: small, glanceable numbers — today's spend, this week's net trading P&L, to-do completion rate for the day/week. Use compact infographic components (sparkline for spend trend, a simple ring/progress indicator for completion rate, a small up/down indicator for P&L) rather than raw numbers alone — this is meant to feel like a modern dashboard, not a table.
- **Motivational quote card**: shows one quote, randomly picked from the time-of-day-appropriate uploaded list (see "Quotes" in Settings below), re-randomized every time the app is opened or resumed.
- **Consistency tracker**: track which calendar days the app was opened/used (a simple local log of dates), and show (a) current streak (consecutive days used) and (b) total days used, ideally as a small calendar-heatmap-style strip (like a contribution graph) rather than just numbers — it's more visually satisfying and reinforces the habit. **Frame this positively** — celebrate streaks and milestones, and avoid shaming copy or red/alarming visuals for gaps in usage. A missed day should read neutrally, not like a failure state.
- Keep the whole screen scannable in a few seconds; this is an entry point, not a reporting tool.

## To-Do — categorized, not one long list
Instead of a single flat list, organize the To-Do tab into clear sections/filter tabs so it never feels cluttered:
- **Urgent** — user-flagged high priority (a simple priority toggle on any item).
- **Today** — due today.
- **In Loop** — items with a recurring reminder enabled (see the repeating-reminder feature below).
- **Weekly** — due within the current week, or explicitly recurring weekly.
- **Whenever** — no due date / low priority, a someday-maybe bucket.
Items can appear filtered into whichever section matches their current state; this is a view/filter concern, not a change to the underlying data model — a single to-do table with priority/due-date/recurrence fields, sliced into these five views.

## Feature 1: To-Do — agentic capture
Base task list: title, optional note, optional due date, priority flag (for Urgent), optional recurring-reminder setting (for In Loop), done/not-done toggle, category (user-managed list, same pattern as Spending categories), sort by due date or creation order. See "To-Do — categorized, not one long list" above for how these fields map to the five view buckets.

**Voice capture (mic icon in the top search bar):**
- Tapping the mic records audio and transcribes it **on-device** using Android's built-in speech recognition (`SpeechRecognizer` / on-device recognition where available) — raw audio should not leave the device.
- The resulting text transcript is then sent to the user's selected AI model (see "AI provider settings" below) to be parsed into one or more structured to-do items (title, due date if mentioned, category if inferable). This step is what leaves the device — disclose that plainly the first time voice capture is used.
- If no AI provider is configured, fall back gracefully: just drop the raw transcript in as a single to-do title, no structuring, no error state.

**AI provider settings (shared with AI Chat):**
- One settings section where the user adds their API key(s) and picks which provider/model to use. Let them select which configured model powers Agentic To-Do vs. AI Chat independently if they've added more than one, but it's the same underlying key-storage and provider-config system — don't build two separate settings screens for this.
- **Usage tracking:** log which provider/model handled each call and the token count returned in that call's response (most provider APIs return usage/token counts — capture it, don't estimate). Store this locally and surface it in Settings as: today's token usage and estimated cost, and all-time total token usage and estimated cost, broken down per provider/model. Estimated cost uses a per-provider rate the user can edit in Settings (same "don't hardcode as permanent fact" principle as the broker presets — pricing changes, so make the rate field editable with a "verify current pricing" note rather than asserting a number).

**Uncertain-creation review flag:**
- When the AI creates a to-do it's not fully confident about (missing/ambiguous due date, unclear title, low-confidence parse, etc.), mark it with a small red "!" badge in the corner of the item.
- Tapping the badge opens a review view with professional, calm copy — something like "This one needs a quick check" — where the user can edit the unfilled/wrong fields or simply clear the flag if it's actually fine. Don't use alarming or apologetic language.

**Link-based to-dos:**
- User can share a link into the app from YouTube, Instagram, or any other app's share sheet, which creates a to-do referencing that link.
- Fetch a preview thumbnail image for the link (standard Open Graph / link-preview metadata fetch) and show it as a small circular thumbnail on the to-do item as a visual reminder. This is a disclosed, on-demand network call limited to that one URL's metadata — nothing else.
- **The raw link must never be shown as visible link text.** The user types their own note (e.g. "Watch this video") and then manually selects a word or phrase within that note and attaches the URL to it as an `href`, the way a rich-text editor's "insert link" works. Do not auto-detect or auto-linkify words/URLs — the href is always an explicit, user-driven action on user-selected text.
- Link-based to-dos can optionally be turned into a **repeating reminder** (a simple interval picker — daily, every N hours, custom) using WorkManager-scheduled local notifications, the same pattern as a habit reminder (e.g. "drink water"). This applies to any to-do, not just link-based ones — build it as a general recurring-reminder option on any item.

## Feature 2: Spending — manual entry only, no automation
- Entry fields: amount, category, optional note, date/time (defaults to now), account tag, payment-app tag.
- **Category is picked by the user from a list they manage in Settings — no auto-guessing, no AI categorization, no "skipped category" nudges or reminders.** If a category isn't picked, just leave it uncategorized; do not build a notification or nudge system for this.
- **Account tag**: user maintains a list of their own account labels in Settings (e.g. "HDFC Savings," "ICICI Salary"). Selecting one per entry is a simple chip/toggle — purely descriptive metadata, no bank integration of any kind.
- **Payment-app tag**: same pattern — user maintains a list of payment apps they use (GPay, PhonePe, Paytm, Cash, Card, etc.), selects one per entry via radio buttons/chips.
- Summary view: totals by category, by account, by payment app, and by day/week/month. Keep it a straightforward aggregation screen, not a dashboard with excessive visualization.

## Feature 3: Trading — net P&L journal
Goal: track net cumulative P&L across the user's entire trading history, computed *after* brokerage and statutory charges, since broker apps like Kite often delay showing net-of-charges figures by a day.

**Trade entry fields:** instrument/symbol, buy price, sell price, quantity, product type (MIS / CNC / NRML / BO / CO — make this an editable list, not a hardcoded enum, since brokers vary), segment (equity intraday / equity delivery / F&O / currency / commodity), entry and exit date-time.

**Broker preset system:**
- Ship a Settings screen with editable "broker presets" — named charge templates (e.g. "Zerodha," "Upstox," "Angel One," "Custom") containing: brokerage (flat per order or % capped, whichever is lower), STT rate, exchange transaction charge rate, SEBI turnover charge, stamp duty rate, GST rate applied to (brokerage + transaction charges), and DP charges for equity delivery sells.
- **Seed these with clearly-labeled placeholder/example values and a visible "verify these against your broker's current rates before relying on the numbers" notice in the UI.** Do not present the seeded numbers as authoritative or current — brokerage and statutory charges change over time and vary by segment, and this app should never assert a specific rate as fact. Make every field trivially editable.
- Users can duplicate/tweak a preset or build a fully custom one.

**Calculation engine:** build one shared module that takes a trade (or a raw fetched fill) plus a broker preset and returns: gross P&L, total charges breakdown (each charge type itemized), and net P&L. Use this same engine whether the trade was entered manually or pulled from a broker connection, so there's one source of truth for the math.

**Cumulative dashboard:** running net P&L across the user's full trading journey, filterable by date range, segment, product type, and broker preset used.

**Optional broker-connect (Kite Connect), scoped narrowly for v1:**
- This is opt-in, off by default, and clearly disclosed as a network feature before enabling.
- Zerodha's Kite Connect Personal API is free for individual use and covers orders/positions/holdings — no live/historical market data subscription needed for this use case.
- It's per-user: each person who wants this generates their **own** free API key/secret under their **own** Kite developer account (same BYO-credentials pattern as the AI chat below) — the app does not hold or proxy a shared key.
- Fetched trade data is stored locally only, immediately, and run through the same P&L calculation engine as manual entries.
- Build this as a separable module so the app is fully functional and shippable without it. Don't let it block v1.
- Do not attempt to support other brokers (Upstox, Angel One, Groww, etc.) in v1 — each has a different API with different terms; scope creep here isn't worth it yet.

## Feature 4: AI Chat (optional, off by default)
- Settings toggle, disabled until the user pastes in their own API key for a provider of their choice. Store the key in Android Keystore / EncryptedSharedPreferences, never in plain storage. This is the same provider/key infrastructure used by Agentic To-Do (Feature 1) — build one shared "AI provider" module, not two.
- Before first use, show a plain-language disclosure that messages — and, if the user asks questions about their own data (e.g. "how much did I spend on food this month"), a summary of the relevant Spending/Trading data — will be sent to that provider's cloud API. Prefer sending aggregated summaries over raw line-by-line entries when building context for a query, to minimize what leaves the device.
- Along with broker-connect and the two items in Feature 1, this is one of the sanctioned reasons for a network call. Keep the provider logic isolated in its own module, shared by both AI features.

## Feature 5: Customization
- Theming: multiple color themes (light/dark + a few accent palettes at minimum), selectable shape styles for cards/buttons (e.g. sharp vs. rounded vs. pill), overall appearance settings.
- Per-category icon and color pickers for To-Do and Spending categories.
- Sticker import: let the user import images to use as stickers/decorations on to-do items or category icons — store locally, no bundled sticker marketplace or remote asset fetching needed for v1.
- Treat this as a real, visible part of the product (it's a stated goal, not a stretch feature) but build it after the core data flows in each tab work end-to-end — see build order.

## Feature 6: Quotes (powers the Home dashboard's motivational quote card)
- In Settings, three separate upload slots: **Morning**, **Evening**, **Night**. Each takes one plain-text file and is used only during its own time window.
- Default time windows (editable in Settings): Morning 5:00–12:00, Evening 12:00–18:00, Night 18:00–5:00 — pick sensible boundaries and let the user adjust the split with a simple slider/time-range picker.
- **File format**: use one quote per line as the primary supported format (simplest and most robust for plain text). Also support a configurable multi-line delimiter (default a blank line, i.e. two consecutive newlines) for quotes that span more than one line — avoid double-space as the delimiter since ordinary punctuation naturally produces double spaces and would silently corrupt the list; a newline or blank-line delimiter is much safer. Whatever the parsing rule, state it clearly in the upload UI so the user knows how to format their file.
- Re-uploading a file for a given slot **completely replaces** the previous list for that slot — no merging/appending.
- Home dashboard picks one random quote from the current time-window's list every time the app is opened or resumes from background.
- Let the user **export** the current quote list for any slot back to a plain-text file (same one-quote-per-line format), so they can edit and re-upload it.

## Settings screen
- User's display name (editable after onboarding), manage account labels, manage payment-app labels, manage broker presets, manage to-do/spending categories + icons/colors, AI provider setup (add key(s), pick model per feature, view daily and all-time token usage + estimated cost per provider/model) shared by AI Chat and Agentic To-Do, toggle broker-connect + manage per-broker credentials, theming/appearance (colors, shapes, stickers), Quotes (upload/export Morning/Evening/Night files, adjust time-window boundaries), data export (local backup file), and an about/privacy page stating plainly what does and doesn't leave the device — including the voice-transcript-to-AI step and the link-preview fetch.

## Explicit non-goals — do not build these
- Any SMS, Call Log, or Notification Listener access.
- Any Accessibility Service usage.
- Any lock-screen overlay or custom double-tap gesture detection.
- Any cloud sync, backup-to-cloud, or account/login system.
- Any ads or ad SDKs.
- Any auto-categorization or automated nudges/reminders for Spending.
- Hardcoding brokerage/tax rates as if they're current, permanent facts.
- Sending raw voice audio off-device — transcription must be on-device; only the resulting text goes to the AI provider, and only when Agentic To-Do is actually used.
- Auto-linkifying words or URLs in to-do notes. Every href is an explicit, user-selected action — never automatic detection of link-shaped words.

## Suggested build order
1. Onboarding (name capture) + app shell: Home + three-tab horizontal pager, navigation, empty screens.
2. Spending: manual entry, local storage, account/payment-app tag management, summary view.
3. To-Do: basic list with priority flag, category, and the five-bucket categorized view (Urgent/Today/In Loop/Weekly/Whenever) — no agentic features yet.
4. Trading: manual entry, broker preset management, shared P&L calc engine, cumulative dashboard.
5. Settings: consolidate all list/preset management from steps 2–4 into one place.
6. Home dashboard: to-do preview, statistics strip with infographics, consistency tracker (local usage-date log + streak calc). Quote card can render placeholder/empty state until step 11.
7. Fast-capture widget/notification actions.
8. Shared AI provider module (key storage, provider/model selection, usage/token tracking) — build once, used by steps 9 and 10.
9. AI Chat feature, using the shared provider module.
10. Agentic To-Do: on-device voice transcription → AI structuring → review-flag UI, using the shared provider module.
11. Link-based to-dos: share-sheet intake, link-preview thumbnail fetch, manual href-on-selected-text editor, recurring-reminder scheduling (also powers the "In Loop" bucket).
12. Quotes: Morning/Evening/Night upload + parsing + export, wired into the Home dashboard's quote card.
13. Kite Connect module (isolated, opt-in).
14. Customization: theming, shapes, category icon/color pickers, sticker import.
15. Privacy page, minimal permissions review, and a pass to confirm nothing calls the network outside the disclosed, opt-in features.

Build incrementally and get each numbered step working end-to-end before moving to the next — don't build everything's data layer simultaneously.
