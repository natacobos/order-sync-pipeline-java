package app;

/** One Pub/Sub message delivered to the service. */
public class Message {
    private final String messageId;
    private final String data;
    final int deliveryAttempt;
    private final LocalSubscription subscription;
    boolean settled = false;

    Message(String messageId, String data, int deliveryAttempt, LocalSubscription subscription) {
        this.messageId = messageId;
        this.data = data;
        this.deliveryAttempt = deliveryAttempt;
        this.subscription = subscription;
    }

    public String messageId() {
        return messageId;
    }

    /** The message body: one JSON order change. */
    public String data() {
        return data;
    }

    /** Tell Pub/Sub the message was processed: it is not delivered again (except the occasional repeat). */
    public void ack() {
        settle(true);
    }

    /** Tell Pub/Sub the message was not processed: it is delivered again later. */
    public void nack() {
        settle(false);
    }

    private void settle(boolean acked) {
        if (!settled) {
            settled = true;
            subscription.settled(this, acked);
        }
    }
}
