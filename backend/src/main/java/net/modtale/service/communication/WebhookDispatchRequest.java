package net.modtale.service.communication;

import java.util.Map;

public record WebhookDispatchRequest(String url, Map<String, Object> body) {
}
