# Code Challenge — Order sync pipeline, Java (30 minutes)

An online store keeps its orders in **Spanner**. Each change to an order is published to **Pub/Sub** (a message queue). This Java service, running on **Cloud Run** as several instances, loads each change into the **BigQuery** table `orders`, used for revenue reports. Here, Pub/Sub and BigQuery are local stand-ins that behave like the real ones.

The tests pass, but after loading 2026-10-27 the analytics team reported:

1. O-1013 and O-1026 appear **twice** in `orders`.
2. O-1040 shows CREATED, but it was paid hours ago; O-1025, O-1033, O-1035 and O-1060 show PAID, but were shipped.
3. O-1028 and O-1055 **never reached** BigQuery.
4. O-1009, O-1023, O-1038 and O-1051 were **deleted** in Spanner but are still in `orders`.

## The ticket's acceptance criteria

Pub/Sub delivers each message **at least once** (sometimes twice) and **in no guaranteed order**; an unacknowledged message is delivered again. The service runs as several instances.

1. Each order appears in `orders` **exactly once**.
2. Each row shows the order's **latest** change (newest `commitTs`).
3. **No change is lost**, even when BigQuery is briefly unavailable.
4. **Deleted** orders are not in `orders`.

## Steps (move on when the time is up)

1. **Investigate (10 min).** For each report, find the cause in the code and the criterion it breaks. Say what you would fix first. No code changes yet.
2. **Fix (12 min).** Fix the causes, most important first, and show how you checked each fix.
3. **Add a check (8 min).** Write `src/app/Reconcile.java`: it compares `orders` with Spanner's export (`data/spanner_orders_2026-10-27.jsonl`), lists missing, extra, duplicated and outdated orders, and exits with 1 if there is any difference.

## Rules

Keep the existing code. Don't trust the tests over the ticket. Don't change `LocalSubscription.java` (it stands in for Pub/Sub). No new libraries. An AI assistant is allowed: share your screen and be ready to explain every change.

## Running it

Java 22 or newer runs these directly, with no build tool. Without Java, put `docker compose run --rm app` in front of each command.

```bash
java src/app/Main.java                  # replay 2026-10-27 through the pipeline
java src/app/Query.java order O-1013    # also: duplicates, count, all
java src/app/WorkerTest.java            # run the tests
```

The service is `src/app/Worker.java`. `Warehouse.java` is the BigQuery stand-in (it fails now and then, like the real one); `Main.java` replays the day's 163 changes through 3 instances.
