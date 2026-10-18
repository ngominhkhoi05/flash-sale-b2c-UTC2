package com.b2c.flash_sale_b2c_UTC2.wallet.mapper;

import com.b2c.flash_sale_b2c_UTC2.wallet.dto.WalletTransactionResponse;
import com.b2c.flash_sale_b2c_UTC2.wallet.entity.WalletTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WalletTransactionMapper {

    @Mapping(target = "orderCode", source = "order.orderCode")
    @Mapping(target = "type", source = "transactionType")
    @Mapping(target = "transactionCode", expression = "java(generateTransactionCode(transaction))")
    @Mapping(target = "status", constant = "SUCCESS")
    WalletTransactionResponse toResponse(WalletTransaction transaction);

    default String generateTransactionCode(WalletTransaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            return "TXN-" + System.currentTimeMillis();
        }
        return String.format("TXN-%06d", transaction.getId());
    }
}
