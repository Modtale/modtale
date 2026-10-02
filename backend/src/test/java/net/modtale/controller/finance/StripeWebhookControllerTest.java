package net.modtale.controller.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.modtale.repository.finance.PaymentWebhookReceiptRepository;
import net.modtale.service.finance.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StripeWebhookControllerTest {
    private DonationCheckoutService donations;
    private PaymentWebhookReceiptRepository receipts;
    private StripeGatewayService gateway;
    private StripeWebhookController controller;
    @BeforeEach void setup() {
        donations = mock(DonationCheckoutService.class); receipts = mock(PaymentWebhookReceiptRepository.class); gateway = mock(StripeGatewayService.class);
        when(gateway.isTestMode()).thenReturn(true); when(gateway.isReconciliationEnabled()).thenReturn(true);
        when(gateway.getPlatformAccountId()).thenReturn("acct_platform");
        controller = new StripeWebhookController(donations, receipts, gateway, mock(RecurringSupportService.class), mock(PaymentAdjustmentService.class), "secret");
    }
    private String signature(String body) throws Exception {
        long time = Instant.now().getEpochSecond(); var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + time + ",v1=" + HexFormat.of().formatHex(mac.doFinal((time + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
    private String body(boolean live) { return "{\"id\":\"evt_test\",\"api_version\":\"" + StripeGatewayService.API_VERSION + "\",\"livemode\":" + live + ",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test\"}}}"; }
    @Test void unsignedOrModifiedBodyCannotReachAccounting() throws Exception {
        assertEquals(400, controller.receive(body(false).getBytes(StandardCharsets.UTF_8), null).getStatusCode().value());
        assertEquals(400, controller.receive((body(false) + " ").getBytes(StandardCharsets.UTF_8), signature(body(false))).getStatusCode().value());
        verifyNoInteractions(donations, receipts);
    }
    @Test void duplicateEventDoesNotRunFulfillmentTwice() throws Exception {
        when(receipts.existsById("stripe:test:acct_platform:event:evt_test")).thenReturn(false, true);
        String body = body(false);
        assertEquals(200, controller.receive(body.getBytes(StandardCharsets.UTF_8), signature(body)).getStatusCode().value());
        assertEquals(200, controller.receive(body.getBytes(StandardCharsets.UTF_8), signature(body)).getStatusCode().value());
        verify(donations, times(1)).handlePaidCheckout(anyMap()); verify(receipts, times(1)).insert(any(net.modtale.model.finance.PaymentWebhookReceipt.class));
    }
    @Test void eventReceiptsAreScopedToConfiguredProviderAccount() throws Exception {
        String body = body(false);
        controller.receive(body.getBytes(StandardCharsets.UTF_8), signature(body));
        verify(receipts).existsById("stripe:test:acct_platform:event:evt_test");
        when(gateway.getPlatformAccountId()).thenReturn("acct_other");
        controller.receive(body.getBytes(StandardCharsets.UTF_8), signature(body));
        verify(receipts).existsById("stripe:test:acct_other:event:evt_test");
    }
    @Test void liveEventsFailClosedAndFailedFulfillmentIsNotAcknowledged() throws Exception {
        String body = body(true);
        assertEquals(503, controller.receive(body.getBytes(StandardCharsets.UTF_8), signature(body)).getStatusCode().value());
        verifyNoInteractions(receipts, donations);
        doThrow(new IllegalArgumentException("not recorded yet")).when(donations).handlePaidCheckout(anyMap());
        body = body(false);
        assertEquals(503, controller.receive(body.getBytes(StandardCharsets.UTF_8), signature(body)).getStatusCode().value());
        verify(receipts, never()).insert(any(net.modtale.model.finance.PaymentWebhookReceipt.class));
    }
}
