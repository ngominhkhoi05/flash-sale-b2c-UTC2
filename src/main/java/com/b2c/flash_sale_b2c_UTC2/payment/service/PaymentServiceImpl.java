package com.b2c.flash_sale_b2c_UTC2.payment.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.realtime.FlashSaleWsBroadcaster;
import com.b2c.flash_sale_b2c_UTC2.order.entity.Order;
import com.b2c.flash_sale_b2c_UTC2.order.exception.OrderErrorCode;
import com.b2c.flash_sale_b2c_UTC2.order.repository.OrderRepository;
import com.b2c.flash_sale_b2c_UTC2.payment.dto.PaymentResponse;
import com.b2c.flash_sale_b2c_UTC2.payment.entity.Payment;
import com.b2c.flash_sale_b2c_UTC2.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    // WebSocket broadcaster — optional, gracefully no-op if null
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
    @Transactional
    public PaymentResponse createZaloPayPayment(Long buyerId, Long orderId) {
        Order order = validateBuyerOrder(buyerId, orderId);

        if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        String transactionCode = "ZP_" + order.getOrderCode() + "_" + System.currentTimeMillis();
        String paymentUrl = "https://qc.zalopay.vn/pay?order=" + order.getOrderCode() + "&amount=" + order.getTotalAmount();
        String qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=" + URLEncoder.encode(paymentUrl, StandardCharsets.UTF_8);

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod("ZALOPAY")
                .transactionCode(transactionCode)
                .amount(order.getTotalAmount())
                .paymentUrl(paymentUrl)
                .qrCodeData(qrCodeUrl)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Created ZaloPay payment intent for orderId={}, transactionCode={}", orderId, transactionCode);

        // Push payment result to buyer via WebSocket
        try {
            String buyerUsername = order.getBuyer().getEmail();
            wsBroadcaster.sendPaymentResultToUser(buyerUsername, order.getOrderCode(),
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

        // Chuyen trang thai don hang thanh CONFIRMED sau khi chon COD
        order.setStatus("CONFIRMED");
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        log.info("Confirmed COD payment for orderId={}, order transitioned to CONFIRMED", orderId);

        // Push payment result to buyer via WebSocket
        try {
            String buyerUsername = order.getBuyer().getEmail();
            wsBroadcaster.sendPaymentResultToUser(buyerUsername, order.getOrderCode(),
                    order.getId(), "COD", "CONFIRMED");
        } catch (Exception e) {
            log.warn("[WS] Failed to send PAYMENT_STATUS_CHANGED for orderId={}: {}", orderId, e.getMessage());
        }

        return mapToResponse(payment, order);
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
