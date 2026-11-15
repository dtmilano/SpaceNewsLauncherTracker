# Build: Space Launches & News tracker

Build a small, visually appealing **native Android app** (Kotlin + Jetpack Compose,
Material 3) that tracks upcoming space launches and recent space news.

## Stack & structure
- **Bottom tab navigation** with three destinations: Launches, News, Settings.
- The app runs **immersive fullscreen** — hide the status and navigation bars (revealed
  transiently on swipe) and draw edge to edge. Re-hide them whenever the window regains
  focus.
- Pick a **space-themed colour scheme** and apply it to the whole app: a dark, near-black
  navy base with a bright accent for interactive elements and live countdowns, a second
  accent for model-generated content, and a warm accent for transient states (a passed
  liftoff, a rate limit). Force the dark scheme and disable Material You dynamic colour, so
  those accents keep their meaning. Keep the XML `windowBackground` in sync with the Compose
  background to avoid a white flash on cold start.
- Cache all API responses (respect free-tier rate limits) and handle network/HTTP errors
  gracefully with a clear message instead of a stack trace. On HTTP 429, tell the user the
  rate limit was hit and to retry shortly.

## Data sources (recommend, then use)
First recommend free, public APIs that need **no API key** (or only a free, optional one),
then use them:
1. **Upcoming launches** — rocket names, launch date/time, mission details, pad location,
   and imagery. (Launch Library 2 by The Space Devs is a good fit.)
2. **Space news** — recent headlines about launches, satellites, etc. (Spaceflight News
   API / SNAPI is a good fit.)

Briefly justify each choice and note any rate limits.

Both APIs' wire formats have drifted between versions (LL2 in particular returns pad
coordinates as strings on some records and numbers on others, and moved the launch image
from a bare string to an object). Decode the volatile fields loosely so an upstream change
degrades one field instead of failing the whole response.

## Features

**Settings**
- Sliders for how many launches and how many news articles to show.
- A "Live countdown" toggle that auto-refreshes so timers tick (reuse cached API data — do
  not re-hit the APIs on each tick).
- A "Refresh now" button that clears the caches.
- An **Amazon Bedrock** section holding the credentials for the explain feature below:
  region, model ID, access key ID, secret access key, and an optional session token. Mask
  the secrets with a show/hide toggle, hold edits in a local draft and commit them on Save,
  persist them, and show at a glance whether the configuration is complete or which fields
  are still missing. Pre-fill the defaults at build time from `local.properties` (keys
  prefixed `bedrock.`), falling back to the ambient `AWS_*` environment variables, so a
  developer who already has credentials in their shell gets a working build with nothing to
  type.
- A short note naming the two data sources and the caching policy.

**Tab 1 — Upcoming Launches** (one card per launch)
- Two columns: rocket/launch image on the left, mission details on the right. Show a
  rocket-emoji placeholder when no image is available — and also when the image fails to
  load.
- A **countdown timer** under the mission name using `java.time`, formatted like a launch
  clock (T-minus days : hours : minutes : seconds); handle "to be announced" and
  already-passed launches (count up as `T+` rather than going negative). The clock must
  **never wrap to a second line** — it sits in a narrow column, so use a single compact
  non-wrapping line such as `T-02d:18h:41m:05s`, with bold monospace digits and smaller,
  dimmer unit letters.
- Key facts: human-readable launch date (UTC), provider, status, rocket, and pad.
- If the launch pad has coordinates, show the coordinates plus an **"Open pad in Google
  Maps"** button that fires an Android `ACTION_VIEW` intent at a `geo:` URI targeting the
  Google Maps package, falling back to any other map app and then to the browser so the
  button never dead-ends. Declare the matching `<queries>` entries in the manifest.
- An **"Explain in plain English"** button per mission that uses **Amazon Bedrock** to
  rewrite a technical mission description into 2-3 plain, jargon-free sentences for a
  general audience (accurate, no invented details). Use the **AWS SDK for Kotlin**
  `BedrockRuntimeClient.invokeModel` with the Anthropic messages payload — the direct
  Android counterpart of boto3's `bedrock-runtime` client — signed with static credentials
  from Settings. Cache each summary so a mission costs at most one model call.
  **If the credentials in Settings are empty, keep the button enabled and have it show an
  error naming the fields that are needed**, rather than disabling the control or crashing.
  Map expired credentials, access denied, throttling, and validation errors to their own
  plain-language messages too, and offer a "Try again" action on failure.

**Tab 2 — Space News**
- A scannable list of recent articles: thumbnail, title, source, published date, summary,
  and a tap-through to the full article in the browser.

## Deliverables
Show the recommended APIs with brief justification, then build the app, install it on the
running emulator, and screenshot all three tabs to confirm it works against live data.
