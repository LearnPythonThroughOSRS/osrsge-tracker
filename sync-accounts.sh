#!/usr/bin/env bash
# Account rotation helper: log each Jagex account through the launcher,
# then play it in the dev client so every character syncs to osrsge.io.
#
# Usage: ./sync-accounts.sh
set -u

CREDS="$HOME/.runelite/credentials.properties"
LAUNCHER="/Applications/Jagex Launcher.app"

echo "=== osrsge account rotation ==="
echo "For each account: the Jagex Launcher opens, you log in and press Play"
echo "(official RuneLite starts and captures the session), then CLOSE it."
echo "This script detects the captured session and starts the plugin client."
echo

while true; do
  before=$(stat -f %m "$CREDS" 2>/dev/null || echo 0)

  echo "-> Opening Jagex Launcher. Log into the NEXT account, press Play,"
  echo "   wait for RuneLite to reach the login screen, then close RuneLite."
  open "$LAUNCHER"

  echo -n "   Waiting for a fresh session capture"
  while true; do
    now=$(stat -f %m "$CREDS" 2>/dev/null || echo 0)
    if [ "$now" != "$before" ] && [ "$now" != 0 ]; then
      break
    fi
    echo -n "."
    sleep 2
  done
  echo " captured."

  echo "-> Close the official RuneLite window if it is still open,"
  read -r -p "   then press Enter to start the plugin client... "

  echo "-> Starting plugin client. Play on this account; close the window when done."
  ./gradlew runClient --console=plain

  echo
  read -r -p "Rotate another account? [y/N] " again
  case "$again" in
    [Yy]*) continue ;;
    *) break ;;
  esac
done

echo "Done. Every account you played is now on your osrsge.io dashboard."
