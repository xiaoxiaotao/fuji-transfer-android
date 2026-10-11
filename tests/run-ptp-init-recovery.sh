#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test_binary=$(mktemp "${TMPDIR:-/tmp}/fuji-ptp-test.XXXXXX")
trap 'rm -f "$test_binary"' EXIT HUP INT TERM
case "$(uname -s)" in
  Darwin) link_option=-Wl,-dead_strip ;;
  *) link_option=-Wl,--gc-sections ;;
esac
"${CC:-cc}" -std=gnu11 -ffunction-sections -fdata-sections \
  -I lib/libpict/src -I lib tests/ptp_init_recovery_test.c \
  "$link_option" -o "$test_binary"
"$test_binary"
