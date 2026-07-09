# Phase 3 UAT Fixes and Near Term Improvements (Claude Code Batch)

> Phase 3 UAT fixes and near term improvements for Claude Code. These are the clear, actionable items from BJ's on device testing. Each item is numbered to match BJ's UAT notes. Category C items (design discussions, new features) are NOT included here and will be handled separately.
> 

## Standing rules (apply to ALL work, not just this batch)

- **Token discipline:** every UI element must reference design tokens from the theme system (the derive(theme) output), NEVER hardcoded colors or hex values. This ensures dark mode and future themes work automatically. If you find yourself writing a color literal, stop and use the token.
- **No em dashes or hyphens in any user facing text.**

## Fix #9: post setup flow (two screens to one)

After the recommended setup completes, do NOT show two separate screens (setting up, then a you are all set screen the user must tap through). Instead, transition directly from the setup spinner to the main dashboard/board view with a non blocking toast or brief overlay confirming setup is complete and syncing with Notion. The user should land on a usable board, not tap through confirmation pages. Remove the intermediate you are all set screen entirely.

## Fix #10: back button after setup

After setup is complete, pressing the system back button must NOT return to the you are all set screen or any setup screen. It should follow default Android behavior (return to the home screen / close the app). The setup flow should be removed from the back stack once the board is reached.

## Fix #13: database picker showing wrong account's databases

**Bug:** the bring your own database picker shows databases from a previous session (emulator's Sift task boards) instead of the currently authenticated account's databases. A brand new database created in the live Notion account does not appear.

**Investigation direction:** the app is likely caching database references from a previous dev token or OAuth session and not refreshing them against the current live connection. Ensure the database picker ALWAYS queries live through the current OAuth session, never serves stale cached database lists from a prior connection. When the user re authenticates or switches accounts, any cached database list from the prior session must be cleared.

**Important Notion behavior (not a bug, but needs a UX solution):** Notion integrations only see pages and databases explicitly shared with them. A database created AFTER the OAuth grant, in a location not already shared, will be invisible to the integration. This is expected Notion behavior.

**The UX solution:** when the database picker shows results and the user does not see what they are looking for, show a helpful prompt: Not seeing your database? You may need to share it with Sift in Notion. Tap here to learn how. This should link to or display a brief step by step (open the database in Notion, tap the three dot menu, tap Connections, add the Sift connection). This reinforces the trust message: Sift only sees what you explicitly share with it, not your whole Notion.

Also support pasting a database URL directly (see #12 below) as an alternative path.

## Fix #8: jump to Notion from Sift

Add a way to navigate directly from Sift to the corresponding item in Notion. Two places:

- **From a task:** a small, unobtrusive icon or menu item that opens the Notion page for that task in the browser or Notion app. Every Notion page has a URL ([https://notion.so/[page-id]](https://notion.so/[page-id])); just open it.
- **From a bucket's settings:** a link that opens the underlying Notion database in the browser or Notion app.

Keep it minimal and tucked away (an icon, not a prominent button). Basic users will never need it; advanced users will find it.

## Fix #12: paste a database URL in the database picker

In the bring your own database flow (and when linking additional databases later), allow the user to paste a Notion database URL directly instead of only browsing the list. Parse the database ID from the URL and attempt to connect. If the database is not shared with the Sift integration, show the same not seeing it / share it with Sift prompt from fix #13. Also add a search/filter bar to the database list for users with many databases.

## Fix #5: bucket selection uses color and icon, subtle recolor on selection

In the add task view, the bucket selection pills must show each bucket's configured color and icon (matching the board). When the user selects a bucket, the add view picks up that bucket's color as a SUBTLE accent (pill highlight, borders, small accents), not a full background recolor. This is a visual cue that they have settled on the right bucket. Keep it subtle for legibility; this will be retooled during the full theming pass.

## Fix #3 (partial): optional time selection on a date

When the user sets a due date, allow them to OPTIONALLY add a time by tapping an icon or affordance (a clock icon, an add time chip, etc). Do NOT show a default time (do not assume 8:00 AM or any time). The date picker shows a date only; the time affordance is a separate, optional, additive step. If no time is set, the task has a date but no specific time (which matters for the notification system later: date only tasks behave differently from timed tasks).

## Near term: Notion email reminder notice

Notion automatically sends its own email reminders for tasks with due dates. Sift cannot disable these through the API. When Sift detects the user has a date property mapped, show a ONE TIME helpful notice (not a nag): Heads up: Notion may send you its own email reminders for tasks with due dates. If you would rather manage reminders through Sift, here is how to turn off Notion's emails. Link this to an external FAQ/support page (NOT inline instructions that go stale with app updates). The app should open a URL to the Sift support site's FAQ article for this topic. For now, use a placeholder URL constant (for example SUPPORT_URL_NOTION_NOTIFICATIONS) that BJ will fill in once the support site is live. Show the notice once, dismissible, and make it findable later in Sift's settings under a Help or Support section for reference.

**The support site itself** is a near term infrastructure need (not part of this Claude Code batch, but the app should be built to point to it). BJ will stand up a simple Astro/Vercel FAQ site. The first article: How to turn off Notion's email notifications so you only get reminders from Sift. Future articles will cover other Notion configuration topics, setup help, etc. The app should have a general Help or Support entry in settings that opens the support site root URL (another placeholder constant, for example SUPPORT_URL_ROOT).

## Near term: dark mode toggle (build NOW, architecture decision)

The token system must produce BOTH a light and dark rendering for each theme. Build this immediately for the Paper theme (the default):

- **Paper Light** (the current default) and **Paper Dark** (a new dark variant using the same Paper design language but with dark surfaces, light text, and adjusted token values for contrast and legibility).
- **A working three state toggle:** Light, Dark, Auto (follows OS setting via Android's isSystemInDarkTheme or equivalent). Persist the choice. Default to Auto.
- **The skeleton/template:** define the shape that every theme must provide (both a light and dark token set). The other four themes (Slate, Ink, Linen, Cyber) do NOT need their dark/light variants filled out right now, but the structure must enforce that they will need both when they are built out. A theme that only provides one mode should fall back gracefully (use its single mode regardless of the toggle) rather than crash.
- **Placement:** the toggle should be accessible in Settings. It does NOT need to be in onboarding yet (that is a Phase 6 concern), but it must be findable and functional.
- **Token discipline (critical):** this only works if EVERY UI element references tokens. Audit the current UI to confirm no hardcoded colors exist. If any are found, fix them as part of this work.

Design the Paper Dark variant thoughtfully: it is not just an inversion. Dark surfaces should use the design system's surface hierarchy (Surface A through E) with appropriate dark values. Text and accent colors should be adjusted for contrast on dark backgrounds. The urgency Priority colors and bucket accent colors should remain recognizable but may need lightness adjustments to read well on dark surfaces. Refer to the existing derive(theme) architecture and extend it with a mode parameter.