<?php
declare(strict_types=1);

namespace DXN;

/**
 * Database schema + seed data.
 *
 * Every statement is written so it runs unchanged on MySQL/MariaDB (production,
 * InfinityFree) and on SQLite (local development, CI). Only the primary key
 * syntax and the table suffix differ, and those are resolved here.
 */
final class Schema
{
    public static function install(bool $seed = true): array
    {
        $pdo = Database::pdo();
        $driver = Config::isSqlite() ? 'sqlite' : 'mysql';

        foreach (self::statements($driver) as $sql) {
            try {
                $pdo->exec($sql);
            } catch (\PDOException $e) {
                // Re-running the installer must never be fatal: tables and
                // indexes that already exist simply skip.
                $message = strtolower($e->getMessage());
                $ignorable = str_contains($message, 'already exists')
                    || str_contains($message, 'duplicate key name')
                    || str_contains($message, 'duplicate column name');
                if (!$ignorable) {
                    throw $e;
                }
            }
        }

        self::ensureCounter();

        if ($seed) {
            self::seed();
        }

        return ['driver' => $driver, 'tables' => self::tableNames()];
    }

    /** @return array<int,string> */
    public static function statements(string $driver): array
    {
        $pk = $driver === 'sqlite'
            ? 'INTEGER PRIMARY KEY AUTOINCREMENT'
            : 'INT NOT NULL AUTO_INCREMENT PRIMARY KEY';

        $suffix = $driver === 'sqlite' ? '' : ' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci';

        return [
            "CREATE TABLE IF NOT EXISTS sync_counter (
                id    INTEGER NOT NULL PRIMARY KEY,
                value BIGINT  NOT NULL DEFAULT 0
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS products (
                id            {$pk},
                sku           VARCHAR(64)  NOT NULL,
                name          VARCHAR(160) NOT NULL,
                category      VARCHAR(64)  NOT NULL DEFAULT 'General',
                description   TEXT         NULL,
                retail_price  DECIMAL(12,2) NOT NULL DEFAULT 0,
                member_price  DECIMAL(12,2) NOT NULL DEFAULT 0,
                pv_points     DECIMAL(12,2) NOT NULL DEFAULT 0,
                stock_qty     INTEGER      NOT NULL DEFAULT 0,
                low_stock_at  INTEGER      NOT NULL DEFAULT 5,
                image_url     VARCHAR(255) NULL,
                is_active     INTEGER      NOT NULL DEFAULT 1,
                created_at    DATETIME     NOT NULL,
                updated_at    DATETIME     NOT NULL,
                rev           BIGINT       NOT NULL DEFAULT 0
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS customers (
                id            {$pk},
                name          VARCHAR(160) NOT NULL,
                phone         VARCHAR(40)  NULL,
                dxn_member_id VARCHAR(40)  NULL,
                is_member     INTEGER      NOT NULL DEFAULT 0,
                total_orders  INTEGER      NOT NULL DEFAULT 0,
                total_spent   DECIMAL(12,2) NOT NULL DEFAULT 0,
                total_pv      DECIMAL(12,2) NOT NULL DEFAULT 0,
                last_order_at DATETIME     NULL,
                created_at    DATETIME     NOT NULL,
                updated_at    DATETIME     NOT NULL,
                rev           BIGINT       NOT NULL DEFAULT 0
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS orders (
                id             {$pk},
                client_uuid    VARCHAR(64)  NULL,
                order_number   VARCHAR(40)  NOT NULL,
                customer_id    INTEGER      NULL,
                customer_name  VARCHAR(160) NOT NULL,
                customer_phone VARCHAR(40)  NULL,
                dxn_member_id  VARCHAR(40)  NULL,
                is_member      INTEGER      NOT NULL DEFAULT 0,
                subtotal       DECIMAL(12,2) NOT NULL DEFAULT 0,
                discount       DECIMAL(12,2) NOT NULL DEFAULT 0,
                total_amount   DECIMAL(12,2) NOT NULL DEFAULT 0,
                total_pv       DECIMAL(12,2) NOT NULL DEFAULT 0,
                status         VARCHAR(24)  NOT NULL DEFAULT 'CONFIRMED',
                payment_method VARCHAR(24)  NOT NULL DEFAULT 'CASH',
                note           TEXT         NULL,
                source         VARCHAR(24)  NOT NULL DEFAULT 'web',
                created_at     DATETIME     NOT NULL,
                updated_at     DATETIME     NOT NULL,
                rev            BIGINT       NOT NULL DEFAULT 0
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS order_items (
                id         {$pk},
                order_id   INTEGER      NOT NULL,
                product_id INTEGER      NOT NULL,
                sku        VARCHAR(64)  NOT NULL DEFAULT '',
                name       VARCHAR(160) NOT NULL DEFAULT '',
                quantity   INTEGER      NOT NULL DEFAULT 1,
                unit_price DECIMAL(12,2) NOT NULL DEFAULT 0,
                unit_pv    DECIMAL(12,2) NOT NULL DEFAULT 0
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS stock_movements (
                id           {$pk},
                product_id   INTEGER      NOT NULL,
                delta        INTEGER      NOT NULL,
                quantity_after INTEGER    NOT NULL,
                reason       VARCHAR(64)  NOT NULL DEFAULT 'adjustment',
                order_id     INTEGER      NULL,
                created_at   DATETIME     NOT NULL
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS settings (
                setting_key   VARCHAR(64) NOT NULL PRIMARY KEY,
                setting_value TEXT        NULL,
                updated_at    DATETIME    NOT NULL,
                rev           BIGINT      NOT NULL DEFAULT 0
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS auth_attempts (
                id           {$pk},
                ip           VARCHAR(64) NOT NULL,
                ok           INTEGER     NOT NULL DEFAULT 0,
                attempted_at DATETIME    NOT NULL
            ){$suffix}",

            "CREATE TABLE IF NOT EXISTS audit_log (
                id         {$pk},
                action     VARCHAR(64) NOT NULL,
                entity     VARCHAR(32) NOT NULL,
                entity_id  VARCHAR(64) NULL,
                detail     TEXT        NULL,
                created_at DATETIME    NOT NULL
            ){$suffix}",

            'CREATE UNIQUE INDEX idx_products_sku ON products (sku)',
            'CREATE INDEX idx_products_rev ON products (rev)',
            'CREATE INDEX idx_products_category ON products (category)',
            'CREATE UNIQUE INDEX idx_orders_number ON orders (order_number)',
            'CREATE UNIQUE INDEX idx_orders_uuid ON orders (client_uuid)',
            'CREATE INDEX idx_orders_rev ON orders (rev)',
            'CREATE INDEX idx_orders_created ON orders (created_at)',
            'CREATE INDEX idx_items_order ON order_items (order_id)',
            'CREATE INDEX idx_customers_rev ON customers (rev)',
            'CREATE UNIQUE INDEX idx_customers_member ON customers (dxn_member_id)',
            'CREATE INDEX idx_movements_product ON stock_movements (product_id)',
            'CREATE INDEX idx_attempts_ip ON auth_attempts (ip)',
        ];
    }

    private static function ensureCounter(): void
    {
        $exists = Database::one('SELECT id FROM sync_counter WHERE id = 1');
        if ($exists === null) {
            Database::run('INSERT INTO sync_counter (id, value) VALUES (1, 1)');
        }
    }

    /** @return array<int,string> */
    public static function tableNames(): array
    {
        return [
            'products', 'orders', 'order_items', 'customers',
            'stock_movements', 'settings', 'sync_counter', 'auth_attempts', 'audit_log',
        ];
    }

    public static function seed(): void
    {
        $count = (int) Database::scalar('SELECT COUNT(*) FROM products');
        if ($count === 0) {
            foreach (self::products() as $product) {
                Store::createProduct($product);
            }
        }

        foreach (self::defaultSettings() as $key => $value) {
            if (Store::setting($key) === null) {
                Store::putSetting($key, $value, false);
            }
        }
    }

    /** @return array<int,array<string,mixed>> */
    public static function products(): array
    {
        return [
            ['sku' => 'DXN-LZC-3IN1-20',  'name' => 'Lingzhi Coffee 3-in-1',           'category' => 'Coffee',        'retail_price' => 45.00,  'member_price' => 35.00,  'pv_points' => 24.0,  'stock_qty' => 120],
            ['sku' => 'DXN-LZC-2IN1-20',  'name' => 'Lingzhi Coffee 2-in-1',           'category' => 'Coffee',        'retail_price' => 42.00,  'member_price' => 33.00,  'pv_points' => 22.0,  'stock_qty' => 80],
            ['sku' => 'DXN-ZHI-MOCHA-20', 'name' => 'Zhi Mocha Premium',               'category' => 'Coffee',        'retail_price' => 50.00,  'member_price' => 40.00,  'pv_points' => 26.0,  'stock_qty' => 60],
            ['sku' => 'DXN-LZC-LITE-20',  'name' => 'Lingzhi Coffee Lite',             'category' => 'Coffee',        'retail_price' => 40.00,  'member_price' => 31.00,  'pv_points' => 20.0,  'stock_qty' => 55],
            ['sku' => 'DXN-RGGL-90',      'name' => 'RG/GL Capsules (90s Duo)',        'category' => 'Supplements',   'retail_price' => 220.00, 'member_price' => 170.00, 'pv_points' => 115.0, 'stock_qty' => 40],
            ['sku' => 'DXN-MORINZHI-500', 'name' => 'Morinzhi Pure Noni (500ml)',      'category' => 'Supplements',   'retail_price' => 90.00,  'member_price' => 70.00,  'pv_points' => 46.0,  'stock_qty' => 30],
            ['sku' => 'DXN-SPIRULINA-120','name' => 'Spirulina Tablets (120s)',        'category' => 'Supplements',   'retail_price' => 75.00,  'member_price' => 58.00,  'pv_points' => 38.0,  'stock_qty' => 50],
            ['sku' => 'DXN-ANDRO-90',     'name' => 'Andro-G Capsules (90s)',          'category' => 'Supplements',   'retail_price' => 65.00,  'member_price' => 50.00,  'pv_points' => 33.0,  'stock_qty' => 35],
            ['sku' => 'DXN-LZC-TEA-25',   'name' => 'Lingzhi Black Tea Blend',         'category' => 'Coffee',        'retail_price' => 38.00,  'member_price' => 29.00,  'pv_points' => 19.0,  'stock_qty' => 3],
            ['sku' => 'DXN-TOOTHPASTE-120','name' => 'Ganozhi Ganoderma Toothpaste',   'category' => 'Personal Care', 'retail_price' => 28.00,  'member_price' => 22.00,  'pv_points' => 14.0,  'stock_qty' => 75],
            ['sku' => 'DXN-SOAP-GANO',    'name' => 'Ganozhi Beauty Soap',             'category' => 'Personal Care', 'retail_price' => 18.00,  'member_price' => 14.00,  'pv_points' => 9.0,   'stock_qty' => 2],
            ['sku' => 'DXN-SHAMPOO-GANO', 'name' => 'Ganozhi Nourishing Shampoo',      'category' => 'Personal Care', 'retail_price' => 35.00,  'member_price' => 27.00,  'pv_points' => 17.0,  'stock_qty' => 25],
        ];
    }

    /** @return array<string,string> */
    public static function defaultSettings(): array
    {
        return [
            'branch_name'         => 'DXN Stockpoint Central',
            'whatsapp_number'     => '8801987851131',
            'currency'            => 'BDT',
            'low_stock_threshold' => '5',
            'receipt_footer'      => 'Thank you for choosing DXN!',
            'business_address'    => '',
            'business_phone'      => '',
            'allow_public_orders' => '1',
        ];
    }
}
