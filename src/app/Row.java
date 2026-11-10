package app;

/** One row of the {@code orders} table. */
public record Row(String orderId, String customerId, String status, Long amountCents, String commitTs) {
}
