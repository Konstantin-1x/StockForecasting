-- Repairs a data-only PostgreSQL import so the Spring/JPA application can use it
-- as the live stockforecasting database.
--
-- The imported archive may contain rows, identity columns and secondary indexes
-- but no primary/foreign key constraints. This script restores schema metadata
-- without changing business data. It is safe to run more than once.

set client_min_messages to warning;

do $$
begin
    if not exists (select 1 from pg_constraint where conrelid = 'app_users'::regclass and contype = 'p') then
        alter table app_users add constraint app_users_pkey primary key (user_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'sellers'::regclass and contype = 'p') then
        alter table sellers add constraint sellers_pkey primary key (seller_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'product_categories'::regclass and contype = 'p') then
        alter table product_categories add constraint product_categories_pkey primary key (category_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'products'::regclass and contype = 'p') then
        alter table products add constraint products_pkey primary key (product_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'tracked_marketplace_products'::regclass and contype = 'p') then
        alter table tracked_marketplace_products add constraint tracked_marketplace_products_pkey primary key (tracked_product_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'marketplace_product_snapshots'::regclass and contype = 'p') then
        alter table marketplace_product_snapshots add constraint marketplace_product_snapshots_pkey primary key (snapshot_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'marketplace_monitoring_jobs'::regclass and contype = 'p') then
        alter table marketplace_monitoring_jobs add constraint marketplace_monitoring_jobs_pkey primary key (monitoring_job_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'competitor_offers'::regclass and contype = 'p') then
        alter table competitor_offers add constraint competitor_offers_pkey primary key (competitor_offer_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'promotion_forecasts'::regclass and contype = 'p') then
        alter table promotion_forecasts add constraint promotion_forecasts_pkey primary key (forecast_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'promotions'::regclass and contype = 'p') then
        alter table promotions add constraint promotions_pkey primary key (promotion_id);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'landing_page_content'::regclass and contype = 'p') then
        alter table landing_page_content add constraint landing_page_content_pkey primary key (content_id);
    end if;
end $$;

select setval(pg_get_serial_sequence('app_users', 'user_id'), greatest(coalesce((select max(user_id) from app_users), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('sellers', 'seller_id'), greatest(coalesce((select max(seller_id) from sellers), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('product_categories', 'category_id'), greatest(coalesce((select max(category_id) from product_categories), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('products', 'product_id'), greatest(coalesce((select max(product_id) from products), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('tracked_marketplace_products', 'tracked_product_id'), greatest(coalesce((select max(tracked_product_id) from tracked_marketplace_products), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('marketplace_product_snapshots', 'snapshot_id'), greatest(coalesce((select max(snapshot_id) from marketplace_product_snapshots), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('marketplace_monitoring_jobs', 'monitoring_job_id'), greatest(coalesce((select max(monitoring_job_id) from marketplace_monitoring_jobs), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('competitor_offers', 'competitor_offer_id'), greatest(coalesce((select max(competitor_offer_id) from competitor_offers), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('promotion_forecasts', 'forecast_id'), greatest(coalesce((select max(forecast_id) from promotion_forecasts), 0) + 1, 1), false);
select setval(pg_get_serial_sequence('promotions', 'promotion_id'), greatest(coalesce((select max(promotion_id) from promotions), 0) + 1, 1), false);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_app_users_seller') then
        alter table app_users add constraint fk_app_users_seller foreign key (seller_id) references sellers(seller_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_competitor_offers_category') then
        alter table competitor_offers add constraint fk_competitor_offers_category foreign key (category_id) references product_categories(category_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_marketplace_monitoring_jobs_tracked_product') then
        alter table marketplace_monitoring_jobs add constraint fk_marketplace_monitoring_jobs_tracked_product foreign key (tracked_product_id) references tracked_marketplace_products(tracked_product_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_marketplace_product_snapshots_tracked_product') then
        alter table marketplace_product_snapshots add constraint fk_marketplace_product_snapshots_tracked_product foreign key (tracked_product_id) references tracked_marketplace_products(tracked_product_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_product_categories_parent') then
        alter table product_categories add constraint fk_product_categories_parent foreign key (parent_category_id) references product_categories(category_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_products_category') then
        alter table products add constraint fk_products_category foreign key (category_id) references product_categories(category_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_products_seller') then
        alter table products add constraint fk_products_seller foreign key (seller_id) references sellers(seller_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_promotion_forecasts_product') then
        alter table promotion_forecasts add constraint fk_promotion_forecasts_product foreign key (product_id) references products(product_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_promotion_forecasts_tracked_product') then
        alter table promotion_forecasts add constraint fk_promotion_forecasts_tracked_product foreign key (tracked_product_id) references tracked_marketplace_products(tracked_product_id);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'fk_promotions_product') then
        alter table promotions add constraint fk_promotions_product foreign key (product_id) references products(product_id);
    end if;
end $$;
