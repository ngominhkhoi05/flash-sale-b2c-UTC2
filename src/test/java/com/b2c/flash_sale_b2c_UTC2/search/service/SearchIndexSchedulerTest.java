package com.b2c.flash_sale_b2c_UTC2.search.service;

import com.b2c.flash_sale_b2c_UTC2.product.entity.Product;
import com.b2c.flash_sale_b2c_UTC2.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchIndexSchedulerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductIndexer productIndexer;

    @InjectMocks
    private SearchIndexScheduler scheduler;

    private Product product(Long id, Instant updatedAt) {
        Product p = Product.builder().id(id).status("ACTIVE").updatedAt(updatedAt).build();
        return p;
    }

    @Test
    @DisplayName("reconcile: reconcileEnabled=false -> khong quet san pham")
    void reconcile_disabled_doesNothing() {
        ReflectionTestUtils.setField(scheduler, "reconcileEnabled", false);

        scheduler.reconcile();

        verify(productRepository, never()).findActiveUpdatedAfter(any(), any());
        verify(productIndexer, never()).indexProducts(anyList());
    }

    @Test
    @DisplayName("reconcile: quet 1 batch roi dung -> index cac san pham thay doi")
    void reconcile_indexesChangedProducts() {
        ReflectionTestUtils.setField(scheduler, "reconcileEnabled", true);
        when(productRepository.findActiveUpdatedAfter(any(), any(Pageable.class)))
                .thenReturn(List.of(product(1L, Instant.parse("2026-01-01T00:00:00Z")),
                        product(2L, Instant.parse("2026-01-02T00:00:00Z"))));
        when(productIndexer.indexProducts(anyList())).thenReturn(2);

        scheduler.reconcile();

        verify(productIndexer).indexProducts(List.of(1L, 2L));
    }

    @Test
    @DisplayName("reconcile: repository nem loi -> khong nem exception ra ngoai")
    void reconcile_repositoryThrows_doesNotPropagate() {
        ReflectionTestUtils.setField(scheduler, "reconcileEnabled", true);
        when(productRepository.findActiveUpdatedAfter(any(), any(Pageable.class)))
                .thenThrow(new RuntimeException("db down"));

        scheduler.reconcile();

        verify(productIndexer, never()).indexProducts(anyList());
    }

    @Test
    @DisplayName("reconcile: batch rong -> khong index gi")
    void reconcile_emptyBatch_doesNotIndex() {
        ReflectionTestUtils.setField(scheduler, "reconcileEnabled", true);
        when(productRepository.findActiveUpdatedAfter(any(), any(Pageable.class))).thenReturn(List.of());

        scheduler.reconcile();

        verify(productIndexer, never()).indexProducts(anyList());
    }

    @Test
    @DisplayName("fullReindex: index tat ca san pham ACTIVE theo batch")
    void fullReindex_indexesAllActiveProducts() {
        when(productRepository.findAllActiveWithStoreAndCategory(any(Pageable.class)))
                .thenReturn(List.of(product(1L, Instant.parse("2026-01-01T00:00:00Z"))));
        when(productIndexer.indexProducts(anyList())).thenReturn(1);

        int total = scheduler.fullReindex();

        assertEquals(1, total);
        verify(productIndexer).indexProducts(List.of(1L));
    }

    @Test
    @DisplayName("fullReindex: nhieu batch -> tong hop so san pham da index")
    void fullReindex_sumsAllBatches() {
        List<Product> fullBatch = new java.util.ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            fullBatch.add(product(i, Instant.parse("2026-01-01T00:00:00Z")));
        }
        when(productRepository.findAllActiveWithStoreAndCategory(any(Pageable.class)))
                .thenReturn(fullBatch)
                .thenReturn(List.of(product(501L, Instant.parse("2026-01-02T00:00:00Z"))));
        when(productIndexer.indexProducts(anyList())).thenReturn(500).thenReturn(1);

        int total = scheduler.fullReindex();

        assertEquals(501, total);
        verify(productIndexer, times(2)).indexProducts(anyList());
    }

    @Test
    @DisplayName("fullReindex: khong co san pham -> tra 0")
    void fullReindex_noProducts_returnsZero() {
        when(productRepository.findAllActiveWithStoreAndCategory(any(Pageable.class))).thenReturn(List.of());

        assertEquals(0, scheduler.fullReindex());
        verify(productIndexer, never()).indexProducts(anyList());
    }

    @Test
    @DisplayName("fullReindex: repository nem loi -> nem exception, tra 0")
    void fullReindex_repositoryThrows_returnsZero() {
        when(productRepository.findAllActiveWithStoreAndCategory(any(Pageable.class)))
                .thenThrow(new RuntimeException("db down"));

        assertEquals(0, scheduler.fullReindex());
        verify(productIndexer, never()).indexProducts(anyList());
    }

    @Test
    @DisplayName("fullReindex: phan trang tang dan theo page")
    void fullReindex_paginatesByPage() {
        when(productRepository.findAllActiveWithStoreAndCategory(any(Pageable.class)))
                .thenReturn(List.of(product(1L, Instant.parse("2026-01-01T00:00:00Z"))));
        when(productIndexer.indexProducts(anyList())).thenReturn(1);

        scheduler.fullReindex();

        verify(productRepository).findAllActiveWithStoreAndCategory(eq(PageRequest.of(0, 500)));
    }
}
