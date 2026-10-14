#!/usr/bin/env python3
"""Select the existing opt-in database suites and reject vacuous CI results."""
import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

GATES = {"WARDEN_REVIEW_DB_TEST", "WARDEN_REPAIR_TX_DB_TEST"}
ANNOTATION = re.compile(r'@EnabledIfEnvironmentVariable\s*\(\s*named\s*=\s*"([^"]+)"\s*,\s*matches\s*=\s*"true"\s*\)')

def suites(source):
    selected = []
    for path in sorted(source.rglob("*.java")):
        text = path.read_text()
        if not GATES.intersection(ANNOTATION.findall(text)):
            continue
        package = re.search(r'^package\s+([\w.]+)\s*;', text, re.M)
        if not package or not re.search(r'\bclass\s+' + re.escape(path.stem) + r'\b', text):
            raise ValueError(f"Cannot establish suite identity: {path}")
        selected.append(f"{package.group(1)}.{path.stem}")
    if not selected:
        raise ValueError("No persisted Warden test suites found")
    return selected

def verify(source, reports):
    selected = suites(source)
    total = 0
    for suite in selected:
        report = reports / f"TEST-{suite}.xml"
        root = ET.parse(report).getroot()
        if root.tag != "testsuite" or root.get("name") != suite:
            raise ValueError(f"Wrong suite identity: {report}")
        tests = int(root.attrib["tests"])
        skipped = int(root.attrib["skipped"])
        failures = int(root.attrib["failures"])
        errors = int(root.attrib["errors"])
        cases = root.findall("testcase")
        if tests <= 0 or skipped or failures or errors or len(cases) != tests:
            raise ValueError(f"Incomplete or failing suite: {suite} (tests={tests}, skipped={skipped}, failures={failures}, errors={errors})")
        if any(case.find(tag) is not None for case in cases for tag in ("skipped", "failure", "error")):
            raise ValueError(f"Non-passing test case: {suite}")
        total += tests
    return len(selected), total

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("list", "verify"))
    parser.add_argument("--source", type=Path, default=Path("backend/src/test/java"))
    parser.add_argument("--reports", type=Path, default=Path("backend/build/test-results/test"))
    args = parser.parse_args()
    try:
        if args.command == "list":
            print("\n".join(suites(args.source)))
        else:
            count, total = verify(args.source, args.reports)
            print(f"Verified {total} executed tests across {count} persisted Warden suites; zero skips, failures or errors.")
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
