package app;

import java.nio.file.Path;
import java.util.List;

/**
 * Replay one day of order changes through the pipeline, the way production ran it on 2026-10-27.
 *
 * <pre>java src/app/Main.java</pre>
 *
 * It empties the local orders table, starts 3 worker instances, delivers every message of
 * data/changes_2026-10-27.jsonl through the local Pub/Sub subscription and prints a summary.
 */
public class Main {
    static final Path CHANGES = Path.of("data", "changes_2026-10-27.jsonl");
    static final int INSTANCES = 3;

    public static void main(String[] args) {
        Warehouse warehouse = new Warehouse();
        warehouse.reset();
        List<Worker> workers = List.of(new Worker(warehouse, "instance-1"), new Worker(warehouse, "instance-2"),
                new Worker(warehouse, "instance-3"));
        LocalSubscription subscription = new LocalSubscription(CHANGES);
        int[] n = {0};
        subscription.deliver(message -> workers.get(n[0]++ % INSTANCES).handle(message)); // Cloud Run spreads requests
        List<Row> rows = warehouse.all();
        long orders = rows.stream().map(Row::orderId).distinct().count();
        System.out.println("Pub/Sub: " + subscription.stats);
        System.out.println("orders table: " + rows.size() + " rows, " + orders + " distinct orders");
    }
}
