# Multi-user API keys — Design

Date: 2026-07-19
Status: Approved

## Goal

Every osrsge.io user gets their own account and personal API key. Their
plugin syncs under that key; their dashboard shows only their data.
Plugin code is unchanged — the key pasted into config now identifies the
user instead of being one shared secret.

## Server changes (Lovable Prompt #3, docs/lovable-prompts/03-multi-user.md)

- Public signup enabled (email + password via Lovable Cloud auth).
- New table `api_keys`: id, user_id (auth.users), key_hash (SHA-256 of
  the full key), key_prefix (first 8 chars for display), created_at,
  last_used_at. One active key per user; regenerating replaces it.
- `trades` and `active_offers` gain `user_id uuid not null`.
- Trades uniqueness becomes (user_id, player_name, item_id,
  buy_timestamp, sell_timestamp).
- Edge functions `trades-sync` / `offers-sync`: SHA-256 the incoming
  bearer token, look up in api_keys → user_id; 401 if unknown; write
  rows with that user_id; bump last_used_at. The old PLUGIN_API_KEY
  shared secret is retired.
- New edge function or RPC `generate-api-key` (session-authenticated):
  creates a random 48-hex-char key, stores hash+prefix, returns the
  full key exactly once.
- RLS: authenticated users can select only their own rows
  (user_id = auth.uid()); no anon access; writes only via service role.
- Dashboard: unchanged queries + RLS scoping, plus a Settings card:
  shows key prefix + created/last-used dates, "Generate API key" button
  (shows full key once with copy button; warns regenerating invalidates
  the old key), and short setup instructions for the RuneLite plugin.
- Migration: backfill existing trades/active_offers rows to the owner's
  user account (identified by their login email at migration time).

## Plugin changes

None.

## Verification

- Smoke tests rerun with a personally generated key (same script,
  new key) — bad key still 401s.
- Owner regenerates key, updates plugin config, sees "Synced" and new
  flips arriving under their account.
- Second (test) account created → generates its own key → its data is
  invisible to the owner account and vice versa.

## Out of scope

- Email verification flows beyond Lovable defaults, password reset
  customization, rate limiting per user, key expiry.
