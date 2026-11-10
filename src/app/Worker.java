package app;

/**
 * The order-sync worker: receives order changes from Pub/Sub and loads them into the {@code orders} table.
 *
 * <p>In production each instance of this service runs on Cloud Run and Pub/Sub pushes messages to it.
 */
public class Worker {
    private final Warehouse warehouse;
    private final String name;
    private final java.util.Set<String> seen = new java.util.HashSet<>(); // message ids this instance already handled

    public Worker(Warehouse warehouse, String name) {
        this.warehouse = warehouse;
        this.name = name;
    }

    public void handle(Message message) {
        // Acknowledge right away: the subscription's ack deadline is 10 seconds and loads can be slow.
        message.ack();
        if (seen.contains(message.messageId())) { // Pub/Sub can deliver the same message twice
            return;
        }
        seen.add(message.messageId());

        OrderChange change = OrderChange.parse(message.data());
        try {
            load(change);
        } catch (WarehouseUnavailable error) {
            System.err.printf("WARNING %s: BigQuery unavailable, skipping %s (%s)%n", name, change.orderId(), error.getMessage());
        }
    }

    void load(OrderChange change) {
        Row row = new Row(change.orderId(), change.customerId(), change.status(), change.amountCents(), change.commitTs());
        if (change.modType().equals("INSERT")) {
            warehouse.insert(row);
        } else if (change.modType().equals("UPDATE")) {
            warehouse.update(change.orderId(), row);
        }
        // DELETE: nothing to load.
    }
}
