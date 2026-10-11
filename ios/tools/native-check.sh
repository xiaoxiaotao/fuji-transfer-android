#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
task_build=$(mktemp -d)
trap 'rm -rf "$task_build"' EXIT
sources="ios/Native/FujiBridge.c lib/fuji.c lib/data.c lib/discovery.c lib/exif.c lib/fuji_usb.c lib/object.c lib/plat.c lib/fp/src/d185.c lib/fp/src/data.c lib/fp/src/xml.c lib/fp/src/ezxml.c"
for f in operations packet enums data enum_dump lib canon liveview ml conv generic transport ip bind canon_adv stuff no_usb; do
 sources="$sources lib/libpict/src/$f.c"
done
# Host build checks the exact native bridge and shared protocol linked into iOS.
clang -std=gnu11 -Werror=implicit-function-declaration -Ilib -Ilib/libpict/src -Ilib/fp/src -Iios/Native $sources ios/Tests/NativeSmoke.c -lpthread -lm -o "$task_build/native-smoke"
"$task_build/native-smoke"
