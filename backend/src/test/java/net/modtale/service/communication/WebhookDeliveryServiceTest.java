package net.modtale.service.communication;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebhookDeliveryServiceTest {

    @Test
    void deliverAsyncSchedulesExecutorWork() {
        Executor executor = mock(Executor.class);
        WebhookDeliveryService service = new WebhookDeliveryService(executor);

        service.deliverAsync(new WebhookDispatchRequest("https://hooks.modtale.test", Map.of()), "Failed");

        verify(executor).execute(any(Runnable.class));
    }
}
