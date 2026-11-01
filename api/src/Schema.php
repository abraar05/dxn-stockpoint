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

        self::backfillArtwork();

        foreach (self::defaultSettings() as $key => $value) {
            if (Store::setting($key) === null) {
                Store::putSetting($key, $value, false);
            }
        }

        self::backfillBranding();
    }

    /**
     * Older installations seeded the placeholder "DXN Stockpoint Central"
     * profile. Move only untouched placeholder values to the Mohammadpur
     * profile; a shopkeeper's own branch name, number or address is preserved.
     */
    private static function backfillBranding(): void
    {
        $replacements = [
            'branch_name' => [
                'old' => ['DXN Stockpoint Central', 'DXN Stockpoint'],
                'new' => 'Mohammadpur Stock Point',
            ],
            'whatsapp_number' => [
                'old' => ['8801987851131'],
                'new' => '8801939994578',
            ],
            'receipt_footer' => [
                'old' => ['Thank you for choosing DXN!', 'Thank you for choosing DXN. Wealth through health.'],
                'new' => 'Thank you for choosing Mohammadpur Stock Point. Wealth through health.',
            ],
            'business_address' => [
                'old' => [''],
                'new' => 'H # 1, R # 11, B # A, Chandrima Model Town, Dhaka-1207',
            ],
            'business_phone' => [
                'old' => [''],
                'new' => '01939994578',
            ],
        ];

        foreach ($replacements as $key => $migration) {
            $current = Store::setting($key);
            if ($current === null || !in_array($current, $migration['old'], true)) {
                continue;
            }
            Store::putSetting($key, $migration['new'], true);
        }
    }

    /**
     * Existing shops that were seeded before product photography existed get
     * the bundled photos on the next install/upgrade run. Only empty columns
     * are touched, so a shopkeeper's own image URLs are never overwritten.
     */
    private static function backfillArtwork(): void
    {
        foreach (self::products() as $product) {
            $row = Database::one(
                'SELECT id, image_url, description FROM products WHERE sku = ?',
                [$product['sku']]
            );
            if ($row === null) {
                continue;
            }
            $needsImage = empty($row['image_url']);
            $needsDescription = empty($row['description']);
            if (!$needsImage && !$needsDescription) {
                continue;
            }
            Database::run(
                'UPDATE products SET image_url = ?, description = ?, updated_at = ?, rev = ? WHERE id = ?',
                [
                    $needsImage ? ($product['image_url'] ?? null) : $row['image_url'],
                    $needsDescription ? ($product['description'] ?? null) : $row['description'],
                    Util::now(),
                    Store::nextRev(),
                    $row['id'],
                ]
            );
        }
    }

    /** Public location of the product photography bundled with the web app. */
    private const IMAGE_BASE = 'https://abraar05.github.io/dxn-stockpoint/images/products/';

    /** @return array<int,array<string,mixed>> */
    public static function products(): array
    {
        return [
            [
                'sku' => 'DXN-LZC-3IN1-20',
                'name' => 'Lingzhi Coffee 3-in-1',
                'category' => 'Coffee',
                'description' => 'The classic DXN coffee: premium instant coffee blended with Ganoderma lucidum extract, sugar and non-dairy creamer. 20 sachets.',
                'retail_price' => 45.00, 'member_price' => 35.00, 'pv_points' => 24.0, 'stock_qty' => 120,
                'image_url' => self::IMAGE_BASE . 'lingzhi-coffee-3in1.jpg',
            ],
            [
                'sku' => 'DXN-LZC-2IN1-20',
                'name' => 'Lingzhi Coffee 2-in-1',
                'category' => 'Coffee',
                'description' => 'DXN Lingzhi Coffee 2-in-1 without added sugar — smooth coffee with the goodness of Ganoderma. 20 sachets.',
                'retail_price' => 42.00, 'member_price' => 33.00, 'pv_points' => 22.0, 'stock_qty' => 80,
                'image_url' => self::IMAGE_BASE . 'lingzhi-coffee-2in1.jpg',
            ],
            [
                'sku' => 'DXN-ZHI-MOCHA-20',
                'name' => 'Zhi Mocha Premium',
                'category' => 'Coffee',
                'description' => 'A rich mocha blend of coffee, cocoa and Ganoderma extract for a café-style cup at home.',
                'retail_price' => 50.00, 'member_price' => 40.00, 'pv_points' => 26.0, 'stock_qty' => 60,
                'image_url' => self::IMAGE_BASE . 'zhi-mocha-premium.jpg',
            ],
            [
                'sku' => 'DXN-LZC-LITE-20',
                'name' => 'Lingzhi Coffee Lite',
                'category' => 'Coffee',
                'description' => 'A lighter, creamier Lingzhi coffee with reduced sugar for an everyday cup.',
                'retail_price' => 40.00, 'member_price' => 31.00, 'pv_points' => 20.0, 'stock_qty' => 55,
                'image_url' => self::IMAGE_BASE . 'lingzhi-coffee-lite.jpg',
            ],
            [
                'sku' => 'DXN-RGGL-90',
                'name' => 'RG/GL Capsules (90s Duo)',
                'category' => 'Supplements',
                'description' => 'Reishi Gano (RG) and Ganocelium (GL) capsules — the two cornerstone DXN Ganoderma supplements, 90 capsules each.',
                'retail_price' => 220.00, 'member_price' => 170.00, 'pv_points' => 115.0, 'stock_qty' => 40,
                'image_url' => self::IMAGE_BASE . 'rggl-capsules-90.jpg',
            ],
            [
                'sku' => 'DXN-MORINZHI-500',
                'name' => 'Morinzhi Pure Noni (500ml)',
                'category' => 'Supplements',
                'description' => 'DXN Morinzhi botanical beverage made from noni (Morinda citrifolia) and roselle. Serve chilled.',
                'retail_price' => 90.00, 'member_price' => 70.00, 'pv_points' => 46.0, 'stock_qty' => 30,
                'image_url' => self::IMAGE_BASE . 'morinzhi-pure-noni.jpg',
            ],
            [
                'sku' => 'DXN-SPIRULINA-120',
                'name' => 'Spirulina Tablets (120s)',
                'category' => 'Supplements',
                'description' => 'DXN Spirulina tablets — a natural source of phytonutrients, cultivated and tableted by DXN.',
                'retail_price' => 75.00, 'member_price' => 58.00, 'pv_points' => 38.0, 'stock_qty' => 50,
                'image_url' => self::IMAGE_BASE . 'spirulina-tablets-120.jpg',
            ],
            [
                'sku' => 'DXN-ANDRO-90',
                'name' => 'Andro-G Capsules (90s)',
                'category' => 'Supplements',
                'description' => 'DXN Andro-G capsules containing Andrographis paniculata extract. 90 capsules.',
                'retail_price' => 65.00, 'member_price' => 50.00, 'pv_points' => 33.0, 'stock_qty' => 35,
                'image_url' => self::IMAGE_BASE . 'andro-g-capsules-90.jpg',
            ],
            [
                'sku' => 'DXN-LZC-TEA-25',
                'name' => 'Lingzhi Black Tea Blend',
                'category' => 'Coffee',
                'description' => 'DXN Lingzhi Black Tea — a smooth black tea enriched with Ganoderma extract.',
                'retail_price' => 38.00, 'member_price' => 29.00, 'pv_points' => 19.0, 'stock_qty' => 3,
                'image_url' => self::IMAGE_BASE . 'lingzhi-black-tea.jpg',
            ],
            [
                'sku' => 'DXN-TOOTHPASTE-120',
                'name' => 'Ganozhi Ganoderma Toothpaste',
                'category' => 'Personal Care',
                'description' => 'Ganozhi Plus toothpaste with Ganoderma extract for everyday oral care.',
                'retail_price' => 28.00, 'member_price' => 22.00, 'pv_points' => 14.0, 'stock_qty' => 75,
                'image_url' => self::IMAGE_BASE . 'ganozhi-toothpaste.jpg',
            ],
            [
                'sku' => 'DXN-SOAP-GANO',
                'name' => 'Ganozhi Beauty Soap',
                'category' => 'Personal Care',
                'description' => 'Ganozhi translucent beauty soap with Ganoderma extract and moisturisers.',
                'retail_price' => 18.00, 'member_price' => 14.00, 'pv_points' => 9.0, 'stock_qty' => 2,
                'image_url' => self::IMAGE_BASE . 'ganozhi-beauty-soap.jpg',
            ],
            [
                'sku' => 'DXN-SHAMPOO-GANO',
                'name' => 'Ganozhi Nourishing Shampoo',
                'category' => 'Personal Care',
                'description' => 'Ganozhi nourishing shampoo with Ganoderma extract for soft, healthy-looking hair.',
                'retail_price' => 35.00, 'member_price' => 27.00, 'pv_points' => 17.0, 'stock_qty' => 25,
                'image_url' => self::IMAGE_BASE . 'ganozhi-shampoo.jpg',
            ],
        ];
    }

    /** @return array<string,string> */
    public static function defaultSettings(): array
    {
        return [
            'branch_name'         => 'Mohammadpur Stock Point',
            'whatsapp_number'     => '8801939994578',
            'currency'            => 'BDT',
            'low_stock_threshold' => '5',
            'receipt_footer'      => 'Thank you for choosing Mohammadpur Stock Point. Wealth through health.',
            'business_address'    => 'H # 1, R # 11, B # A, Chandrima Model Town, Dhaka-1207',
            'business_phone'      => '01939994578',
            'allow_public_orders' => '1',
        ];
    }
}
