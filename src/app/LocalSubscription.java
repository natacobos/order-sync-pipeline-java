package app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A local stand-in for a Pub/Sub subscription. It behaves like the real one in the ways that matter here:
 * <ul>
 *   <li>At-least-once delivery: a message that is not acknowledged (ack) is delivered again later. Calling nack()
 *       asks for it to be delivered again. Even an acknowledged message is occasionally delivered a second time.</li>
 *   <li>No ordering: messages can arrive in a different order from the one they were published in.</li>
 *   <li>Several instances: deliveries are spread across the service's instances, so a message delivered again
 *       usually goes to a different instance.</li>
 * </ul>
 * The behavior is fixed by a seed, so every run delivers the same messages in the same order.
 * Do not change this file: it stands in for Pub/Sub, which you cannot change in production either.
 */
public class LocalSubscription {
    private record Pending(String id, String body, int attempt) {}

    private final SeededRandom rng;
    private final double repeatRate;
    private final int maxAttempts;
    private final List<Pending> queue = new ArrayList<>();
    public final Map<String, Integer> stats = new LinkedHashMap<>();

    public LocalSubscription(Path path) {
        this(path, 13, 0.08, 0.06, 10);
    }

    public LocalSubscription(Path path, int seed, double swapRate, double repeatRate, int maxAttempts) {
        List<String> bodies;
        try {
            bodies = Files.readAllLines(path).stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.rng = new SeededRandom(seed);
        this.repeatRate = repeatRate;
        this.maxAttempts = maxAttempts;
        for (int i = 0; i < bodies.size(); i++) {
            queue.add(new Pending(String.format("m-%04d", i), bodies.get(i), 1));
        }
        for (int i = 0; i < queue.size() - 1; i++) { // some neighbors arrive swapped
            if (rng.random() < swapRate) {
                Pending a = queue.get(i);
                queue.set(i, queue.get(i + 1));
                queue.set(i + 1, a);
            }
        }
        for (String k : List.of("delivered", "acked", "nacked", "not_settled", "repeated")) stats.put(k, 0);
    }

    /** Deliver messages one by one, the way a push subscription calls the service. */
    public void deliver(Consumer<Message> service) {
        while (!queue.isEmpty()) {
            Pending p = queue.remove(0);
            Message message = new Message(p.id(), p.body(), p.attempt(), this);
            count("delivered");
            service.accept(message);
            if (!message.settled) { // the ack deadline passed: delivered again
                count("not_settled");
                again(message);
            }
        }
    }

    void settled(Message message, boolean acked) {
        if (acked) {
            count("acked");
            if (rng.random() < repeatRate) { // Pub/Sub may deliver an acknowledged message again
                count("repeated");
                queue.add(Math.min(queue.size(), rng.randint(1, 4)),
                        new Pending(message.messageId(), message.data(), message.deliveryAttempt + 1));
            }
        } else {
            count("nacked");
            again(message);
        }
    }

    private void again(Message message) {
        if (message.deliveryAttempt < maxAttempts) {
            int size = queue.size();
            queue.add(Math.min(size, rng.randint(3, 8)),
                    new Pending(message.messageId(), message.data(), message.deliveryAttempt + 1));
        }
    }

    private void count(String key) {
        stats.merge(key, 1, Integer::sum);
    }

    /** Python's random.Random (Mersenne Twister), so this stand-in delivers exactly like the Python version. */
    static final class SeededRandom {
        private final int[] mt = new int[624];
        private int index = 625;

        SeededRandom(int seed) {
            initGenrand(19650218);
            int[] key = {seed};
            int i = 1, j = 0;
            for (int k = Math.max(624, key.length); k > 0; k--) {
                mt[i] = (int) (((mt[i] & 0xffffffffL) ^ (((mt[i - 1] ^ (mt[i - 1] >>> 30)) & 0xffffffffL) * 1664525L))
                        + (key[j] & 0xffffffffL) + j);
                i++; j++;
                if (i >= 624) { mt[0] = mt[623]; i = 1; }
                if (j >= key.length) j = 0;
            }
            for (int k = 623; k > 0; k--) {
                mt[i] = (int) (((mt[i] & 0xffffffffL) ^ (((mt[i - 1] ^ (mt[i - 1] >>> 30)) & 0xffffffffL) * 1566083941L)) - i);
                i++;
                if (i >= 624) { mt[0] = mt[623]; i = 1; }
            }
            mt[0] = 0x80000000;
            index = 624;
        }

        private void initGenrand(int s) {
            mt[0] = s;
            for (int i = 1; i < 624; i++) {
                mt[i] = (int) (1812433253L * ((mt[i - 1] ^ (mt[i - 1] >>> 30)) & 0xffffffffL) + i);
            }
            index = 624;
        }

        private long next32() {
            if (index >= 624) {
                for (int k = 0; k < 624; k++) {
                    int y = (mt[k] & 0x80000000) | (mt[(k + 1) % 624] & 0x7fffffff);
                    mt[k] = mt[(k + 397) % 624] ^ (y >>> 1) ^ ((y & 1) != 0 ? 0x9908b0df : 0);
                }
                index = 0;
            }
            int y = mt[index++];
            y ^= (y >>> 11);
            y ^= (y << 7) & 0x9d2c5680;
            y ^= (y << 15) & 0xefc60000;
            y ^= (y >>> 18);
            return y & 0xffffffffL;
        }

        double random() {
            long a = next32() >>> 5, b = next32() >>> 6;
            return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
        }

        int randint(int low, int high) {
            int n = high - low + 1;
            int k = 32 - Integer.numberOfLeadingZeros(n);
            long r = next32() >>> (32 - k);
            while (r >= n) r = next32() >>> (32 - k);
            return low + (int) r;
        }
    }
}
