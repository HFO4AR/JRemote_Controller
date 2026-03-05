#!/usr/bin/env python3
"""
JRemote Controller - Flash Script
Flash ESP32-S3 firmware using Zephyr
"""

import os
import sys
import subprocess
import argparse

# Configuration
ZEPHYR_APP_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(ZEPHYR_APP_DIR)
VENV_ACTIVATE = os.path.join(ZEPHYR_APP_DIR, ".venv", "bin", "activate")
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


def flash():
    """Flash firmware to device"""
    print("Flashing firmware...")

    # Check if firmware exists
    firmware_path = os.path.join(BUILD_DIR, "zephyr", "zephyr.bin")
    if not os.path.exists(firmware_path):
        print(f"Error: Firmware not found at {firmware_path}")
        print("Please run build.py first")
        return False

    cmd = "west flash"
    return run_command(cmd)


def erase():
    """Flash erase"""
    print("Erasing flash...")
    cmd = "west flash --erase"
    return run_command(cmd)


def main():
    parser = argparse.ArgumentParser(description="Flash JRemote Controller firmware")
    parser.add_argument("--erase", action="store_true", help="Erase flash before flashing")

    args = parser.parse_args()

    # Change to project root
    os.chdir(PROJECT_ROOT)

    if args.erase:
        success = erase()
    else:
        success = flash()

    if success:
        print("\n=== Flash Successful ===")
    else:
        print("\n=== Flash Failed ===")
        sys.exit(1)


if __name__ == "__main__":
    main()
