// SIMULATION: Task.assignBook(book, level, location, amountOfBook)
// TESTS:      src/client/java/com/goofy/goofyaddons/features/bookflipper/helper/Task.java:61-79
// MODE:       real class (Task and Book are plain Java, no Minecraft imports)
// RUN:        ./gradlew compileClientJava
//             java --class-path build/classes/java/client tests/TaskAssignBookSim.java
//
// What it checks: which inputs assignBook accepts (0) or rejects (-1),
// and what happens to amountToOrder and bookList for each input.

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;

// (1) One test case = plain data: a name, the inputs, and what we expect back.
record Case(String name, Book bookPassed, int level, int location, int amountOfBook,
            int expectedReturn, int expectedAmountLeft) {}

// (2) What actually happened, also plain data, so printing is separate from running.
record Result(Case c, int actualReturn, int actualAmountLeft, int booksAdded, String error) {
    boolean passed() {
        return error == null
                && actualReturn == c.expectedReturn()
                && actualAmountLeft == c.expectedAmountLeft();
    }
}

// The book every Task in this file is created with. Level 1 -> sell level 5 = needs 16 books.
static final Book UW = new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise", 0, 0);

// (3) The input table. Normal values first, then edges, then "weird data" cases.
//     "expected" = what the developer INTENDS. The AI guessed these; the developer confirms them.
static List<Case> cases() {
    // Same values as UW but a different object (like after GoofyConfig.load() runs again).
    Book uwCopy = new Book("ENCHANTMENT_ULTIMATE_WISE", 1, 5, "Ultimate Wise", 0, 0);
    return List.of(
            new Case("normal: 1x level I",          UW,     1, 0,  1,  0, 15),
            new Case("normal: 1x level III",        UW,     3, 1,  1,  0, 12),
            new Case("edge: exactly fills (1x V)",  UW,     5, 0,  1,  0,  0),
            new Case("edge: too many (17x I)",      UW,     1, 0, 17, -1, 16),
            new Case("edge: level below book (0)",  UW,     0, 0,  1, -1, 16),
            new Case("edge: amountOfBook = 0",      UW,     1, 0,  0,  0, 16),
            new Case("weird: equal Book, new obj",  uwCopy, 1, 0,  1,  0, 15),
            new Case("weird: amountOfBook = -1",    UW,     1, 0, -1, -1, 16),
            new Case("weird: level 40 (shift)",     UW,    40, 0,  1, -1, 16)
    );
}

// (4) Run one case on a fresh Task, so cases can't affect each other.
//     Exceptions are caught and stored as data instead of stopping the whole run.
static Result run(Case c) {
    Task task = new Task(UW, false, false);
    try {
        int ret = task.assignBook(c.bookPassed(), c.level(), c.location(), c.amountOfBook());
        return new Result(c, ret, task.getAmountToOrder(), task.bookList.size(), null);
    } catch (Exception e) {
        return new Result(c, 0, task.getAmountToOrder(), task.bookList.size(), e.toString());
    }
}

// (5) Run everything and print one row per case.
void main() {
    List<Result> results = cases().stream().map(c -> run(c)).toList();

    IO.println(String.format("%-30s | %-12s | %-12s | %-5s | %s",
            "case", "return", "amtLeft", "added", "verdict"));
    for (Result r : results) {
        String verdict = r.error() != null ? "CRASH " + r.error() : (r.passed() ? "PASS" : "FAIL");
        IO.println(String.format("%-30s | %3d (exp %2d) | %3d (exp %2d) | %5d | %s",
                r.c().name(), r.actualReturn(), r.c().expectedReturn(),
                r.actualAmountLeft(), r.c().expectedAmountLeft(), r.booksAdded(), verdict));
    }

    long failed = results.stream().filter(r -> !r.passed()).count();
    IO.println("\n" + (results.size() - failed) + "/" + results.size() + " passed");
}
