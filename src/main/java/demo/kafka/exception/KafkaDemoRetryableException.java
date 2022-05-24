package demo.kafka.exception;

public class KafkaDemoRetryableException extends RuntimeException implements Retryable {
    public KafkaDemoRetryableException(Throwable cause) {
        super(cause);
    }
}
