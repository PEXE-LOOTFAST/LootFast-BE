-- Merchants may choose any discount. The only price rule left is that a deal
-- (and a deal template) costs less than the original price; the previous
-- checks required at least 10% off.
ALTER TABLE deals DROP CONSTRAINT chk_deals_prices;
ALTER TABLE deals ADD CONSTRAINT chk_deals_prices CHECK (
    original_price > 0
    AND discounted_price > 0
    AND discounted_price < original_price
);

ALTER TABLE deal_templates DROP CONSTRAINT chk_deal_templates_prices;
ALTER TABLE deal_templates ADD CONSTRAINT chk_deal_templates_prices CHECK (
    original_price > 0
    AND discounted_price > 0
    AND discounted_price < original_price
);
