package com.example.zuora.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.zuora.client.ZuoraClient;
import com.example.zuora.config.ZuoraConfig;
import com.example.zuora.model.OrderEvent;
import com.example.zuora.service.InvoiceCorrectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Zuora Order.Completed callout を受け取る Lambda ハンドラ。
 * API Gateway (HTTP API) 経由で呼び出されることを想定。
 *
 * Lambda ハンドラ設定: com.example.zuora.handler.OrderEventHandler::handleRequest
 */
public class OrderEventHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // ZuoraClient はウォームインボケーション間で共有する。
    // これにより OAuth トークン取得のオーバーヘッドを低減する。
    private final ZuoraClient zuoraClient;
    private final ObjectMapper objectMapper;

    public OrderEventHandler() {
        ZuoraConfig config = new ZuoraConfig();
        this.zuoraClient = new ZuoraClient(config);
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {

        try {
            String body = input.getBody();
            if (body == null || body.isBlank()) {
                return response(400, "{\"error\":\"missing request body\"}");
            }

            OrderEvent event = objectMapper.readValue(body, OrderEvent.class);
            InvoiceCorrectionService service = new InvoiceCorrectionService(zuoraClient, context.getLogger());

            service.correct(event);

            return response(200, "{\"status\":\"ok\"}");

        } catch (Exception e) {
            context.getLogger().log("Unhandled error: " + e.getMessage());
            return response(500, "{\"error\":\"internal error\"}");
        }
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody(body);
    }
}
