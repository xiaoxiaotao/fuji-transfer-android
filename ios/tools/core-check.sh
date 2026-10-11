#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
task_build=$(mktemp -d)
trap 'rm -rf "$task_build"' EXIT
swiftc ios/Core/Photo.swift ios/tools/CoreChecks.swift -o "$task_build/core-check"
"$task_build/core-check"
