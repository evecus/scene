#!/usr/bin/env bash
# 交叉编译 scene-rs (aarch64-linux-android) 并拷入 app assets
# 前提: rustup target add aarch64-linux-android; ANDROID_NDK_HOME 指向 NDK
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_NDK_HOME:?请设置 ANDROID_NDK_HOME，例如 /opt/ndk/android-ndk-r27c}"
export PATH="$HOME/.cargo/bin:$PATH"

TARGET=aarch64-linux-android
API=29
CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android${API}-clang"
AR="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$CC"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_AR="$AR"

cargo build --release --target "$TARGET"

DEST=../../app/src/main/assets/native
mkdir -p "$DEST"
cp "target/$TARGET/release/scene-rs" "$DEST/scene-rs"
echo "OK -> app/src/main/assets/native/scene-rs ($(du -h $DEST/scene-rs | cut -f1))"
