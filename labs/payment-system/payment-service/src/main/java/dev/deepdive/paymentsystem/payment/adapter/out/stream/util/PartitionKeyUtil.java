package dev.deepdive.paymentsystem.payment.adapter.out.stream.util;

import org.springframework.stereotype.Component;

@Component
public class PartitionKeyUtil {

    private static final int PARTITION_KEY_COUNT = 6;

    public int createPartitionKey(int number) {
        return Math.abs(number) % PARTITION_KEY_COUNT;
    }
}
