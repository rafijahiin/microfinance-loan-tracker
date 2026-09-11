package bd.org.pksf.loantracker.schema;

import bd.org.pksf.loantracker.borrower.Borrower;
import bd.org.pksf.loantracker.loan.Instalment;
import bd.org.pksf.loantracker.loan.Loan;
import bd.org.pksf.loantracker.loan.Repayment;
import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import bd.org.pksf.loantracker.user.AppUser;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one thing the rest of the suite cannot see.
 *
 * The tests run against a schema Hibernate generates from the entity mappings,
 * while production runs against the hand-written Flyway migration with
 * ddl-auto set to validate. Those two can drift, and when they do every test
 * still passes and the application refuses to start on the next deployment.
 *
 * That is not hypothetical: Borrower.union had to be mapped to a column named
 * union_name because UNION is a reserved SQL word, and nothing else in this
 * suite would have noticed if the migration had kept the original name.
 *
 * So: read the migration, read the mappings, and assert every mapped table and
 * column is actually created.
 */
class MigrationMatchesEntitiesTest {

    private static final String MIGRATION = "db/migration/V1__initial_schema.sql";

    private static final Class<?>[] ENTITIES = {
            PartnerOrganisation.class, AppUser.class, Borrower.class,
            Loan.class, Instalment.class, Repayment.class,
    };

    @Test
    @DisplayName("every mapped table and column exists in the Flyway migration")
    void theMigrationCreatesEverythingTheMappingsExpect() throws IOException {
        Map<String, Set<String>> declared = parseMigration(readMigration());
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
                .as("entity mappings and the Flyway migration have drifted apart, "
                    + "which ddl-auto=validate would only reveal at deployment")
                .isEmpty();
    }

    @Test
    @DisplayName("the migration creates no table the mappings do not know about")
    void theMigrationHasNoOrphanTables() throws IOException {
        Set<String> declared = parseMigration(readMigration()).keySet();
        Set<String> mapped = mappedTables().keySet();

        assertThat(declared)
                .as("a table created but never mapped is either dead weight or a "
                    + "mapping someone forgot to add")
                .containsExactlyInAnyOrderElementsOf(mapped);
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

    // ── Reading the migration ───────────────────────────────────────────────

    private String readMigration() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(in).as("migration %s is on the classpath", MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Pulls table and column names out of the CREATE TABLE statements. Only
     *  the shapes this migration actually uses are handled; it is a guard over
     *  one known file, not a general SQL parser. */
    private Map<String, Set<String>> parseMigration(String sql) {
        Map<String, Set<String>> tables = new TreeMap<>();

        String[] chunks = sql.split("(?i)CREATE\\s+TABLE\\s+");
        for (int i = 1; i < chunks.length; i++) {
            String chunk = chunks[i];
            int open = chunk.indexOf('(');
            if (open < 0) continue;

            String name = chunk.substring(0, open).trim().toLowerCase(Locale.ROOT);
            String body = chunk.substring(open + 1, closingParen(chunk, open));

            Set<String> columns = new TreeSet<>();
            for (String rawLine : body.split("\\R")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("--")) continue;

                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.startsWith("CONSTRAINT") || upper.startsWith("PRIMARY KEY")
                        || upper.startsWith("UNIQUE") || upper.startsWith("FOREIGN KEY")
                        || upper.startsWith("CHECK") || line.startsWith(")")) {
                    continue;
                }
                String first = line.split("[\\s(,]")[0].trim();
                if (!first.isEmpty()) {
                    columns.add(first.toLowerCase(Locale.ROOT));
                }
            }
            tables.put(name, columns);
        }
        return tables;
    }

    /** Index of the parenthesis that closes the one at `open`. Needed because
     *  column definitions such as NUMERIC(15,2) contain their own. */
    private int closingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        throw new IllegalStateException("Unbalanced parentheses in the migration");
    }
}
