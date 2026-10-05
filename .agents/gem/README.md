# Gemini web app (Gem) version

Same assistant as the Antigravity setup, but for gemini.google.com. A Gem can't see your
project, so you paste code and logs into the chat. It also can't be technically blocked from
writing code. Only the instructions keep it in line, so the Antigravity version is stronger.

## Setup (once)

1. Open Gemini → **Gems** → **New Gem**.
2. Name: `GoofyAddons Debug Buddy` (or anything).
3. **Instructions:** paste the whole content of [`gem-instructions.md`](gem-instructions.md).
4. **Knowledge:** upload these 9 files (Gems allow up to 10). Rename the four `SKILL.md`
   files when uploading so they're distinguishable:

   | Upload this file | As |
   | --- | --- |
   | `GEMINI.md` (project root) | `GEMINI.md` |
   | `.agents/skills/bug-hunt/SKILL.md` | `bug-hunt.md` |
   | `.agents/skills/teach/SKILL.md` | `teach.md` |
   | `.agents/skills/simulate/SKILL.md` | `simulate.md` |
   | `.agents/skills/debug-lines/SKILL.md` | `debug-lines.md` |
   | `.agents/references/codebase-index.md` | same |
   | `.agents/references/server-context.md` | same |
   | `.agents/references/comment-style.md` | same |
   | `.agents/references/dop-style.md` | same |

   Use the 10th slot for `.agents/memory/INDEX.md` (what the assistant learned from you).
   Don't use it for source code: an uploaded file goes out of date as soon as you change
   it. Paste code into the chat instead.
5. Save.

## Memory in the Gem

The Gem can't write files or run scripts. At the end of a session say `personalize`. It
will print the proposed memory entries as text. Save the ones you want with Antigravity's
`/personalize` (or paste them into `.agents/memory/entries/` and run `memory.py index`),
then re-upload `INDEX.md`. If the Gem needs a full entry, it will ask you to paste it.

## Keeping it up to date

The Gem keeps its **own copy** of these files. When you change a file here (or the AI
updates `codebase-index.md`), re-upload that file to the Gem.

## Using it

- Say what you want in your own words. It will restate it and ask what to paste.
- Paste the crash report / the `State switched` lines / the method you're asking about.
- Start a message with a mode name if you want to force it: `bug-hunt: ...`, `teach: ...`,
  `simulate: ...`, `debug-lines: ...`.
