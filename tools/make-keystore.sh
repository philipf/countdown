#!/usr/bin/env bash
# Generate the release keystore outside the repo and keep its password in pass.
#
# Safe to re-run: it does only the steps that are still missing, and when the
# keystore is there but the password is not it asks for the password rather than
# giving up.
set -euo pipefail

keystore_dir="$HOME/.android-keystores"
keystore="$keystore_dir/countdown-release.jks"
pass_entry="countdown/release-keystore/password"
key_alias="countdown"
dname="CN=Countdown, OU=Personal, O=notnot.ninja, C=ZA"

die() {
  printf '%s\n' "$@" >&2
  exit 1
}

command -v pass >/dev/null ||
  die "pass is not installed. The keystore password is kept in pass, so install it first."
command -v keytool >/dev/null ||
  die "keytool is not on PATH. Run this through mise: mise run keystore"

store_dir="${PASSWORD_STORE_DIR:-$HOME/.password-store}"
[[ -f "$store_dir/.gpg-id" ]] ||
  die "No password store at $store_dir." \
    "Create one with: pass init <your-gpg-key-id>"

require_terminal() {
  [[ -t 0 ]] || die "This needs a terminal: the password is typed, never generated."
}

password_is_stored() {
  pass show "$pass_entry" >/dev/null 2>&1
}

# Only the first line. The password stays in a shell variable and is handed to
# keytool through the environment, never on a command line where ps could read it.
stored_password() {
  local secret
  secret="$(pass show "$pass_entry")"
  printf '%s' "${secret%%$'\n'*}"
}

# pass prompts without echo and asks for the password twice.
store_password() {
  echo "The password is typed twice and never shown. It is stored at $pass_entry."
  pass insert "$pass_entry"
}

keystore_opens_with() {
  COUNTDOWN_KEYSTORE_PASSWORD="$1" keytool -list \
    -keystore "$keystore" \
    -storetype PKCS12 \
    -storepass:env COUNTDOWN_KEYSTORE_PASSWORD \
    >/dev/null 2>&1
}

generate_keystore() {
  mkdir -p "$keystore_dir"
  chmod 700 "$keystore_dir"
  COUNTDOWN_KEYSTORE_PASSWORD="$1" keytool -genkeypair \
    -keystore "$keystore" \
    -storetype PKCS12 \
    -storepass:env COUNTDOWN_KEYSTORE_PASSWORD \
    -keypass:env COUNTDOWN_KEYSTORE_PASSWORD \
    -alias "$key_alias" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10950 \
    -dname "$dname"
  chmod 600 "$keystore"
  echo "Generated $keystore"
}

have_keystore=false
[[ -f "$keystore" ]] && have_keystore=true
have_password=false
password_is_stored && have_password=true

if $have_keystore && $have_password; then
  keystore_opens_with "$(stored_password)" ||
    die "The password at $pass_entry does not open $keystore." \
      "Correct the entry with: pass edit $pass_entry" \
      "Or move the keystore aside and re-run this to make a new key."
  echo "$keystore exists and $pass_entry opens it. Nothing to do."
  exit 0
fi

if $have_keystore; then
  echo "$keystore exists but its password is not in pass."
  echo "Type it now to store it. If it is lost, press Ctrl-C, move the keystore"
  echo "aside and re-run this: the key can be replaced as long as nothing signed"
  echo "with it has been shared."
  require_terminal
  store_password
  if ! keystore_opens_with "$(stored_password)"; then
    pass rm --force "$pass_entry" >/dev/null
    die "That password does not open $keystore. Nothing was stored."
  fi
  echo "Stored. $keystore is usable again."
  exit 0
fi

if $have_password; then
  echo "$pass_entry is already in pass. Generating the keystore with it."
  generate_keystore "$(stored_password)"
  exit 0
fi

require_terminal
store_password
generate_keystore "$(stored_password)"
