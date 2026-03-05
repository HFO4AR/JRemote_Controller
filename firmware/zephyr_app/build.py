#!/usr/bin/env python3
"""
JRemote Controller - Build Script
Build ESP32-S3 firmware using Zephyr
"""

import os
import sys
import subprocess
import argparse

# Configuration
ZEPHYR_APP_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(ZEPHYR_APP_DIR)
VENV_ACTIVATE = os.path.join(ZEPHYR_APP_DIR, ".venv", "bin", "activate")
BOARD = "esp32s3_devkitm/esp32s3/procpu"
APP_DIR = os.path.join(ZEPHYR_APP_DIR, "app")
BUILD_DIR = os.path.join(PROJECT_ROOT, "build")


def run_command(cmd, cwd=None, activate_venv=True):
    """Run a shell command"""
    if activate_venv and os.path.exists(VENV_ACTIVATE):
        cmd = f"source {VENV_ACTIVATE} && {cmd}"

    result = subprocess.run(
        cmd,
        shell=True,
        cwd=cwd or PROJECT_ROOT,
        executable="/bin/bash"
    )
    return result.returncode == 0


def clean():
    """Clean build directory"""
    print("Cleaning build directory...")
    if os.path.exists(BUILD_DIR):
        import shutil
        shutil.rmtree(BUILD_DIR)
    print("Clean complete.")


def build(clean_build=False):
    """Build firmware"""
    if clean_build:
        clean()

    print(f"Building for board: {BOARD}")
    cmd = f"west build -b {BOARD} {APP_DIR}"
    return run_command(cmd)


def main():
    parser = argparse.ArgumentParser(description="Build JRemote Controller firmware")
    parser.add_argument("--clean", action="store_true", help="Clean before building")
    parser.add_argument("--no-clean", action="store_true", help="Don't clean before building")

    args = parser.parse_args()

    # Change to project root
    os.chdir(PROJECT_ROOT)

    success = build(clean_build=args.clean and not args.no_clean)

    if success:
        print("\n=== Build Successful ===")
        print(f"Firmware: {BUILD_DIR}/zephyr/zephyr.bin")
    else:
        print("\n=== Build Failed ===")
        sys.exit(1)


if __name__ == "__main__":
    main()
