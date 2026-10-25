-- ===================================================================
-- Flash Sale B2C - UTC2 Database Migration V16
-- Enable pgvector extension and create image_embeddings table
-- Phuc vu tinh nang Image Search (Visual Search san pham).
-- ===================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS image_embeddings (
    image_id        BIGINT          PRIMARY KEY,
    product_id      BIGINT          NOT NULL,
    embedding       vector(512)     NOT NULL,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_image_embeddings_image 
        FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE CASCADE,
    CONSTRAINT fk_image_embeddings_product 
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_image_embeddings_product_id 
    ON image_embeddings(product_id);

-- Index HNSW phuc vu tim kiem Approximate Nearest Neighbor (Cosine Distance)
CREATE INDEX IF NOT EXISTS idx_image_embeddings_hnsw 
    ON image_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
