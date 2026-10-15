package com.b2c.flash_sale_b2c_UTC2.payment.controller;

import com.b2c.flash_sale_b2c_UTC2.payment.dto.ZaloPayCallbackDto;
import com.b2c.flash_sale_b2c_UTC2.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@Tag(name = "ZaloPay Callback", description = "Public callback from ZaloPay server (signed by MAC)")
@RestController
@RequestMapping(value = "/api/v1/payments/zalopay", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ZaloPayCallbackController {

    private final PaymentService paymentService;

    @Operation(summary = "Callback from ZaloPay server when user completes payment",
               description = "PUBLIC endpoint (no JWT). ZaloPay POSTs to this URL after payment. Verify via MAC signature.")
    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody ZaloPayCallbackDto cb) {
        log.info("ZaloPay callback received: type={}", cb.getType());
        Map<String, Object> result = paymentService.handleZaloPayCallback(cb);
        return ResponseEntity.ok(result);
    }
}