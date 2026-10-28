#!/usr/bin/env python3
"""Hilfs-Logger fuer Skill-Invocations im Run-Log.

Usage:
  python3 .agent/tools/skill_log.py <skill> <agent> <step> [start|end]

Schreibt eine JSON-Zeile nach .agent/artifacts/run-log.jsonl.
"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

def main():
    if len(sys.argv) < 4:
        print("Usage: skill_log.py <skill> <agent> <step> [start|end]", file=sys.stderr)
        sys.exit(2)
    skill = sys.argv[1]
    agent = sys.argv[2]
    try:
        step = int(sys.argv[3])
    except ValueError:
        step = sys.argv[3]
    phase = sys.argv[4] if len(sys.argv) > 4 else "start"
    event = "skill_start" if phase in ("start", "begin") else "skill_end"
    entry = {
        "ts": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
        "event": event,
        "skill": skill,
        "agent": agent,
        "step": step,
    }
    log = Path(".agent/artifacts/run-log.jsonl")
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    print(json.dumps(entry, ensure_ascii=False))

if __name__ == "__main__":
    main()
