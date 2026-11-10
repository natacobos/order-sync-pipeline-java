package app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A local stand-in for the BigQuery dataset: the {@code orders} table, kept in the file {@code warehouse.tsv}.
 *
 * <p>Like a BigQuery table, {@code orders} does not enforce unique keys: nothing stops two rows with the same
 * orderId. There is no MERGE here; read the current rows with {@link #find} and then insert, update or delete.
 *
 * <p>To simulate BigQuery being briefly unavailable (HTTP 503), every 25th write throws WarehouseUnavailable.
 * Reads never fail.
 */
public class Warehouse {
    private final Path file;
    private final int failEvery;
    private final List<Row> rows = new ArrayList<>();
    private int writes = 0;

    /** The table in warehouse.tsv, failing every 25th write. */
    public Warehouse() {
        this(Path.of("warehouse.tsv"), 25);
    }

    /** file == null keeps the table in memory only (tests); failEvery == 0 never fails. */
    public Warehouse(Path file, int failEvery) {
        this.file = file;
        this.failEvery = failEvery;
        load();
    }

    public void reset() {
        rows.clear();
        save();
    }

    /** Adds one row. */
    public void insert(Row row) {
        beginWrite();
        rows.add(row);
        save();
    }

    /** Replaces every row with this orderId; returns how many rows changed. */
    public int update(String orderId, Row row) {
        beginWrite();
        int changed = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).orderId().equals(orderId)) {
                rows.set(i, row);
                changed++;
            }
        }
        save();
        return changed;
    }

    /** Removes every row with this orderId; returns how many rows were removed. */
    public int delete(String orderId) {
        beginWrite();
        int before = rows.size();
        rows.removeIf(r -> r.orderId().equals(orderId));
        save();
        return before - rows.size();
    }

    /** The rows with this orderId (a read: never fails). */
    public List<Row> find(String orderId) {
        return rows.stream().filter(r -> r.orderId().equals(orderId)).toList();
    }

    /** Every row, in the order they were written. */
    public List<Row> all() {
        return List.copyOf(rows);
    }

    private void beginWrite() {
        writes++;
        if (failEvery > 0 && writes % failEvery == 0) {
            throw new WarehouseUnavailable("503 Service Unavailable (write #" + writes + ")");
        }
    }

    private void load() {
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                String[] f = line.split("\t", -1);
                rows.add(new Row(f[0], blank(f[1]), blank(f[2]), f[3].isEmpty() ? null : Long.valueOf(f[3]), f[4]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (Row r : rows) {
            out.append(r.orderId()).append('\t').append(text(r.customerId())).append('\t').append(text(r.status()))
               .append('\t').append(text(r.amountCents())).append('\t').append(r.commitTs()).append('\n');
        }
        try {
            Files.writeString(file, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String blank(String value) {
        return value.isEmpty() ? null : value;
    }
}
