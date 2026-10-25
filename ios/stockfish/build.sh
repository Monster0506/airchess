#!/usr/bin/env bash
# Builds Stockfish (pinned in pins.env) as Stockfish.xcframework for iOS, and a
# macOS host build of the same sources for the smoke test. Runs on macOS only.
#
#   build.sh ios    iOS device + simulator static libraries -> build/Stockfish.xcframework
#   build.sh test   host library + smoke test, then runs the test
#   build.sh all    both (default)
#
# Downloads are cached in $AIRCHESS_SF_CACHE (default ~/.cache/airchess/stockfish).
set -euo pipefail

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=pins.env
. "$HERE/pins.env"

CACHE_DIR=${AIRCHESS_SF_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/airchess/stockfish}
BUILD_DIR=$HERE/build
SF_CHECKOUT=$CACHE_DIR/stockfish-$SF_COMMIT
WORK_SRC=$BUILD_DIR/work/src
JOBS=$(sysctl -n hw.ncpu)

# Mirrors the Makefile flags of the upstream build (minus LTO and -mdynamic-no-pic,
# which only suit executables) plus the AirChess switches.
COMMON_FLAGS="-std=c++17 -fno-exceptions -O3 -funroll-loops -DNDEBUG -DIS_64BIT \
-fconstexpr-steps=500000000 -Xclang -mllvm -Xclang -inline-threshold=500 -DSF_NO_UNIX_SHM"

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

sha256_of() { shasum -a 256 "$1" | cut -d' ' -f1; }

fetch_source() {
  if [ -d "$SF_CHECKOUT/.git" ] && [ "$(git -C "$SF_CHECKOUT" rev-parse HEAD)" = "$SF_COMMIT" ]; then
    log "Stockfish $SF_TAG source cached"
    return
  fi

  log "Fetching Stockfish $SF_TAG ($SF_COMMIT)"
  local tag_commit
  tag_commit=$(git ls-remote "$SF_REPO" "refs/tags/$SF_TAG" "refs/tags/$SF_TAG^{}" | tail -n 1 | cut -f1)
  [ "$tag_commit" = "$SF_COMMIT" ] || die "tag $SF_TAG points at '$tag_commit', pinned $SF_COMMIT"

  rm -rf "$SF_CHECKOUT"
  mkdir -p "$SF_CHECKOUT"
  git -C "$SF_CHECKOUT" init -q
  git -C "$SF_CHECKOUT" remote add origin "$SF_REPO"
  git -C "$SF_CHECKOUT" fetch -q --depth 1 origin "$SF_COMMIT"
  git -C "$SF_CHECKOUT" checkout -q FETCH_HEAD
  [ "$(git -C "$SF_CHECKOUT" rev-parse HEAD)" = "$SF_COMMIT" ] || die "fetched commit does not match pin"
}

fetch_net() {
  local net=$CACHE_DIR/$NET_NAME

  grep -q "\"$NET_NAME\"" "$SF_CHECKOUT/src/evaluate.h" || die "$NET_NAME is not the net named in evaluate.h"

  if [ -f "$net" ] && [ "$(sha256_of "$net")" = "$NET_SHA256" ]; then
    log "$NET_NAME cached"
    return
  fi

  mkdir -p "$CACHE_DIR"
  local url tmp=$net.part
  for url in $NET_URLS; do
    log "Downloading $url"
    if curl -fsSL --retry 3 --max-time 600 -o "$tmp" "$url" && [ "$(sha256_of "$tmp")" = "$NET_SHA256" ]; then
      mv "$tmp" "$net"
      return
    fi
    log "download or checksum failed"
  done
  rm -f "$tmp"
  die "could not obtain $NET_NAME with SHA-256 $NET_SHA256"
}

prepare_work() {
  local patch
  rm -rf "$BUILD_DIR/work"
  mkdir -p "$BUILD_DIR/work"
  cp -R "$SF_CHECKOUT/src" "$WORK_SRC"
  for patch in "$HERE"/patches/*.patch; do
    log "Applying $(basename "$patch")"
    patch -p1 -d "$BUILD_DIR/work" < "$patch"
  done
  ln -s "$CACHE_DIR/$NET_NAME" "$WORK_SRC/$NET_NAME"
}

# build_lib <name> <sdk> <clang target/arch flags...>
build_lib() {
  local name=$1 sdk=$2
  shift 2
  log "Compiling $name"
  rm -rf "$BUILD_DIR/$name"
  make -s -j "$JOBS" -f "$HERE/lib.mk" \
    CXX="xcrun --sdk $sdk clang++" \
    CXXFLAGS="$COMMON_FLAGS $*" \
    SRC_DIR="$WORK_SRC" BRIDGE_DIR="$HERE" \
    OBJ_DIR="$BUILD_DIR/$name/obj" LIB="$BUILD_DIR/$name/libstockfish.a"
}

build_ios() {
  # Device stays on baseline NEON: iOS 18 still runs on A12 (no dot product).
  # The simulator only runs on Apple silicon Macs, which all have it.
  build_lib ios-device iphoneos \
    -target "arm64-apple-ios$IOS_MIN_VERSION" -DUSE_NEON=8 -DUSE_POPCNT -DARCH=armv8
  build_lib ios-simulator iphonesimulator \
    -target "arm64-apple-ios$IOS_MIN_VERSION-simulator" -march=armv8.2-a+dotprod \
    -DUSE_NEON=8 -DUSE_NEON_DOTPROD -DUSE_POPCNT -DARCH=armv8-dotprod

  log "Creating Stockfish.xcframework"
  rm -rf "$BUILD_DIR/Stockfish.xcframework"
  xcodebuild -create-xcframework \
    -library "$BUILD_DIR/ios-device/libstockfish.a" -headers "$HERE/include" \
    -library "$BUILD_DIR/ios-simulator/libstockfish.a" -headers "$HERE/include" \
    -output "$BUILD_DIR/Stockfish.xcframework"
  cat "$BUILD_DIR/Stockfish.xcframework/Info.plist"
}

build_test() {
  local arch
  arch=$(uname -m)
  if [ "$arch" = arm64 ]; then
    build_lib host macosx -mmacosx-version-min=14.0 -march=armv8.2-a+dotprod \
      -DUSE_NEON=8 -DUSE_NEON_DOTPROD -DUSE_POPCNT -DARCH=armv8-dotprod
  else
    build_lib host macosx -mmacosx-version-min=14.0 -msse4.1 -mpopcnt \
      -DUSE_POPCNT -DUSE_SSE41 -DUSE_SSSE3 -DUSE_SSE2 -DARCH=x86-64-sse41-popcnt
  fi

  log "Building smoke test"
  xcrun --sdk macosx clang++ -std=c++17 -O1 -mmacosx-version-min=14.0 -I"$HERE/include" \
    "$HERE/test/smoke_test.cpp" "$BUILD_DIR/host/libstockfish.a" -o "$BUILD_DIR/host/smoke_test"

  log "Running smoke test"
  "$BUILD_DIR/host/smoke_test" 2>&1 | tee "$BUILD_DIR/smoke_test.log"
}

main() {
  [ "$(uname -s)" = Darwin ] || die "build.sh needs macOS and Xcode"
  local target=${1:-all}
  case "$target" in
    ios | test | all) ;;
    *) die "usage: build.sh [ios|test|all]" ;;
  esac

  mkdir -p "$BUILD_DIR"
  fetch_source
  fetch_net
  prepare_work

  [ "$target" = test ] || build_ios
  [ "$target" = ios ] || build_test
}

main "$@"
