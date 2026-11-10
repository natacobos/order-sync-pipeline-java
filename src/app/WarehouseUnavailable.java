package app;

/** BigQuery answered 503: the write did not happen and can be tried again. */
public class WarehouseUnavailable extends RuntimeException {
    public WarehouseUnavailable(String message) {
        super(message);
    }
}
