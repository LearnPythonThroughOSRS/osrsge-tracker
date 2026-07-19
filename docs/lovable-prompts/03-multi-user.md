# Lovable Prompt 3 — Multi-user accounts with personal API keys

Paste everything below the line into Lovable chat. Prerequis: prompts 1
and 2 already deployed (sync backend + dashboard).

---

Upgrade the site from single-user to multi-user. Anyone can sign up,
generate their own API key, and see only their own Grand Exchange data
on /dashboard.

## Auth

Enable public signup (email + password) with Lovable Cloud auth. The
/dashboard page keeps requiring login. Re-enable/show the signup link
that was hidden earlier.

## Database changes

1. New table **api_keys**:
   - id: uuid primary key default gen_random_uuid()
   - user_id: uuid not null references auth.users, UNIQUE (one key per user)
   - key_hash: text not null        (SHA-256 hex of the full key)
   - key_prefix: text not null      (first 8 characters, for display)
   - created_at: timestamptz default now()
   - last_used_at: timestamptz

2. Add `user_id uuid` to **trades** and **active_offers**.

3. Migration of existing data: set user_id on ALL existing rows in
   trades and active_offers to the user account with my email (the only
   account that exists today). Then make user_id NOT NULL on both tables.

4. Replace the trades unique constraint with UNIQUE
   (user_id, player_name, item_id, buy_timestamp, sell_timestamp).

5. Row Level Security: logged-in users can SELECT only rows where
   user_id = auth.uid(), on trades and active_offers. No INSERT/UPDATE/
   DELETE for clients — writes happen only in edge functions via the
   service role. api_keys: users can SELECT only their own row (and only
   the key_prefix / dates — never key_hash if column-level control is
   possible; otherwise selecting the hash is acceptable since it cannot
   be reversed).

## Key generation

New edge function **generate-api-key**, callable only by a logged-in
user (verify the Supabase session JWT):
- Generate a 48-character random hex key.
- SHA-256 hash it; upsert into api_keys for this user (replacing any
  existing row — regeneration invalidates the old key immediately).
- Return {"ok":true,"apiKey":"<full key>","keyPrefix":"<first 8>"} —
  this is the ONLY time the full key is ever returned.

## Update sync functions

Modify **trades-sync** and **offers-sync**:
- Take the bearer token from the Authorization header, SHA-256 it, and
  look it up in api_keys by key_hash.
- Unknown hash → 401 {"ok":false,"error":"unauthorized"}.
- Known → set last_used_at = now() and write all rows with that user's
  user_id (trades upsert now uses the new user-scoped unique constraint;
  offers replace-all now deletes/inserts only rows matching BOTH user_id
  and player_name).
- The old PLUGIN_API_KEY secret is no longer used; remove that check.

## Dashboard additions

Add a **Settings card** on /dashboard:
- If the user has no API key: explain they need one for the RuneLite
  plugin, with a "Generate API key" button.
- If they have one: show key_prefix + "…", created date, last used date,
  and a "Regenerate" button with a confirmation warning that the old key
  stops working immediately.
- After generate/regenerate: show the full key once in a copy-to-
  clipboard box with the message "Save this now — it won't be shown
  again", plus 3 short setup steps: install the OSRS GE Tracker RuneLite
  plugin, paste this key in its API Key setting, set Sync URL to
  https://jhzavbdwaagowigihbjx.supabase.co/functions/v1 and enable sync.

Everything else on the dashboard stays the same — RLS now scopes all
data to the logged-in user automatically.

## When done

Tell me it's deployed. I will generate my own key from the Settings
card and re-run my smoke tests with it.
