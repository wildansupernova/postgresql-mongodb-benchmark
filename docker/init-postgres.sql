-- For Scenario 1 & 3 (JSONB)
CREATE TABLE IF NOT EXISTS orders_jsonb (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB,
    items JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX idx_orders_jsonb_items ON orders_jsonb USING GIN (items);
CREATE INDEX idx_orders_jsonb_status ON orders_jsonb(status);

-- For Scenario 2 & 4 (Normalized)
CREATE TABLE IF NOT EXISTS orders_normalized (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata JSONB
);

CREATE TABLE IF NOT EXISTS items_normalized (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders_normalized(id) ON DELETE CASCADE,
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    tags TEXT[],
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_items_order_id ON items_normalized(order_id);
CREATE INDEX idx_items_status ON items_normalized(status);
CREATE INDEX idx_items_product_sku ON items_normalized(product_sku);

-- Trigger to update order amount when items change
CREATE OR REPLACE FUNCTION update_order_amount()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE orders_normalized
    SET amount = (
        SELECT COALESCE(SUM(amount), 0)
        FROM items_normalized
        WHERE order_id = COALESCE(NEW.order_id, OLD.order_id)
    ),
    updated_at = NOW()
    WHERE id = COALESCE(NEW.order_id, OLD.order_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_order_amount
AFTER INSERT OR UPDATE OR DELETE ON items_normalized
FOR EACH ROW
EXECUTE FUNCTION update_order_amount();
