-- Supports newest-first browsing without loading all active deals into application memory.
CREATE INDEX idx_deals_global_feed_created
    ON deals (created_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND remaining_quantity > 0;
