-- Catalog fixtures for H2 schema creation; PostgreSQL tests use Flyway seeds.
INSERT INTO content_categories(code,name,enabled,sort_order,revision,updated_at) SELECT 'BAKERY','Bánh và đồ nướng',TRUE,10,0,CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM content_categories WHERE code='BAKERY');
INSERT INTO content_categories(code,name,enabled,sort_order,revision,updated_at) SELECT 'MEAL','Món ăn',TRUE,20,0,CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM content_categories WHERE code='MEAL');
INSERT INTO content_categories(code,name,enabled,sort_order,revision,updated_at) SELECT 'VEGETARIAN','Món chay',TRUE,30,0,CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM content_categories WHERE code='VEGETARIAN');
INSERT INTO content_categories(code,name,enabled,sort_order,revision,updated_at) SELECT 'DRINK','Đồ uống',TRUE,40,0,CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM content_categories WHERE code='DRINK');
INSERT INTO content_categories(code,name,enabled,sort_order,revision,updated_at) SELECT 'OTHER','Khác',TRUE,50,0,CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM content_categories WHERE code='OTHER');
INSERT INTO content_legal_heads(document) SELECT 'TERMS_OF_USE' WHERE NOT EXISTS (SELECT 1 FROM content_legal_heads WHERE document='TERMS_OF_USE');
INSERT INTO content_legal_heads(document) SELECT 'PRIVACY_POLICY' WHERE NOT EXISTS (SELECT 1 FROM content_legal_heads WHERE document='PRIVACY_POLICY');
