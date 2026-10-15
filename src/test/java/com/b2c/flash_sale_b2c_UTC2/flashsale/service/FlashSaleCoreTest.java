package com.b2c.flash_sale_b2c_UTC2.flashsale.service;

import com.b2c.flash_sale_b2c_UTC2.common.exception.BusinessException;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateFlashSaleSlotRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.CreateReservationRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleItemResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.FlashSaleSlotResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.PublicFlashSaleSlotResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.RegisterFlashSaleItemRequest;
import com.b2c.flash_sale_b2c_UTC2.flashsale.dto.ReservationResponse;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleItem;
import com.b2c.flash_sale_b2c_UTC2.flashsale.entity.FlashSaleSlot;
import com.b2c.flash_sale_b2c_UTC2.flashsale.exception.FlashSaleErrorCode;
import com.b2c.flash_sale_b2c_UTC2.flashsale.mapper.FlashSaleItemMapper;
import com.b2c.flash_sale_b2c_UTC2.flashsale.mapper.FlashSaleSlotMapper;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.ExpiredOrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.FlashSaleOrderPort;
import com.b2c.flash_sale_b2c_UTC2.flashsale.port.OrderRef;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleItemRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.repository.FlashSaleSlotRepository;
import com.b2c.flash_sale_b2c_UTC2.flashsale.strategy.StockReservationStrategy;
import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.entity.ProductVariant;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductVariantRepository;
import com.b2c.flash_sale_b2c_UTC2.store.entity.Store;
import com.b2c.flash_sale_b2c_UTC2.store.repository.StoreRepository;
import com.b2c.flash_sale_b2c_UTC2.user.entity.Address;
import com.b2c.flash_sale_b2c_UTC2.user.entity.User;
import com.b2c.flash_sale_b2c_UTC2.user.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashSaleCoreTest {

    @Nested
    @DisplayName("FlashSaleSlotService Tests")
    class SlotServiceTests {

        @Mock
        private FlashSaleSlotRepository slotRepository;

        @Spy
        private FlashSaleSlotMapper slotMapper = Mappers.getMapper(FlashSaleSlotMapper.class);

        @InjectMocks
        private FlashSaleSlotService slotService;

        @Test
        @DisplayName("Tạo slot thành công khi không bị trùng lặp")
        void createSlot_Success() {
            Instant start = Instant.now().plusSeconds(3600);
            Instant end = start.plusSeconds(7200);

            CreateFlashSaleSlotRequest req = CreateFlashSaleSlotRequest.builder()
                    .title("Flash Sale 12h")
                    .startTime(start)
                    .endTime(end)
                    .reservationTtlSeconds(300)
                    .build();

            when(slotRepository.existsOverlappingSlot(null, start, end)).thenReturn(false);
            when(slotRepository.save(any(FlashSaleSlot.class))).thenAnswer(inv -> {
                FlashSaleSlot s = inv.getArgument(0);
                s.setId(1L);
                return s;
            });

            FlashSaleSlotResponse res = slotService.createSlot(req);
            assertNotNull(res);
            assertEquals(1L, res.getId());
            assertEquals("Flash Sale 12h", res.getTitle());
            assertEquals("UPCOMING", res.getStatus());
        }

        @Test
        @DisplayName("Ném lỗi SLOT_OVERLAP khi khung giờ bị trùng")
        void createSlot_OverlapError() {
            Instant start = Instant.now().plusSeconds(3600);
            Instant end = start.plusSeconds(7200);

            CreateFlashSaleSlotRequest req = CreateFlashSaleSlotRequest.builder()
                    .title("Flash Sale 12h")
                    .startTime(start)
                    .endTime(end)
                    .build();

            when(slotRepository.existsOverlappingSlot(null, start, end)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class, () -> slotService.createSlot(req));
            assertEquals(FlashSaleErrorCode.SLOT_OVERLAP, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("FlashSaleItemService Tests")
    class ItemServiceTests {

        @Mock
        private FlashSaleSlotRepository slotRepository;
        @Mock
        private FlashSaleItemRepository itemRepository;
        @Mock
        private ProductVariantRepository variantRepository;
        @Mock
        private StoreRepository storeRepository;
        @Spy
        private FlashSaleItemMapper itemMapper = Mappers.getMapper(FlashSaleItemMapper.class);

        @InjectMocks
        private FlashSaleItemService itemService;

        private Store store;
        private FlashSaleSlot slot;
        private Product product;
        private ProductVariant variant;

        @BeforeEach
        void setUp() {
            User sellerUser = User.builder().id(2L).email("seller@utc2.edu.vn").fullName("Seller").build();
            store = Store.builder().id(10L).user(sellerUser).status("APPROVED").build();
            product = Product.builder().id(100L).store(store).name("Áo Thun").build();
            variant = ProductVariant.builder()
                    .id(200L)
                    .product(product)
                    .sku("AT-DEN-S")
                    .variantName("Đen S")
                    .originalPrice(new BigDecimal("200000.00"))
                    .stockQuantity(50)
                    .status("ACTIVE")
                    .build();

            slot = FlashSaleSlot.builder()
                    .id(1L)
                    .title("Flash Sale")
                    .startTime(Instant.now().plusSeconds(3600))
                    .endTime(Instant.now().plusSeconds(7200))
                    .status("UPCOMING")
                    .build();
        }

        @Test
        @DisplayName("Seller nộp đăng ký Flash Sale Item thành công")
        void registerItem_Success() {
            RegisterFlashSaleItemRequest req = RegisterFlashSaleItemRequest.builder()
                    .slotId(1L)
                    .variantId(200L)
                    .flashSalePrice(new BigDecimal("150000.00"))
                    .allocatedStock(20)
                    .userPurchaseLimit(2)
                    .build();

            when(storeRepository.findByUserId(2L)).thenReturn(Optional.of(store));
            when(slotRepository.findById(1L)).thenReturn(Optional.of(slot));
            when(variantRepository.findById(200L)).thenReturn(Optional.of(variant));
            when(itemRepository.existsBySlotIdAndVariantId(1L, 200L)).thenReturn(false);
            when(itemRepository.save(any(FlashSaleItem.class))).thenAnswer(inv -> {
                FlashSaleItem it = inv.getArgument(0);
                it.setId(500L);
                return it;
            });

            FlashSaleItemResponse res = itemService.registerItem(2L, req);
            assertNotNull(res);
            assertEquals(500L, res.getId());
            assertEquals(20, res.getAllocatedStock());
            assertEquals("PENDING_APPROVAL", res.getStatus());
        }

        @Test
        @DisplayName("Admin duyệt Flash Sale Item và trừ kho gốc nguyên tử thành công")
        void approveItem_Success() {
            FlashSaleItem item = FlashSaleItem.builder()
                    .id(500L)
                    .slot(slot)
                    .variant(variant)
                    .flashSalePrice(new BigDecimal("150000.00"))
                    .allocatedStock(20)
                    .availableStock(20)
                    .status("PENDING_APPROVAL")
                    .build();

            when(itemRepository.findById(500L)).thenReturn(Optional.of(item));
            when(variantRepository.deductStockQuantityConditionally(200L, 20)).thenReturn(1);
            when(itemRepository.save(any(FlashSaleItem.class))).thenReturn(item);

            FlashSaleItemResponse res = itemService.approveItem(500L);
            assertNotNull(res);
            assertEquals("APPROVED", res.getStatus());
            assertEquals(20, res.getAvailableStock());
            verify(variantRepository).deductStockQuantityConditionally(200L, 20);
        }

        @Test
        @DisplayName("Admin duyệt thất bại khi tồn kho gốc không đủ (deduct trả về 0)")
        void approveItem_InsufficientBaseStock() {
            FlashSaleItem item = FlashSaleItem.builder()
                    .id(500L)
                    .slot(slot)
                    .variant(variant)
                    .allocatedStock(100) // lớn hơn tồn kho 50
                    .status("PENDING_APPROVAL")
                    .build();

            when(itemRepository.findById(500L)).thenReturn(Optional.of(item));
            when(variantRepository.deductStockQuantityConditionally(200L, 100)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class, () -> itemService.approveItem(500L));
            assertEquals(FlashSaleErrorCode.INSUFFICIENT_BASE_STOCK, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("FlashSaleReservationService & Compensation Tests")
    class ReservationServiceTests {

        @Mock
        private FlashSaleItemRepository itemRepository;
        @Mock
        private AddressRepository addressRepository;
        @Mock
        private StockReservationStrategy stockReservationStrategy;
        @Mock
        private FlashSaleOrderPort orderPort;

        @InjectMocks
        private FlashSaleReservationService reservationService;

        private User buyer;
        private Address address;
        private FlashSaleSlot activeSlot;
        private FlashSaleItem approvedItem;

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(reservationService, "orderPort", orderPort);

            buyer = User.builder().id(99L).email("buyer@utc2.edu.vn").fullName("Buyer").build();
            address = Address.builder().id(101L).user(buyer).contactName("Buyer").build();

            activeSlot = FlashSaleSlot.builder()
                    .id(1L)
                    .title("Flash Sale Active")
                    .startTime(Instant.now().minusSeconds(100))
                    .endTime(Instant.now().plusSeconds(3500))
                    .reservationTtlSeconds(300)
                    .status("ACTIVE")
                    .build();

            Store store = Store.builder().id(10L).build();
            Product product = Product.builder().id(100L).name("Áo Polo").store(store).build();
            ProductVariant variant = ProductVariant.builder().id(200L).product(product).variantName("Đen").build();

            approvedItem = FlashSaleItem.builder()
                    .id(500L)
                    .slot(activeSlot)
                    .variant(variant)
                    .flashSalePrice(new BigDecimal("99000.00"))
                    .allocatedStock(10)
                    .availableStock(10)
                    .userPurchaseLimit(1)
                    .status("APPROVED")
                    .build();
        }

        @Test
        @DisplayName("Đặt giữ chỗ thành công")
        void createReservation_Success() {
            CreateReservationRequest req = CreateReservationRequest.builder()
                    .flashSaleItemId(500L)
                    .addressId(101L)
                    .quantity(1)
                    .build();

            when(addressRepository.findById(101L)).thenReturn(Optional.of(address));
            when(itemRepository.findById(500L)).thenReturn(Optional.of(approvedItem));
            when(stockReservationStrategy.reserveStock(eq(1L), eq(99L), eq(500L), eq(1), eq(1), anyLong(), anyString()))
                    .thenReturn(1L);
            when(itemRepository.deductAvailableStockConditionally(500L, 1)).thenReturn(1);
            when(orderPort.createPendingOrder(any())).thenReturn(new OrderRef(1001L, "FS-ORDER-1001"));

            ReservationResponse res = reservationService.createReservation(99L, "idem-key-1", req);
            assertNotNull(res);
            assertEquals(1001L, res.getOrderId());
            assertEquals("FS-ORDER-1001", res.getOrderCode());
            assertEquals("PENDING_PAYMENT", res.getStatus());
        }

        @Test
        @DisplayName("Hết hàng tồn kho ném OUT_OF_STOCK")
        void createReservation_OutOfStock() {
            CreateReservationRequest req = CreateReservationRequest.builder()
                    .flashSaleItemId(500L)
                    .addressId(101L)
                    .quantity(1)
                    .build();

            when(addressRepository.findById(101L)).thenReturn(Optional.of(address));
            when(itemRepository.findById(500L)).thenReturn(Optional.of(approvedItem));
            when(stockReservationStrategy.reserveStock(eq(1L), eq(99L), eq(500L), eq(1), eq(1), anyLong(), anyString()))
                    .thenReturn(0L); // out of stock

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(99L, "idem-key-2", req));
            assertEquals(FlashSaleErrorCode.OUT_OF_STOCK, ex.getErrorCode());
        }

        @Test
        @DisplayName("Vượt quá giới hạn mua ném PURCHASE_LIMIT_EXCEEDED")
        void createReservation_LimitExceeded() {
            CreateReservationRequest req = CreateReservationRequest.builder()
                    .flashSaleItemId(500L)
                    .addressId(101L)
                    .quantity(2) // limit is 1
                    .build();

            when(addressRepository.findById(101L)).thenReturn(Optional.of(address));
            when(itemRepository.findById(500L)).thenReturn(Optional.of(approvedItem));
            when(stockReservationStrategy.reserveStock(eq(1L), eq(99L), eq(500L), eq(2), eq(1), anyLong(), anyString()))
                    .thenReturn(-1L); // limit exceeded

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(99L, "idem-key-3", req));
            assertEquals(FlashSaleErrorCode.PURCHASE_LIMIT_EXCEEDED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Dual-write compensation: Khi OrderPort tạo đơn thất bại, tự động bù hoàn kho Redis và DB")
        void createReservation_DualWriteFailure_TriggersCompensation() {
            CreateReservationRequest req = CreateReservationRequest.builder()
                    .flashSaleItemId(500L)
                    .addressId(101L)
                    .quantity(1)
                    .build();

            when(addressRepository.findById(101L)).thenReturn(Optional.of(address));
            when(itemRepository.findById(500L)).thenReturn(Optional.of(approvedItem));
            when(stockReservationStrategy.reserveStock(eq(1L), eq(99L), eq(500L), eq(1), eq(1), anyLong(), anyString()))
                    .thenReturn(1L);
            when(itemRepository.deductAvailableStockConditionally(500L, 1)).thenReturn(1);
            when(orderPort.createPendingOrder(any())).thenThrow(new RuntimeException("DB Connection timeout!"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reservationService.createReservation(99L, "idem-key-4", req));
            assertEquals(FlashSaleErrorCode.ORDER_CREATION_FAILED, ex.getErrorCode());

            // Xác minh bù hoàn
            verify(stockReservationStrategy).rollbackStockWithMarker(eq(1L), eq(99L), eq(500L), eq(1), anyString());
            verify(itemRepository).replenishAvailableStockConditionally(500L, 1);
        }
    }

    @Nested
    @DisplayName("FlashSaleExpirationScheduler Tests")
    class SchedulerTests {

        @Mock
        private FlashSaleSlotRepository slotRepository;
        @Mock
        private FlashSaleItemRepository itemRepository;
        @Mock
        private ProductVariantRepository variantRepository;
        @Mock
        private StockReservationStrategy stockReservationStrategy;
        @Mock
        private FlashSaleOrderPort orderPort;

        @InjectMocks
        private FlashSaleExpirationScheduler scheduler;

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(scheduler, "orderPort", orderPort);
        }

        @Test
        @DisplayName("Quét đơn timeout: hoàn trả kho đúng số lượng đã giữ chỗ khi hủy thành công")
        void processExpiredReservations_Success() {
            ExpiredOrderRef ref = new ExpiredOrderRef(1001L, "FS-TEST-001", 500L, 1L, 99L, 3);
            when(orderPort.lockExpiredPendingOrders(any(), eq(100))).thenReturn(List.of(ref));
            when(orderPort.cancelTimeoutIfPending(1001L)).thenReturn(true);

            scheduler.processExpiredReservations();

            verify(stockReservationStrategy).compensate(1L, 99L, 500L, 3);
            verify(itemRepository).replenishAvailableStockConditionally(500L, 3);
        }

        @Test
        @DisplayName("Quét đơn timeout: không hoàn trả kho nếu đơn đã thanh toán hoặc đã hủy trước đó (cancel trả về false)")
        void processExpiredReservations_AlreadyHandled() {
            ExpiredOrderRef ref = new ExpiredOrderRef(1001L, "FS-TEST-001", 500L, 1L, 99L, 3);
            when(orderPort.lockExpiredPendingOrders(any(), eq(100))).thenReturn(List.of(ref));
            when(orderPort.cancelTimeoutIfPending(1001L)).thenReturn(false);

            scheduler.processExpiredReservations();

            verify(stockReservationStrategy, never()).compensate(anyLong(), anyLong(), anyLong(), anyInt());
            verify(itemRepository, never()).replenishAvailableStockConditionally(anyLong(), anyInt());
        }

        @Test
        @DisplayName("Đóng slot hết hạn: tự động hoàn tồn kho ế (available_stock) về kho gốc variant")
        void closeSlotAndReturnStock_Success() {
            ProductVariant variant = ProductVariant.builder().id(200L).stockQuantity(30).build();
            FlashSaleSlot slot = FlashSaleSlot.builder().id(1L).status("ACTIVE").build();
            FlashSaleItem item = FlashSaleItem.builder()
                    .id(500L)
                    .slot(slot)
                    .variant(variant)
                    .allocatedStock(20)
                    .availableStock(15) // còn 15 cái chưa bán được
                    .status("APPROVED")
                    .build();

            when(orderPort.countPendingBySlot(1L)).thenReturn(0L);
            when(itemRepository.findBySlotId(1L)).thenReturn(List.of(item));

            scheduler.closeSlotAndReturnStock(slot);

            verify(variantRepository).replenishStockQuantityConditionally(200L, 15);
            assertEquals("ENDED", item.getStatus());
            assertEquals("ENDED", slot.getStatus());
        }
    }
}
