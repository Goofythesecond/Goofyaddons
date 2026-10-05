# tests/ — static simulations

Small standalone Java 25 files that run a piece of the mod's logic against many inputs and
print a PASS / FAIL / CRASH table. They don't start Minecraft and aren't part of the mod jar.

The AI writes these only when you ask (`/simulate`, then "write the test").

## Run one

**Tests that use the real classes** (`Book`, `BookList`, `Task`, `FlipItem`, `BazaarData`):
```sh
./gradlew compileClientJava                                   # once, and after you change src/
java --class-path build/classes/java/client tests/<Name>Sim.java
```

**Tests with "mirrored" logic** (copies of code that normally needs Minecraft):
```sh
java tests/<Name>Sim.java
```
Each file's header says which command it needs and which `src/` lines it tests.
Mirrored tests have a `// MIRRORS ... @ <commit>` line. If those `src/` lines changed,
the copy is out of date.

## See an example first

```sh
./gradlew compileClientJava
java --class-path build/classes/java/client .agents/skills/simulate/examples/TaskAssignBookSim.java
```

## Reading the output

```
case                           | return       | amtLeft      | added | verdict
normal: 1x level I             |   0 (exp  0) |  15 (exp 15) |     1 | PASS
weird: amountOfBook = -1       |   0 (exp -1) |  17 (exp 16) |     0 | FAIL
```
- `exp` = what the case expected (your intent — check that the AI guessed it right).
- `FAIL` = the code did something else. `CRASH` = it threw an exception (shown in the row).
