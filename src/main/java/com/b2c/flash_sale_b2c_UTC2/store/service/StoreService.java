package com.b2c.flash_sale_b2c_UTC2.store.service;

import com.b2c.flash_sale_b2c_UTC2.store.dto.CreateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.FeaturedStoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.SidebarSummaryResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.StoreResponse;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreRequest;
import com.b2c.flash_sale_b2c_UTC2.store.dto.UpdateStoreStatusRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.AddressResponse;
import com.b2c.flash_sale_b2c_UTC2.user.dto.CreateAddressRequest;
import com.b2c.flash_sale_b2c_UTC2.user.dto.UpdateAddressRequest;

import java.util.List;

public interface StoreService {

    StoreResponse registerStore(Long userId, CreateStoreRequest request);

    StoreResponse getMyStore(Long userId);

    StoreResponse updateMyStore(Long userId, UpdateStoreRequest request);

    StoreResponse getStoreById(Long storeId);

    StoreResponse updateStoreStatus(Long storeId, UpdateStoreStatusRequest request);

    /**
     * Get featured stores for homepage (Vibe Mall section).
     * Returns APPROVED stores ordered by createdAt DESC.
     *
     * @param limit max number of stores to return (clamped to 1..8)
     * @return list of FeaturedStoreResponse
     */
    List<FeaturedStoreResponse> getFeaturedStores(int limit);

    List<AddressResponse> getStoreAddresses(Long userId);

    AddressResponse createStoreAddress(Long userId, CreateAddressRequest request);

    AddressResponse updateStoreAddress(Long userId, Long addressId, UpdateAddressRequest request);

    void deleteStoreAddress(Long userId, Long addressId);

    AddressResponse setDefaultStoreAddress(Long userId, Long addressId);

    /**
     * Get aggregated metrics for seller dashboard sidebar.
     *
     * @param userId current authenticated user ID
     * @return SidebarSummaryResponse with all aggregated metrics
     */
    SidebarSummaryResponse getSidebarSummary(Long userId);
}
