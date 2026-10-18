-- V13__add_is_stock_rolled_back_to_flash_sale_slots.sql
-- Thêm cờ đánh dấu đã hoàn kho khi emergency pause hoặc slot close để chống hoàn kho 2 lần
ALTER TABLE flash_sale_slots ADD COLUMN is_stock_rolled_back BOOLEAN DEFAULT FALSE;
