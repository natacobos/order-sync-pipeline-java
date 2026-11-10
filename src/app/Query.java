package app;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Look at the local orders table.
 *
 * <pre>
 * java src/app/Query.java all              every row
 * java src/app/Query.java order O-1013     the rows of one order
 * java src/app/Query.java duplicates       orders with more than one row, and how many
 * java src/app/Query.java count            number of rows and of distinct orders
 * </pre>
 */
public class Query {
    public static void main(String[] args) {
        Warehouse warehouse = new Warehouse();
        String what = args.length > 0 ? args[0] : "all";
        switch (what) {
            case "all" -> warehouse.all().forEach(Query::print);
            case "order" -> warehouse.find(args[1]).forEach(Query::print);
            case "duplicates" -> {
                Map<String, Integer> counts = new TreeMap<>();
                warehouse.all().forEach(r -> counts.merge(r.orderId(), 1, Integer::sum));
                counts.forEach((id, c) -> { if (c > 1) System.out.println(id + " | " + c); });
            }
            case "count" -> {
                List<Row> rows = warehouse.all();
                System.out.println(rows.size() + " | " + rows.stream().map(Row::orderId).distinct().count());
            }
            default -> {
                System.err.println("usage: java src/app/Query.java all | order <id> | duplicates | count");
                System.exit(2);
            }
        }
    }

    private static void print(Row r) {
        System.out.println(String.join(" | ", r.orderId(), text(r.customerId()), text(r.status()),
                text(r.amountCents()), r.commitTs()));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
