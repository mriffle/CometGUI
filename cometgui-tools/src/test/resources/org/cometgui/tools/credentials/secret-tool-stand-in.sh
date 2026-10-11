#!/bin/bash
# A SCRIPTED STAND-IN for libsecret's secret-tool, for CometGUI's tests (phase 12,
# unit 5).  It is not secret-tool and talks to no keyring.  It behaves as
# secret-tool documents -- "store" reads the secret from standard input,
# "lookup" prints it on standard output (exit 1 and silence when there is none),
# "clear" removes it -- and it RECORDS its argument array (argv.record), its
# standard input (stdin.record) and the session-bus variable it was given
# (env.record) beside itself, so a test can prove where the secret travelled.
# Only bash builtins are used: the process service gives a tool no PATH.
here="${0%/*}"
for argument in "$@"; do printf '%s\n' "$argument" >> "$here/argv.record"; done
printf -- '--\n' >> "$here/argv.record"
printf '%s\n' "${DBUS_SESSION_BUS_ADDRESS-unset}" >> "$here/env.record"
if [ -e "$here/no-keyring" ]; then
    printf 'Cannot autolaunch D-Bus without X11 $DISPLAY\n' >&2
    exit 1
fi
command="$1"
shift
account=""
while [ $# -gt 0 ]; do
    if [ "$1" = "account" ]; then
        account="$2"
        shift 2
    else
        shift
    fi
done
mkdir_free="$here/keyring"
entry="$mkdir_free/${account//[^A-Za-z0-9]/_}"
case "$command" in
    store)
        secret=""
        IFS= read -r -d '' secret
        printf '%s' "$secret" >> "$here/stdin.record"
        printf -- '\n--\n' >> "$here/stdin.record"
        printf '%s' "$secret" > "$entry"
        exit 0
        ;;
    lookup)
        if [ -s "$entry" ]; then
            printf '%s' "$(< "$entry")"
            exit 0
        fi
        exit 1
        ;;
    clear)
        if [ -s "$entry" ]; then
            : > "$entry"
            exit 0
        fi
        exit 1
        ;;
    *)
        printf 'usage: secret-tool store|lookup|clear\n' >&2
        exit 2
        ;;
esac
