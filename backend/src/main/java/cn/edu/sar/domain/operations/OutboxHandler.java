package cn.edu.sar.domain.operations;

/** E implements this as a Spring bean. Only local transactional DB work; never HTTP/mail here. */
public interface OutboxHandler {
    String eventType();
    void handle(OutboxService.Event event);
}
