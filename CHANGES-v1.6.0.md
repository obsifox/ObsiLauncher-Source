# Changes in v1.6.0

## Play dashboard (main UI redesign)
- The home screen is now a console-style dashboard instead of a stack of cards:
  full-bleed wallpaper, a compact bottom info bar and one big PLAY button
  (bottom-right) with the version subtitle underneath it.
- Bottom info bar shows three tidy chips, like a console home:
  - grass-block icon + the selected version (tap → Versions),
  - clock icon + total playtime for the instance,
  - gamepad icon + when the instance was last played.
- New playtime tracking: every game session is recorded per instance
  (total seconds + last-played timestamp, stored in instances.json).

## Top navigation bar
- The bottom tab bar is gone. Navigation moved to a floating top bar:
  avatar + player name (tap → Accounts) on the left, then the tabs
  Play · Version · Mods · Settings, and a small star (About) on the right.
- The selected tab is highlighted with an accent underline; the top bar fades
  into the wallpaper with a soft gradient scrim.
- Accounts are reached through the avatar (like the reference design);
  About stays one tap away at the top-right star.

## Compact, tidier controls everywhere
- Glass cards: smaller corner radius and tighter padding (18/16 → 14/11 dp).
- Buttons: smaller height/padding and label-size text so lists and dialogs fit
  more content without scrolling.
- Section titles are smaller and closer to their content.
- Instances on Home are now small rounded pills in one horizontal row
  (tap to switch, plus a small "Manage" pill for the selected instance).
- Transient states (launching/running, missing account/runtime) appear as a
  single small status chip with an inline action instead of full-width cards.

## Housekeeping
- versionCode 10600 / versionName 1.6.0.
- Full EN + FA strings for every new element (playtime, last-played, tabs).
