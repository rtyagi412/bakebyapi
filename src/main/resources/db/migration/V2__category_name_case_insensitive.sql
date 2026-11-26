-- Category names are unique ignoring case ("Cakes" and "cakes" are duplicates).
ALTER TABLE category DROP CONSTRAINT uk_category_name;
CREATE UNIQUE INDEX uk_category_name_lower ON category (lower(name));
