package com.b2c.flash_sale_b2c_UTC2.reconciliation.service;

import com.b2c.flash_sale_b2c_UTC2.common.api.PageResponse;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.LockReconciliationRequest;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.ReconciliationKpisResponse;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.ReconciliationOrderResponse;
import com.b2c.flash_sale_b2c_UTC2.reconciliation.dto.ReconciliationRefundResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReconciliationService {

    ReconciliationKpisResponse getReconciliationKpis(String period);

    PageResponse<ReconciliationOrderResponse> getReconciliationOrders(
            String search,
            Long storeId,
            String paymentMethod,
            String status,
            String period,
            Pageable pageable
    );

    List<ReconciliationRefundResponse> getReconciliationRefunds(String search, String period);

    void lockReconciliationPeriod(LockReconciliationRequest request);
}
