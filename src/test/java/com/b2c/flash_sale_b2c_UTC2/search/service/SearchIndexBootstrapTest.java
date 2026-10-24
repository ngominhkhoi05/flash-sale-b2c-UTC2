package com.b2c.flash_sale_b2c_UTC2.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchIndexBootstrapTest {

    @Mock
    private SearchIndexService searchIndexService;

    @InjectMocks
    private SearchIndexBootstrap bootstrap;

    @Test
    @DisplayName("onApplicationReady: dam bao index + alias ton tai (idempotent)")
    void onApplicationReady_ensuresIndex() {
        bootstrap.onApplicationReady();
        verify(searchIndexService).ensureIndexExists();
    }

    @Test
    @DisplayName("onApplicationReady: goi ensureIndexExists dung mot lan")
    void onApplicationReady_ensuresIndexOnce() {
        bootstrap.onApplicationReady();
        verify(searchIndexService, times(1)).ensureIndexExists();
    }
}
