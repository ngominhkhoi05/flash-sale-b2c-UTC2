package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.common.exception.CommonErrorCode;
import com.b2c.flash_sale_b2c_UTC2.config.ZaloPayProperties;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.exception.OrderErrorCode;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.PaymentResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCallbackDto;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCreateOrderRequest;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCreateOrderResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.entity.Payment;
import com.b2c.flash_sale_b2c_UTC2.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ZaloPayClient zaloPayClient;
    private final ZaloPayProperties zaloPayProperties;
    private final ObjectMapper objectMapper;
    private final FlashSaleWsBroadcaster wsBroadcaster;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getOrderPayment(Long buyerId, Long orderId) {
        Order order = validateBuyerOrder(buyerId, orderId);
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        if (payments != null && !payments.isEmpty()) {
            Payment latest = payments.get(payments.size() - 1);
            return mapToResponse(latest, order);
        }
        return PaymentResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentMethod("UNPAID")
                .amount(order.getTotalAmount())
                .status("PENDING")
                .createdAt(order.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public PaymentResponse createZaloPayPayment(Long buyerId, Long orderId) {
        Order order = validateBuyerOrder(buyerId, orderId);
        if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        List<Payment> existingPayments = paymentRepository.findByOrderId(orderId);
        if (existingPayments != null && !existingPayments.isEmpty()) {
            Payment latest = existingPayments.get(existingPayments.size() - 1);
            if ("SUCCESS".equalsIgnoreCase(latest.getStatus())) {
                return mapToResponse(latest, order);
            }
            if ("PENDING".equalsIgnoreCase(latest.getStatus())
                    && latest.getCreatedAt() != null
                    && latest.getCreatedAt().isAfter(Instant.now().minusSeconds(900))) {
                if ((latest.getQrCodeData() == null || latest.getQrCodeData().isBlank() || latest.getQrCodeData().contains("vietqr"))
                        && latest.getPaymentUrl() != null && !latest.getPaymentUrl().isBlank()) {
                    latest.setQrCodeData(com.b2c.flash_sale_b2c_UTC2.common.util.QrCodeGenerator.generateQrBase64(latest.getPaymentUrl(), 300, 300));
                    paymentRepository.save(latest);
                }
                log.info("Reusing active PENDING ZaloPay payment id={} for orderId={}", latest.getId(), orderId);
                return mapToResponse(latest, order);
            }
        }

        long now = System.currentTimeMillis();
        String datePrefix = java.time.format.DateTimeFormatter.ofPattern("yyMMdd")
                .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(Instant.now());
        String appTransId = datePrefix + "_" + order.getId() + "_" + (now % 10000000L);
        String embedData = "{\"orderId\":" + order.getId() + ",\"orderCode\":\"" + order.getOrderCode() + "\"}";
        String item = "[]";
        long amount = order.getTotalAmount().longValue();

        ZaloPayCreateOrderRequest req = ZaloPayCreateOrderRequest.builder()
                .appId(zaloPayProperties.getAppId())
                .appTransId(appTransId)
                .appUser(zaloPayProperties.getAppUser())
                .amount(amount)
                .appTime(now)
                .embedData(embedData)
                .item(item)
                .description("Thanh toan don hang " + order.getOrderCode())
                .bankCode("")
                .callbackUrl(zaloPayProperties.getCallbackUrl())
                .build();

        ZaloPayCreateOrderResponse zpResp = zaloPayClient.createOrder(req);
        if (zpResp.getReturnCode() == null || zpResp.getReturnCode() != 1) {
            log.error("ZaloPay /v2/create failed: return_code={} message={}",
                    zpResp.getReturnCode(), zpResp.getReturnMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        String zpPayloadJson;
        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("app_trans_id", appTransId);
            payloadMap.put("zp_trans_token", zpResp.getZpTransToken());
            payloadMap.put("order_token", zpResp.getOrderToken());
            payloadMap.put("amount", amount);
            zpPayloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            log.warn("Failed to serialize ZaloPay response payload", e);
            zpPayloadJson = "{}";
        }

        String qrCodeData = zpResp.getQrCode();
        if ((qrCodeData == null || qrCodeData.isBlank() || !qrCodeData.startsWith("data:image/"))
                && zpResp.getOrderUrl() != null && !zpResp.getOrderUrl().isBlank()) {
            qrCodeData = com.b2c.flash_sale_b2c_UTC2.common.util.QrCodeGenerator.generateQrBase64(zpResp.getOrderUrl(), 300, 300);
        }

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod("ZALOPAY")
                .transactionCode(appTransId)
                .amount(order.getTotalAmount())
                .paymentUrl(zpResp.getOrderUrl())
                .qrCodeData(qrCodeData)
                .status("PENDING")
                .callbackPayload(zpPayloadJson)
                .createdAt(Instant.now())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Created ZaloPay payment for orderId={}, transactionCode={}", orderId, appTransId);

        try {
            wsBroadcaster.sendPaymentResultToUser(order.getBuyer().getEmail(), order.getOrderCode(),
                    order.getId(), "ZALOPAY", "PENDING");
        } catch (Exception e) {
            log.warn("[WS] Failed to send PAYMENT_STATUS_CHANGED for orderId={}: {}", orderId, e.getMessage());
        }

        return mapToResponse(payment, order);
    }

    @Override
    @Transactional
    public PaymentResponse confirmCodPayment(Long buyerId, Long orderId) {
        Order order = validateBuyerOrder(buyerId, orderId);
        if (!"PENDING_PAYMENT".equalsIgnoreCase(order.getStatus()) && !"CREATED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        String transactionCode = "COD_" + order.getOrderCode() + "_" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod("COD")
                .transactionCode(transactionCode)
                .amount(order.getTotalAmount())
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
        payment = paymentRepository.save(payment);

        order.setStatus("CONFIRMED");
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        log.info("Confirmed COD payment for orderId={}", orderId);

        try {
            wsBroadcaster.sendPaymentResultToUser(order.getBuyer().getEmail(), order.getOrderCode(),
                    order.getId(), "COD", "CONFIRMED");
        } catch (Exception e) {
            log.warn("[WS] Failed to send PAYMENT_STATUS_CHANGED for orderId={}: {}", orderId, e.getMessage());
        }

        return mapToResponse(payment, order);
    }

    @Override
    @Transactional
    public Map<String, Object> handleZaloPayCallback(ZaloPayCallbackDto cb) {
        log.info("Received ZaloPay callback: type={}", cb.getType());

        if (!zaloPayClient.verifyCallbackMac(cb)) {
            log.error("ZaloPay callback MAC verification FAILED");
            return Map.of("return_code", -1, "return_message", "mac not equal");
        }

        ZaloPayCallbackDto.ZaloPayCallbackData data;
        try {
            data = zaloPayClient.parseCallbackData(cb.getData());
        } catch (Exception e) {
            log.error("Failed to parse ZaloPay callback data: {}", e.getMessage());
            return Map.of("return_code", 0, "return_message", "invalid data: " + e.getMessage());
        }

        String appTransId = data.getAppTransId();
        if (appTransId == null || appTransId.isBlank()) {
            return Map.of("return_code", 0, "return_message", "missing app_trans_id");
        }

        Optional<Payment> optPayment = paymentRepository.findByTransactionCode(appTransId);
        if (optPayment.isEmpty()) {
            log.error("ZaloPay callback: payment not found for app_trans_id={}", appTransId);
            return Map.of("return_code", 0, "return_message", "payment not found");
        }

        Payment payment = optPayment.get();

        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            log.info("ZaloPay callback: payment {} already SUCCESS, skip (idempotent)", appTransId);
            return Map.of("return_code", 1, "return_message", "Success");
        }

        payment.setStatus("SUCCESS");
        payment.setPaidAt(Instant.now());
        if (data.getZpTransId() != null) {
            payment.setGatewayTransId(data.getZpTransId());
        }
        try {
            Map<String, Object> raw = new HashMap<>();
            raw.put("data", data);
            raw.put("mac", cb.getMac());
            raw.put("type", cb.getType());
            raw.put("received_at", Instant.now().toString());
            payment.setCallbackPayload(objectMapper.writeValueAsString(raw));
        } catch (Exception e) {
            log.warn("Failed to serialize callback audit payload", e);
        }
        payment = paymentRepository.save(payment);

        Order order = payment.getOrder();
        if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("CONFIRMED");
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            log.info("Order {} transitioned to CONFIRMED via ZaloPay callback", order.getOrderCode());
        }

        try {
            wsBroadcaster.sendPaymentResultToUser(order.getBuyer().getEmail(), order.getOrderCode(),
                    order.getId(), "ZALOPAY", "SUCCESS");
        } catch (Exception e) {
            log.warn("[WS] Failed to send PAYMENT_STATUS_CHANGED for orderId={}: {}",
                    order.getId(), e.getMessage());
        }

        return Map.of("return_code", 1, "return_message", "Success");
    }

    @Override
    @Transactional
    public PaymentResponse queryZaloPayPayment(Long buyerId, Long orderId) {
        Order order = validateBuyerOrder(buyerId, orderId);
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        if (payments == null || payments.isEmpty()) {
            if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
                return createZaloPayPayment(buyerId, orderId);
            }
            if ("CONFIRMED".equalsIgnoreCase(order.getStatus()) || "COMPLETED".equalsIgnoreCase(order.getStatus())) {
                return PaymentResponse.builder()
                        .orderId(order.getId())
                        .orderCode(order.getOrderCode())
                        .paymentMethod("ZALOPAY")
                        .amount(order.getTotalAmount())
                        .status("SUCCESS")
                        .createdAt(order.getCreatedAt())
                        .paidAt(order.getUpdatedAt())
                        .build();
            }
            throw new BusinessException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        Payment latest = payments.stream()
                .filter(p -> "ZALOPAY".equalsIgnoreCase(p.getPaymentMethod()))
                .reduce((a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b)
                .orElse(payments.get(payments.size() - 1));

        if ("SUCCESS".equalsIgnoreCase(latest.getStatus())) {
            return mapToResponse(latest, order);
        }

        try {
            Map<String, Object> zpResult = zaloPayClient.queryOrder(latest.getTransactionCode());
            Object returnCodeObj = zpResult.get("return_code");
            int returnCode = returnCodeObj instanceof Number ? ((Number) returnCodeObj).intValue() : 0;

            if (returnCode == 1) {
                Object isProcessing = zpResult.get("is_processing");
                Object zpTransId = zpResult.get("zp_trans_id");
                Object amountPaid = zpResult.get("amount");

                if ((isProcessing == null || Boolean.FALSE.equals(isProcessing))
                        && (amountPaid == null || (amountPaid instanceof Number && ((Number) amountPaid).longValue() > 0))) {
                    log.info("ZaloPay query fallback: app_trans_id={} reported as paid", latest.getTransactionCode());
                    latest.setStatus("SUCCESS");
                    latest.setPaidAt(Instant.now());
                    if (zpTransId != null) {
                        latest.setGatewayTransId(String.valueOf(zpTransId));
                    }
                    latest = paymentRepository.save(latest);

                    if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
                        order.setStatus("CONFIRMED");
                        order.setUpdatedAt(Instant.now());
                        orderRepository.save(order);
                    }

                    try {
                        wsBroadcaster.sendPaymentResultToUser(order.getBuyer().getEmail(), order.getOrderCode(),
                                order.getId(), "ZALOPAY", "SUCCESS");
                    } catch (Exception e) {
                        log.warn("[WS] Failed to send PAYMENT_STATUS_CHANGED on query fallback: {}",
                                e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ZaloPay query fallback failed for orderId={}: {}", orderId, e.getMessage());
        }

        return mapToResponse(latest, order);
    }

    @Override
    @Transactional
    public PaymentResponse queryZaloPayPaymentByCode(Long buyerId, String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
        return queryZaloPayPayment(buyerId, order.getId());
    }

    @Override
    @Transactional
    public PaymentResponse confirmCodPaymentByCode(Long buyerId, String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
        return confirmCodPayment(buyerId, order.getId());
    }

    private Order validateBuyerOrder(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
        return order;
    }

    private PaymentResponse mapToResponse(Payment payment, Order order) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .paymentMethod(payment.getPaymentMethod())
                .transactionCode(payment.getTransactionCode())
                .amount(payment.getAmount())
                .paymentUrl(payment.getPaymentUrl())
                .qrCodeUrl(payment.getQrCodeData())
                .status(payment.getStatus())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}