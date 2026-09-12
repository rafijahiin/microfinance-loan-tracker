package io.github.rafijahiin.loantracker.schema;

import io.github.rafijahiin.loantracker.audit.AuditEvent;
import io.github.rafijahiin.loantracker.borrower.Borrower;
import io.github.rafijahiin.loantracker.loan.Instalment;
import io.github.rafijahiin.loantracker.loan.Loan;
import io.github.rafijahiin.loantracker.loan.Repayment;
import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.user.AppUser;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one thing the rest of the suite cannot see.
 *
 * The tests run against a schema Hibernate generates from the entity mappings,
 * while production runs against the Flyway migrations with ddl-auto set to
 * validate. Those two can drift, and when they do every other test still
 * passes and the application refuses to start on the next deployment.
 *
 * That is not hypothetical. It has already caught two things here: Borrower
 * needed its column named union_name because UNION is a reserved SQL word, and
 * adding V2 for the national ID and repayment frequency broke this test until
 * the migration matched the mappings.
 *
 * The whole migration history is replayed in version order, not just the
 * initial schema, because a column added in V2 is every bit as real as one
 * created in V1.
 */
class MigrationMatchesEntitiesTest {

    private static final Class<?>[] ENTITIES = {
            PartnerOrganisation.class, AppUser.class, Borrower.class,
            Loan.class, Instalment.class, Repayment.class, AuditEvent.class,
    };

    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?is)CREATE\\s+TABLE\\s+(\\w+)\\s*\\(");
    private static final Pattern ADD_COLUMN =
            Pattern.compile("(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+COLUMN\\s+(\\w+)");
    private static final Pattern RENAME_COLUMN = Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+RENAME\\s+COLUMN\\s+(\\w+)\\s+TO\\s+(\\w+)");
    private static final Pattern DROP_COLUMN =
            Pattern.compile("(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+DROP\\s+COLUMN\\s+(\\w+)");

    @Test
    @DisplayName("every mapped table and column exists once the migrations have run")
    void theMigrationsCreateEverythingTheMappingsExpect() throws IOException {
        Map<String, Set<String>> declared = replayMigrations();
        Map<String, Set<String>> mapped = mappedTables();

        List<String> problems = new ArrayList<>();
        mapped.forEach((table, columns) -> {
            Set<String> actual = declared.get(table);
            if (actual == null) {
                problems.add("table '" + table + "' is mapped but never created");
                return;
            }
            columns.stream()
                    .filter(c -> !actual.contains(c))
                    .forEach(c -> problems.add(
                            "column '" + table + "." + c + "' is mapped but not created"));
        });

        assertThat(problems)
                .as("entity mappings and the Flyway migrations have drifted apart, "
                    + "which ddl-auto=validate would only reveal at deployment")
                .isEmpty();
    }

    @Test
    @DisplayName("the migrations leave behind no column the mappings do not know about")
    void theMigrationsLeaveNoOrphanColumns() throws IOException {
        Map<String, Set<String>> declared = replayMigrations();
        Map<String, Set<String>> mapped = mappedTables();

        assertThat(declared.keySet())
                .as("a table created but never mapped is either dead weight or a "
                    + "mapping someone forgot to add")
                .containsExactlyInAnyOrderElementsOf(mapped.keySet());

        List<String> orphans = new ArrayList<>();
        declared.forEach((table, columns) -> {
            Set<String> expected = mapped.getOrDefault(table, Set.of());
            columns.stream()
                    .filter(c -> !expected.contains(c))
                    .forEach(c -> orphans.add(table + "." + c));
        });

        // A column the application never reads is a column nobody maintains,
        // and on a financial table it is a column somebody will eventually
        // mistake for authoritative.
        assertThat(orphans).as("columns exist in the schema but are not mapped")
                .isEmpty();
    }

    // ── Replaying the migrations ────────────────────────────────────────────

    /** Applies every V*.sql in version order and returns the resulting shape. */
    private Map<String, Set<String>> replayMigrations() throws IOException {
        Map<String, Set<String>> tables = new TreeMap<>();

        for (Resource resource : migrationsInOrder()) {
            String sql = stripComments(new String(
                    resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

            for (String statement : sql.split(";")) {
                applyStatement(statement, tables);
            }
        }
        return tables;
    }

    private void applyStatement(String statement, Map<String, Set<String>> tables) {
        Matcher create = CREATE_TABLE.matcher(statement);
        if (create.find()) {
            tables.put(create.group(1).toLowerCase(Locale.ROOT),
                    columnsOf(statement, create.end() - 1));
            return;
        }
        Matcher rename = RENAME_COLUMN.matcher(statement);
        if (rename.find()) {
            Set<String> cols = table(tables, rename.group(1));
            cols.remove(rename.group(2).toLowerCase(Locale.ROOT));
            cols.add(rename.group(3).toLowerCase(Locale.ROOT));
            return;
        }
        Matcher drop = DROP_COLUMN.matcher(statement);
        if (drop.find()) {
            table(tables, drop.group(1)).remove(drop.group(2).toLowerCase(Locale.ROOT));
            return;
        }
        Matcher add = ADD_COLUMN.matcher(statement);
        if (add.find()) {
            table(tables, add.group(1)).add(add.group(2).toLowerCase(Locale.ROOT));
        }
        // Anything else (indexes, constraints, DROP DEFAULT) does not change
        // which columns exist, so it is deliberately ignored.
    }

    private Set<String> table(Map<String, Set<String>> tables, String name) {
        return tables.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new TreeSet<>());
    }

    private List<Resource> migrationsInOrder() throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql");
        assertThat(found).as("migrations are on the classpath").isNotEmpty();

        List<Resource> ordered = new ArrayList<>(Arrays.asList(found));
        ordered.sort(Comparator.comparingInt(r -> versionOf(r.getFilename())));
        return ordered;
    }

    /** V2__whatever.sql -> 2. Flyway orders by version, so this test must too:
     *  applying V2's RENAME before V1's CREATE would produce nonsense. */
    private int versionOf(String filename) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(
                filename == null ? "" : filename);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    /**
     * Column names from the body of a CREATE TABLE, skipping table-level
     * constraints.
     *
     * Splits on commas at nesting depth zero rather than on line breaks. A
     * line-based reader looked correct until a CHECK constraint wrapped:
     *
     *     CONSTRAINT ck_audit_action CHECK (action IN (
     *         'MEMBER_ENROLLED', 'LOAN_DISBURSED', ...
     *     ))
     *
     * and the continuation line was read as a column named 'member_enrolled'.
     * A guard that reports invented columns is worse than no guard, because
     * the next person to meet it silences it.
     */
    private Set<String> columnsOf(String statement, int openParen) {
        String body = statement.substring(openParen + 1, closingParen(statement, openParen));
        Set<String> columns = new TreeSet<>();

        for (String entry : splitTopLevel(body)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("CONSTRAINT") || upper.startsWith("PRIMARY KEY")
                    || upper.startsWith("UNIQUE") || upper.startsWith("FOREIGN KEY")
                    || upper.startsWith("CHECK")) {
                continue;
            }
            String first = trimmed.split("[\\s(,]")[0].trim();
            if (!first.isEmpty()) {
                columns.add(first.toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    /** Commas inside parentheses belong to a type or a constraint rather than
     *  to the column list: NUMERIC(15,2) and IN ('A','B') both contain them. */
    private List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (char c : body.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    /** Index of the parenthesis closing the one at `open`. Needed because
     *  column definitions such as NUMERIC(15,2) contain their own. */
    private int closingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        throw new IllegalStateException("Unbalanced parentheses in a migration");
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    // ── Reading the mappings ────────────────────────────────────────────────

    private Map<String, Set<String>> mappedTables() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect",
                        "org.hibernate.dialect.PostgreSQLDialect")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> entity : ENTITIES) {
                sources.addAnnotatedClass(entity);
            }
            Metadata metadata = sources.buildMetadata();

            Map<String, Set<String>> tables = new TreeMap<>();
            for (PersistentClass binding : metadata.getEntityBindings()) {
                Table table = binding.getTable();
                Set<String> columns = tables.computeIfAbsent(
                        table.getName().toLowerCase(Locale.ROOT), k -> new TreeSet<>());
                for (Column column : table.getColumns()) {
                    columns.add(column.getName().toLowerCase(Locale.ROOT));
                }
            }
            return tables;
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
