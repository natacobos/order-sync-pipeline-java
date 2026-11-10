package app;

import java.util.ArrayList;
import java.util.List;

/**
 * The existing tests. Run them with: java src/app/WorkerTest.java
 * (No test library is needed: each test is a method, and the program exits with 1 if any test fails.)
 */
public class WorkerTest {
    private Warehouse warehouse;
    private Worker worker;
    private int nextId = 1;

    private void setUp() {
        warehouse = new Warehouse(null, 0); // in memory, never unavailable
        worker = new Worker(warehouse, "instance-1");
        nextId = 1;
    }

    /** Delivers one change straight to the worker, the way Pub/Sub would. */
    private boolean deliver(String orderId, String modType, String commitTs, String status, long amountCents) {
        String data = modType.equals("DELETE") ? "{}" : String.format(
                "{\"customer_id\": \"C-001\", \"status\": \"%s\", \"amount_cents\": %d}", status, amountCents);
        String body = String.format("{\"order_id\": \"%s\", \"mod_type\": \"%s\", \"commit_ts\": \"%s\", \"data\": %s}",
                orderId, modType, commitTs, data);
        boolean[] acked = {false};
        LocalSubscription none = null;
        Message message = new Message("m-" + nextId++, body, 1, none) {
            @Override public void ack() { acked[0] = true; settled = true; }
            @Override public void nack() { settled = true; }
        };
        worker.handle(message);
        return acked[0];
    }

    private List<String> orders() {
        List<String> out = new ArrayList<>();
        warehouse.all().stream().sorted((a, b) -> a.orderId().compareTo(b.orderId()))
                .forEach(r -> out.add(r.orderId() + " " + r.status() + " " + r.amountCents()));
        return out;
    }

    void testInsertCreatesTheOrder() {
        boolean acked = deliver("O-1", "INSERT", "2026-10-27T08:00:00.000000Z", "CREATED", 2450);
        check(acked, "the message is acknowledged");
        check(orders().equals(List.of("O-1 CREATED 2450")), "table is " + orders());
    }

    void testUpdatesChangeTheStatus() {
        deliver("O-1", "INSERT", "2026-10-27T08:00:00.000000Z", "CREATED", 2450);
        deliver("O-1", "UPDATE", "2026-10-27T08:01:00.000000Z", "PAID", 2450);
        deliver("O-1", "UPDATE", "2026-10-27T08:05:00.000000Z", "SHIPPED", 2450);
        check(orders().equals(List.of("O-1 SHIPPED 2450")), "table is " + orders());
    }

    void testOrdersAreKeptApart() {
        deliver("O-1", "INSERT", "2026-10-27T08:00:00.000000Z", "CREATED", 2450);
        deliver("O-2", "INSERT", "2026-10-27T08:00:01.000000Z", "CREATED", 999);
        deliver("O-2", "UPDATE", "2026-10-27T08:02:00.000000Z", "PAID", 999);
        check(orders().equals(List.of("O-1 CREATED 2450", "O-2 PAID 999")), "table is " + orders());
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    public static void main(String[] args) {
        WorkerTest t = new WorkerTest();
        List<Runnable> tests = List.of(t::testInsertCreatesTheOrder, t::testUpdatesChangeTheStatus, t::testOrdersAreKeptApart);
        List<String> names = List.of("testInsertCreatesTheOrder", "testUpdatesChangeTheStatus", "testOrdersAreKeptApart");
        int failed = 0;
        for (int i = 0; i < tests.size(); i++) {
            t.setUp();
            try {
                tests.get(i).run();
                System.out.println(names.get(i) + " ... ok");
            } catch (AssertionError | RuntimeException e) {
                failed++;
                System.out.println(names.get(i) + " ... FAIL: " + e.getMessage());
            }
        }
        System.out.println();
        System.out.println("Ran " + tests.size() + " tests");
        System.out.println(failed == 0 ? "OK" : "FAILED (failures=" + failed + ")");
        System.exit(failed == 0 ? 0 : 1);
    }
}
