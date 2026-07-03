# Master Design Prompt — Triledge (UI/UX for Claude Design)

You are the design lead on Triledge: an offline-first phone app that holds three very different kinds of data — a to-do list, a spending log, and a trading P&L journal — under one roof. Your job is a complete visual identity and screen-by-screen high-fidelity mockup set, not a generic Material Design skin. Read this whole brief before designing anything.

**Direction in one line:** premium, dark-first fintech (think CRED) — confident, a little metallic, unmistakably not a default Compose theme.

## Anti-patterns — do not default to these
- No cream background + high-contrast serif + terracotta accent (an overused AI-default look).
- No pure-black background with a single neon-green or vermilion accent (the other overused AI-default).
- No stock unstyled Material 3 components — every button, chip, and card gets this app's own shape and color language.
- No pie charts or heavy charting on the Spending summary — the brief explicitly wants restrained aggregation, not a BI dashboard.
- No red/alarm styling for a missed streak day — that violates the app's positive-psychology requirement.
- No visible raw URL text anywhere (to-do links are always hidden behind user-selected words, per the build brief).

## Design system

**Color** (dark-first; a light theme exists as secondary and should invert these roles, not just flip black/white):
- Base background: `#12141C` — deep navy-charcoal, not flat black
- Elevated surface: `#1A1D28`
- Card surface: `#21242F`
- Hairline / border: `#343849` at low opacity — used instead of drop shadows for most elevation
- Primary text: `#F4F2EC` (warm off-white)
- Secondary text: `#9195A6`
- **Signature accent — antique gold** `#D9A94E`: primary CTAs, the streak flame, the ring motif's base track, nav active state. This is the one color that appears everywhere and ties the app together.
- **Warm-zone accent — coral** `#FF8F65`: Home + To-Do, celebration, quote card
- **Serious-zone accent — indigo** `#5E72E4`: Trading, data-forward surfaces
- Gains: muted sage `#6FCF97` · Losses: muted rose `#EF6C7C` (avoid pure saturated red/green — too alarm-like for a journal you check daily)
- Category palette (user-assignable, for to-do/spending tags): gold, coral, indigo, sage, rose, teal `#3FBAC2`, violet `#A385E8`, slate `#6B7280`

**Typography** — three roles, used deliberately, not the same pairing you'd reach for on any other brief:
- Display (bold, slightly expanded — e.g. Clash Display or similar): screen titles and the big hero numbers (P&L figure, streak count). Used sparingly — this is the "premium" signal, not body text.
- Body (e.g. General Sans or Inter): everything else — list items, labels, copy.
- Data/mono, tabular figures (e.g. IBM Plex Mono): every money amount, trade row, and token-usage number. Numbers that line up like a ledger is the app's quiet way of saying "we take your money seriously."

**Shape & elevation**
- Cards: 20dp radius. Trading data tables/rows: tighter 14dp — signals "focus mode." Pills/chips: full radius.
- Elevation via a 1px inner top highlight + hairline border, not heavy shadows. Reserve a soft outer glow for pressed/focused states only.
- 4pt spacing grid, 16dp screen margins, 64dp bottom nav sized for one-handed thumb reach.

**Iconography & motion**
- Custom line icons, 1.5px stroke, rounded caps; filled variant on selection. Each of the four nav destinations gets its own icon that echoes the ring motif below.
- Motion is restrained: the signature ring fills on load, stat numbers count up once, page transitions are a soft cross-fade/slide. No bouncy overshoot everywhere — over-animating is one of the things that makes an interface read as AI-generated. Respect reduced-motion settings (ring fills instantly, no count-up).

## The signature element: The Ring

To-do completion, spend-vs-trend, trading P&L, and the daily streak are four unrelated metrics. Unify them with one recurring shape: a circular ring, gold track, colored fill (coral/indigo/sage/rose depending on context). It shows up as the Home completion ring, the streak's heatmap-strip anchor, the Trading dashboard's win-rate gauge, and small inline indicators next to list items. This is the one thing a user should recognize as "this is Triledge" across all three tabs — spend your visual boldness here, keep everything around it disciplined.

