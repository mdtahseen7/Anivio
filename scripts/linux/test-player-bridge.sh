#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
test_jdk="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
c++ -std=c++17 -O2 -Wno-deprecated-declarations \
    -I"$test_jdk/include" -I"$test_jdk/include/linux" \
    $(pkg-config --cflags mpv webkit2gtk-4.1 gtk+-3.0 x11 xcomposite xext) \
    "$repo_root/composeApp/src/desktopTest/native/linux/player_bridge_test.cpp" \
    -o "$test_dir/player-bridge-test" \
    $(pkg-config --libs mpv webkit2gtk-4.1 gtk+-3.0 x11 xcomposite xext) -lpthread
"$test_dir/player-bridge-test"
