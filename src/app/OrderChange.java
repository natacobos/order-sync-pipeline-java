package app;

import java.util.Map;

/**
 * One change to one order, as published by the Spanner change stream.
 *
 * <p>A message body looks like this:
 * <pre>
 * {"order_id": "O-1007", "mod_type": "UPDATE", "commit_ts": "2026-10-27T14:05:12.000120Z",
 *  "data": {"customer_id": "C-031", "status": "PAID", "amount_cents": 4599}}
 * </pre>
 * modType is INSERT (order created), UPDATE (order changed) or DELETE (order removed; data is empty).
 * commitTs is when the change was committed in Spanner. Timestamps have the same format, so comparing them
 * as strings ({@code a.compareTo(b)}) gives the same result as comparing them as times.
 */
public record OrderChange(String orderId, String modType, String commitTs,
                          String customerId, String status, Long amountCents) {

    @SuppressWarnings("unchecked")
    public static OrderChange parse(String body) {
        Map<String, Object> raw = Json.parseObject(body);
        Map<String, Object> data = raw.get("data") == null ? Map.of() : (Map<String, Object>) raw.get("data");
        return new OrderChange((String) raw.get("order_id"), (String) raw.get("mod_type"), (String) raw.get("commit_ts"),
                (String) data.get("customer_id"), (String) data.get("status"), (Long) data.get("amount_cents"));
    }
}