## Adaptive tone system

Settings has a **Dashboard Tone** toggle: **Adaptive** (default) or **Uniform**. Design both states, most visibly on the Trading screen so the difference is obvious:
- **Adaptive:** Home and To-Do lean coral, 20–22dp radii, friendlier line-illustration empty states, warmer celebratory microcopy. Trading leans indigo, 14dp radii, denser ledger rows, more mono/tabular numerals, tighter line-height — a visibly more "focused" register. Spending sits between the two: neutral gray/gold structurally, with sage/rose only on the amount itself.
- **Uniform:** one consistent radius and density app-wide (use the warmer, 20dp treatment as the baseline). Functional color-coding stays (gains/losses, category colors) — only the structural personality stops shifting between tabs.

## Screens to mock up

**Onboarding** — single centered dark screen, the gold ring mark animates in, one line: "Welcome to Triledge. What should we call you?" Thumb-anchored text field + gold pill "Continue" button. Nothing else on screen.

**App shell** — bottom nav, 4 items (Home / To-Do / Spending / Trading), each with its custom ring-derived icon; gold = active, muted gray = inactive. Inside the To-Do → Spending → Trading pager, a slim top TabRow whose indicator pill recolors per tab (coral / gold-neutral / indigo) as a preview of the adaptive tone.

**Home dashboard** — "Hey {name}" in the display face, settings gear top-right. Horizontal-scroll preview cards for the next few Urgent/Today to-dos. A stats strip of three compact cards: today's spend (mini sparkline), completion ring, weekly P&L (small up/down chip). A quote card with a soft gold hairline glow, italic body text, subtle time-of-day icon. A contribution-graph-style streak heatmap in gold gradient intensity — a missed day is a neutral dim dot, never red or an X.

**To-Do** — search bar with mic icon up top; the five buckets (Urgent / Today / In Loop / Weekly / Whenever) as a horizontal scrollable pill row, each with a small accent dot. List items are rounded cards with a ring-shaped checkbox that fills gold with a brief celebratory flourish on completion. Priority = small coral flag badge. Review-flag = small amber "!" (not alarm-red) that opens a calm bottom sheet: "This one needs a quick check."

**Spending** — day-grouped list, mono tabular amounts right-aligned, category icon+color chip on the left, account/payment-app as small secondary tags. Gold pill FAB, bottom-right. Summary view behind a segmented control (By Category / Account / App / Time) shown as simple proportional bar rows — deliberately not chart-heavy.

**Trading** — hero at top: large mono P&L figure in gain/loss color, small ring gauge for win-rate, a horizontal pill row of filters (date range / segment / product / broker). Trade list as dense ledger rows: symbol bold, qty/price/dates in small mono caption, net P&L colored and right-aligned. Broker preset editor as a settings-style list; a dismissible outlined (not filled/alarming) banner: "Verify these rates against your broker's current pricing."

**Settings** — grouped list-detail layout with caption headers, pill toggle switches with a gold thumb, four theme swatches as tappable circles, the Adaptive/Uniform segmented control, and AI usage shown as two mono stat numbers (today / all-time) with a small cost bar.

**Empty states** — every empty list gets a small ring-motif line illustration, one direct sentence, and a primary action — an invitation, not a dead end.

## Voice & microcopy
Active voice, plain verbs: "Add expense," not "Submit." A button's label matches its resulting confirmation ("Publish" → "Published"). Errors state exactly what happened, no apology: "Couldn't fetch link preview — check your connection." Streak/celebration copy stays understated and specific ("4-day streak") rather than effusive.

## Accessibility & polish
Gold on the dark base is for large text, icons, and the ring — verify contrast before using it for small body text. Tap targets ≥44dp. Visible focus states. Text scales with system font size. Reduced-motion variant for every animated element.

## Deliverable
High-fidelity portrait mockups (390×844 frame) for: Onboarding, Home, To-Do (incl. bucket filters + review-flag sheet), Spending (list + summary), Trading (dashboard + ledger + broker preset editor), Settings. Show the Trading screen in **both** Adaptive and Uniform tone so the toggle's effect is visible side by side.
