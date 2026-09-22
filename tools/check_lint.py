#!/usr/bin/env python3
"""Reject lint errors and increases against reviewed per-category warning counts."""
import json
from collections import Counter
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
budget = json.loads((root / "quality/lint-warning-budget.json").read_text())
report = ET.parse(root / "app/build/reports/lint-results-debug.xml")
counts = Counter()
errors = []
for issue in report.findall("issue"):
    severity = issue.get("severity")
    category = issue.get("id")
    if severity in ("Error", "Fatal"):
        errors.append(category)
    elif severity == "Warning":
        counts[category] += 1
for category, count in sorted(counts.items()):
    if count > budget.get(category, 0):
        errors.append(f"{category}: {count} > {budget.get(category, 0)}")
if errors:
    print("Lint gate failed:\n" + "\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"Lint gate passed: no errors; {sum(counts.values())} warnings within budget")
