#!/usr/bin/env python3
# GoofyAddons AI guard — Antigravity PreToolUse hook.
#
# Why this exists: the rules in GEMINI.md tell the AI not to write code without permission,
# but an AI can ignore rules. This script runs BEFORE every file write or terminal command
# and makes Antigravity show a permission prompt, no matter what the AI decided.
#
# Input:  JSON on stdin from Antigravity, e.g. {"toolCall": {"name": "write_to_file", "args": {"TargetFile": "..."}}, ...}
# Output: JSON on stdout, e.g. {"decision": "force_ask", "reason": "..."}
#   "force_ask" = always show a prompt (even if "always allow" was clicked before)
#   "allow"     = run without a prompt (only used for plain read-only commands)
# If anything goes wrong in here, we fall back to "force_ask" (fail safe, never fail open).

import json
import os
import re
import sys

WRITE_TOOLS = {"write_to_file", "replace_file_content", "multi_replace_file_content"}

# Programs that only read. A command is auto-allowed only if EVERY piped part starts with one of these.
READ_ONLY_PROGRAMS = {"grep", "rg", "cat", "head", "tail", "ls", "find", "zcat", "wc", "sort",
                      "uniq", "cut", "less", "file", "stat", "pwd", "tree", "unzip", "git", "echo"}
# git is only read-only with these subcommands.
READ_ONLY_GIT = {"log", "show", "diff", "status", "blame", "grep", "rev-parse", "ls-files", "branch"}
# Anything that can write, chain or hide another command -> always ask.
# Also catches "read" programs that can write a file: sort -o, git diff --output, find -fls/-fprint.
RISKY = re.compile(r"[>;&`]|\$\(|\btee\b|\s-delete\b|\s-exec\b|\s-execdir\b|\s-fprint|\s-fls\b|\s-ok\b|\s--output|\s-o\b")


def zone_of(path, workspace):
    """Turns an absolute path into (relative path, human-readable zone)."""
    rel = os.path.relpath(path, workspace) if workspace and os.path.isabs(path) else path
    rel = rel.replace("\\", "/")
    if rel.startswith("src/"):
        return rel, "SOURCE CODE — only allowed for debug lines marked // DEBUG(ai), and only if you asked"
    if rel.startswith("tests/"):
        return rel, "TEST FILE — simulation harness, only if you asked"
    if rel.startswith(".agents/memory/"):
        return rel, "AI MEMORY — something the AI learned from you (only during /personalize)"
    if rel.startswith(".agents/") or rel in ("GEMINI.md", "AGENTS.md"):
        return rel, "AI CONFIG — changes how the assistant behaves"
    if rel.startswith(".."):
        return rel, "OUTSIDE THE PROJECT"
    return rel, "OTHER PROJECT FILE (build/config/etc.)"


# Exact commands that only read, even though they run a script.
READ_ONLY_EXACT = {
    "python3 .agents/skills/personalize/scripts/memory.py check",
    "python3 .agents/skills/personalize/scripts/memory.py next-id",
}


def is_read_only(command):
    """True only for simple read commands like `grep ... | tail -20`."""
    if " ".join(command.split()) in READ_ONLY_EXACT:
        return True
    if RISKY.search(command):
        return False
    for part in command.split("|"):
        words = part.strip().split()
        if not words or words[0] not in READ_ONLY_PROGRAMS:
            return False
        if words[0] == "git" and (len(words) < 2 or words[1] not in READ_ONLY_GIT):
            return False
        # unzip without -l (list) or -p (print to screen) would extract files.
        if words[0] == "unzip" and not ({"-l", "-p"} & set(words)):
            return False
        if words[0] == "git" and words[1] == "branch" and len(words) > 2:
            return False  # "git branch x" creates a branch
        # "uniq in.txt out.txt" writes out.txt, so allow at most one file argument.
        if words[0] == "uniq" and len([w for w in words[1:] if not w.startswith("-")]) > 1:
            return False
    return True


def decide(payload):
    tool = payload.get("toolCall", {})
    name = tool.get("name", "")
    args = tool.get("args", {}) or {}
    workspace = (payload.get("workspacePaths") or [""])[0]

    if name in WRITE_TOOLS:
        rel, zone = zone_of(args.get("TargetFile", "?"), workspace)
        return {"decision": "force_ask",
                "reason": f"GoofyAddons guard: the AI wants to WRITE {rel}\nZone: {zone}\n"
                          f"Approve only if you asked for this in your last message."}

    if name == "run_command":
        command = args.get("CommandLine", "")
        if is_read_only(command):
            return {"decision": "allow", "reason": "GoofyAddons guard: read-only command"}
        return {"decision": "force_ask",
                "reason": f"GoofyAddons guard: this command may change files or run code:\n{command}\n"
                          f"Approve only if you asked for it."}

    # Any other tool routed here by mistake: ask, never silently allow.
    return {"decision": "force_ask", "reason": f"GoofyAddons guard: unknown tool '{name}'"}


def main():
    try:
        result = decide(json.load(sys.stdin))
    except Exception as e:  # broken input or a bug in this script -> still ask the user
        result = {"decision": "force_ask", "reason": f"GoofyAddons guard error ({e}); asking to be safe"}
    print(json.dumps(result))


if __name__ == "__main__":
    main()
