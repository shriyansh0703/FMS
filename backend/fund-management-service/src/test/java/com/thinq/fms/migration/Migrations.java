package com.thinq.fms.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the migration files so tests can assert that the schema and the code agree.
 *
 * <p>Nothing but a test can hold two files in different languages in step. A vocabulary is
 * declared twice — once as a Java enum and once as a SQL {@code CHECK} — and the two drift
 * silently: a value added to the enum and not the constraint fails at insert, in production, on a
 * row nobody expected to fail.
 */
public final class Migrations {

    private static final Path DIR = Path.of("src/main/resources/db/migration");

    private Migrations() {
    }

    public static String read(String fileName) {
        try {
            return Files.readString(DIR.resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read migration " + fileName, e);
        }
    }

    /**
     * The quoted values inside a named {@code CHECK (<column> IN (...))} constraint.
     *
     * @throws AssertionError if the constraint is absent, because a missing constraint is the
     *     failure these tests exist to catch and returning an empty set would let it pass
     */
    public static Set<String> checkValues(String fileName, String constraintName) {
        Matcher m = Pattern.compile(
                        "CONSTRAINT\\s+" + Pattern.quote(constraintName)
                                + "\\s+CHECK\\s*\\(\\s*\\w+\\s+IN\\s*\\(([^)]*)\\)",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(read(fileName));

        if (!m.find()) {
            throw new AssertionError("constraint " + constraintName + " not found in " + fileName);
        }
        return quoted(m.group(1));
    }

    /** The quoted values in a named partial index's {@code WHERE ... IN (...)} predicate. */
    public static Set<String> indexPredicateValues(String fileName, String indexName) {
        Matcher m = Pattern.compile(
                        "CREATE\\s+UNIQUE\\s+INDEX\\s+" + Pattern.quote(indexName)
                                + ".*?WHERE\\s+\\w+\\s+IN\\s*\\(([^)]*)\\)",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(read(fileName));

        if (!m.find()) {
            throw new AssertionError("index " + indexName + " not found in " + fileName);
        }
        return quoted(m.group(1));
    }

    private static Set<String> quoted(String list) {
        Set<String> out = new TreeSet<>();
        Matcher q = Pattern.compile("'([^']+)'").matcher(list);
        while (q.find()) {
            out.add(q.group(1));
        }
        return out;
    }
}
