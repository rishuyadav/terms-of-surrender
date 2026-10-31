#!/usr/bin/env python3
"""Generate a private quota exemption hash using hidden local prompts."""

import getpass
import hashlib
import hmac
import ipaddress
import sys
import warnings


def main():
    if not sys.stdin.isatty():
        sys.exit("Run in an interactive terminal; inputs must remain hidden.")
    secret = getpass.getpass("AI_DEMO_PROXY_SECRET (hidden): ")
    if len(secret) < 32:
        sys.exit("The shared secret must contain at least 32 characters.")
    literal = getpass.getpass("Your current public IP (hidden): ").strip()
    try:
        if "%" in literal:
            raise ValueError()
        address = ipaddress.ip_address(literal)
    except ValueError:
        sys.exit("Enter a valid public IPv4 or IPv6 literal.")
    if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped:
        address = address.ipv4_mapped
    network = address.packed.hex()
    if isinstance(address, ipaddress.IPv6Address):
        network = address.packed[:8].hex() + "/64"
    digest = hmac.new(secret.encode("utf-8"), ("visitor\n" + network).encode("utf-8"), hashlib.sha256)
    print(digest.hexdigest())


if __name__ == "__main__":
    warnings.simplefilter("error", getpass.GetPassWarning)
    try:
        main()
    except getpass.GetPassWarning:
        sys.exit("Hidden input is unavailable; use a local interactive terminal.")
    except (EOFError, KeyboardInterrupt):
        sys.exit("\nCancelled.")
