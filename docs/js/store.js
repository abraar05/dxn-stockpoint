/**
 * Client-side state: an offline-first cache, a cart, an outbox and the sync
 * loop that keeps this browser and every other device on the same data.
 *
 * Everything a shop needs to keep selling lives in localStorage, so a dropped
 * connection or a closed tab never loses a sale.
 */

import api, { ApiError, getBase, hasBase, getToken, getTokenMeta, isTokenExpired, setToken } from './api.js';
import { num, round2, uuid, sortBy } from './util.js';

const CACHE_KEY = 'dxn.cache.v2';
const CART_KEY = 'dxn.cart.v2';
const OUTBOX_KEY = 'dxn.outbox.v2';
const SESSION_KEY = 'dxn.session.v2';

const POLL_MS = 15000;
const MAX_ORDERS = 400;

/**
 * Product photography bundled with the app so the catalogue looks right even
 * on the first offline launch. Files live in docs/images/products/ and the
 * original source URLs are recorded in docs/images/products/sources.json.
 */
export const PRODUCT_IMAGES = {
  'DXN-LZC-3IN1-20': 'images/products/lingzhi-coffee-3in1.jpg',
  'DXN-LZC-2IN1-20': 'images/products/lingzhi-coffee-2in1.jpg',
  'DXN-ZHI-MOCHA-20': 'images/products/zhi-mocha-premium.jpg',
  'DXN-LZC-LITE-20': 'images/products/lingzhi-coffee-lite.jpg',
  'DXN-RGGL-90': 'images/products/rggl-capsules-90.jpg',
  'DXN-MORINZHI-500': 'images/products/morinzhi-pure-noni.jpg',
  'DXN-SPIRULINA-120': 'images/products/spirulina-tablets-120.jpg',
  'DXN-ANDRO-90': 'images/products/andro-g-capsules-90.jpg',
  'DXN-LZC-TEA-25': 'images/products/lingzhi-black-tea.jpg',
  'DXN-TOOTHPASTE-120': 'images/products/ganozhi-toothpaste.jpg',
  'DXN-SOAP-GANO': 'images/products/ganozhi-beauty-soap.jpg',
  'DXN-SHAMPOO-GANO': 'images/products/ganozhi-shampoo.jpg',
};

const PRODUCT_DESCRIPTIONS = {
  'DXN-LZC-3IN1-20': 'The classic DXN coffee: premium instant coffee blended with Ganoderma lucidum extract, sugar and non-dairy creamer. 20 sachets.',
  'DXN-LZC-2IN1-20': 'DXN Lingzhi Coffee 2-in-1 without added sugar — smooth coffee with the goodness of Ganoderma. 20 sachets.',
  'DXN-ZHI-MOCHA-20': 'A rich mocha blend of coffee, cocoa and Ganoderma extract for a café-style cup at home.',
  'DXN-LZC-LITE-20': 'A lighter, creamier Lingzhi coffee with reduced sugar for an everyday cup.',
  'DXN-RGGL-90': 'Reishi Gano (RG) and Ganocelium (GL) capsules — the two cornerstone DXN Ganoderma supplements, 90 capsules each.',
  'DXN-MORINZHI-500': 'DXN Morinzhi botanical beverage made from noni (Morinda citrifolia) and roselle. Serve chilled.',
  'DXN-SPIRULINA-120': 'DXN Spirulina tablets — a natural source of phytonutrients, cultivated and tableted by DXN.',
  'DXN-ANDRO-90': 'DXN Andro-G capsules containing Andrographis paniculata extract. 90 capsules.',
  'DXN-LZC-TEA-25': 'DXN Lingzhi Black Tea — a smooth black tea enriched with Ganoderma extract.',
  'DXN-TOOTHPASTE-120': 'Ganozhi Plus toothpaste with Ganoderma extract for everyday oral care.',
  'DXN-SOAP-GANO': 'Ganozhi translucent beauty soap with Ganoderma extract and moisturisers.',
  'DXN-SHAMPOO-GANO': 'Ganozhi nourishing shampoo with Ganoderma extract for soft, healthy-looking hair.',
};

/** Shown on a cold start so the shop is never staring at an empty screen. */
const SEED_PRODUCTS = [
  ['DXN-LZC-3IN1-20', 'Lingzhi Coffee 3-in-1', 'Coffee', 45, 35, 24, 120],
  ['DXN-LZC-2IN1-20', 'Lingzhi Coffee 2-in-1', 'Coffee', 42, 33, 22, 80],
  ['DXN-ZHI-MOCHA-20', 'Zhi Mocha Premium', 'Coffee', 50, 40, 26, 60],
  ['DXN-LZC-LITE-20', 'Lingzhi Coffee Lite', 'Coffee', 40, 31, 20, 55],
  ['DXN-RGGL-90', 'RG/GL Capsules (90s Duo)', 'Supplements', 220, 170, 115, 40],
  ['DXN-MORINZHI-500', 'Morinzhi Pure Noni (500ml)', 'Supplements', 90, 70, 46, 30],
  ['DXN-SPIRULINA-120', 'Spirulina Tablets (120s)', 'Supplements', 75, 58, 38, 50],
  ['DXN-ANDRO-90', 'Andro-G Capsules (90s)', 'Supplements', 65, 50, 33, 35],
  ['DXN-LZC-TEA-25', 'Lingzhi Black Tea Blend', 'Coffee', 38, 29, 19, 3],
  ['DXN-TOOTHPASTE-120', 'Ganozhi Ganoderma Toothpaste', 'Personal Care', 28, 22, 14, 75],
  ['DXN-SOAP-GANO', 'Ganozhi Beauty Soap', 'Personal Care', 18, 14, 9, 2],
  ['DXN-SHAMPOO-GANO', 'Ganozhi Nourishing Shampoo', 'Personal Care', 35, 27, 17, 25],
].map(([sku, name, category, retail, member, pv, stock], index) => ({
  id: index + 1,
  sku,
  name,
  category,
  description: PRODUCT_DESCRIPTIONS[sku] || null,
  retail_price: retail,
  member_price: member,
  pv_points: pv,
  stock_qty: stock,
  low_stock_at: 5,
  image_url: PRODUCT_IMAGES[sku] || null,
  is_active: 1,
  created_at: null,
  updated_at: null,
  rev: 0,
}));

/** Fills in bundled artwork/descriptions when the server row has none. */
function decorateProduct(product) {
  if (!product) return product;
  return {
    ...product,
    image_url: product.image_url || PRODUCT_IMAGES[product.sku] || null,
    description: product.description || PRODUCT_DESCRIPTIONS[product.sku] || null,
  };
}

/** Absolute URL for a product photo, safe for API rows and bundled files. */
export function imageFor(product) {
  if (!product) return null;
  const raw = product.image_url || PRODUCT_IMAGES[product.sku] || null;
  if (!raw) return null;
  try {
    return new URL(raw, document.baseURI).href;
  } catch {
    return raw;
  }
}

const DEFAULT_SETTINGS = {
  branch_name: 'Mohammadpur Stock Point',
  whatsapp_number: '8801939994578',
  currency: 'BDT',
  low_stock_threshold: 5,
  receipt_footer: 'Thank you for choosing Mohammadpur Stock Point. Wealth through health.',
  business_address: 'H # 1, R # 11, B # A, Chandrima Model Town, Dhaka-1207',
  business_phone: '01939994578',
  allow_public_orders: 1,
};

/** Placeholder profile shipped before the Mohammadpur rebrand. */
const LEGACY_SETTINGS = {
  branch_name: ['DXN Stockpoint', 'DXN Stockpoint Central'],
  whatsapp_number: ['8801987851131'],
  receipt_footer: ['Thank you for choosing DXN!', 'Thank you for choosing DXN. Wealth through health.'],
  business_address: [''],
  business_phone: [''],
};

/** Fills the new shop profile over untouched placeholder values only. */
function withDefaults(settings) {
  const merged = { ...DEFAULT_SETTINGS, ...(settings || {}) };
  for (const [key, placeholders] of Object.entries(LEGACY_SETTINGS)) {
    if (placeholders.includes(String(merged[key] ?? ''))) merged[key] = DEFAULT_SETTINGS[key];
  }
  return merged;
}

export const state = {
  ready: false,
  online: typeof navigator === 'undefined' ? true : navigator.onLine,
  syncState: 'idle', // idle | syncing | online | pending | offline | local | error
  syncError: '',
  lastSyncAt: 0,
  lastPush: 0,
  rev: 0,
  products: [],
  orders: [],
  customers: [],
  settings: { ...DEFAULT_SETTINGS },
  cart: [],
  sale: emptySale(),
  tier: 'retail',
  outbox: [],
  isAdmin: false,
  health: null,
};

function emptySale() {
  return {
    customer_name: '',
    customer_phone: '',
    dxn_member_id: '',
    payment_method: 'CASH',
    discount: 0,
    note: '',
  };
}

// ── Persistence ─────────────────────────────────────────────────────────────

function read(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false; // private mode or quota — the session keeps working in memory
  }
}

export function persistCache() {
  write(CACHE_KEY, {
    rev: state.rev,
    products: state.products,
    orders: state.orders,
    customers: state.customers,
    settings: state.settings,
    savedAt: Date.now(),
  });
}

export function persistCart() {
  write(CART_KEY, { cart: state.cart, sale: state.sale, tier: state.tier });
}

export function persistOutbox() {
  write(OUTBOX_KEY, state.outbox);
}

// ── Tiny observable ─────────────────────────────────────────────────────────

const listeners = new Set();

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

let queued = false;

export function notify() {
  if (queued) return;
  queued = true;
  queueMicrotask(() => {
    queued = false;
    for (const fn of listeners) {
      try {
        fn(state);
      } catch (error) {
        console.error(error);
      }
    }
  });
}

/** Renders immediately while a poll is in flight. */
export function notifyNow() {
  for (const fn of listeners) {
    try {
      fn(state);
    } catch (error) {
      console.error(error);
    }
  }
}

// ── Boot ────────────────────────────────────────────────────────────────────

export function hydrate() {
  const cache = read(CACHE_KEY, null);
  if (cache) {
    state.rev = num(cache.rev);
    state.products = Array.isArray(cache.products) ? cache.products : [];
    state.orders = Array.isArray(cache.orders) ? cache.orders : [];
    state.customers = Array.isArray(cache.customers) ? cache.customers : [];
    state.settings = withDefaults(cache.settings);
    state.lastSyncAt = num(cache.savedAt);
  }

  state.products = state.products.map(decorateProduct);
  if (!state.products.length) state.products = SEED_PRODUCTS.map((p) => decorateProduct({ ...p }));

  const savedCart = read(CART_KEY, null);
  if (savedCart) {
    state.cart = Array.isArray(savedCart.cart) ? savedCart.cart : [];
    state.sale = { ...emptySale(), ...(savedCart.sale || {}) };
    state.tier = savedCart.tier === 'member' ? 'member' : 'retail';
  }

  state.outbox = read(OUTBOX_KEY, []) || [];

  const session = read(SESSION_KEY, null);
  state.isAdmin = Boolean(session?.isAdmin) && Boolean(getToken()) && !isTokenExpired(getTokenMeta());
  if (!state.isAdmin && session?.isAdmin) write(SESSION_KEY, { isAdmin: false });

  state.syncState = hasBase() ? 'idle' : 'local';
  state.ready = true;
  return state;
}

export function setAdmin(flag) {
  state.isAdmin = Boolean(flag);
  write(SESSION_KEY, { isAdmin: state.isAdmin });
  if (!flag) {
    setToken('');
    if (state.settings) {
      // admin-only fields never stay in the shared cache
      const { admin_pin_hash, ...rest } = state.settings;
      state.settings = rest;
    }
  }
  notify();
}

/** Re-reads the cache another tab just wrote. */
export function refreshFromStorage() {
  const cache = read(CACHE_KEY, null);
  if (!cache) return;
  state.rev = num(cache.rev);
  state.products = (Array.isArray(cache.products) ? cache.products : state.products).map(decorateProduct);
  state.orders = Array.isArray(cache.orders) ? cache.orders : state.orders;
  state.customers = Array.isArray(cache.customers) ? cache.customers : state.customers;
  state.settings = withDefaults(cache.settings);
  state.lastSyncAt = num(cache.savedAt) || state.lastSyncAt;
  const outbox = read(OUTBOX_KEY, null);
  if (Array.isArray(outbox)) state.outbox = outbox;
  notify();
}

// ── Derived data ────────────────────────────────────────────────────────────

export const currency = () => state.settings.currency || DEFAULT_SETTINGS.currency;

export const activeProducts = () => state.products.filter((p) => Number(p.is_active ?? 1) === 1);

export const productById = (id) => state.products.find((p) => Number(p.id) === Number(id)) || null;

export function unitPrice(product) {
  if (!product) return 0;
  return state.tier === 'member' ? num(product.member_price) : num(product.retail_price);
}

export function stockLevel(product) {
  const qty = num(product?.stock_qty);
  const threshold = Math.max(num(product?.low_stock_at, 5), num(state.settings.low_stock_threshold, 5));
  if (qty <= 0) return 'out';
  if (qty <= threshold) return 'low';
  return 'ok';
}

export function categories() {
  return [...new Set(activeProducts().map((p) => p.category).filter(Boolean))].sort();
}

export function cartLines() {
  return state.cart
    .map((line) => {
      const product = productById(line.productId);
      if (!product) return null;
      const price = unitPrice(product);
      const pv = num(product.pv_points);
      return {
        product,
        productId: product.id,
        qty: line.qty,
        price,
        pv,
        lineTotal: round2(price * line.qty),
        linePv: round2(pv * line.qty),
        available: num(product.stock_qty),
      };
    })
    .filter(Boolean);
}

export function cartTotals() {
  const lines = cartLines();
  const subtotal = round2(lines.reduce((sum, l) => sum + l.lineTotal, 0));
  const pv = round2(lines.reduce((sum, l) => sum + l.linePv, 0));
  const discount = Math.min(round2(num(state.sale.discount)), subtotal);
  const total = round2(Math.max(0, subtotal - discount));
  const units = lines.reduce((sum, l) => sum + l.qty, 0);
  return { lines, subtotal, discount, total, pv, units, count: lines.length };
}

export function cartCount() {
  return state.cart.reduce((sum, line) => sum + line.qty, 0);
}

export const pendingCount = () => state.outbox.filter((o) => o.status !== 'failed').length;
export const failedCount = () => state.outbox.filter((o) => o.status === 'failed').length;

// ── Cart ────────────────────────────────────────────────────────────────────

export function setTier(tier) {
  state.tier = tier === 'member' ? 'member' : 'retail';
  persistCart();
  notify();
}

export function addToCart(productId, qty = 1) {
  const product = productById(productId);
  if (!product) return { ok: false, message: 'That product is no longer in the catalogue.' };

  const existing = state.cart.find((line) => Number(line.productId) === Number(productId));
  const next = (existing?.qty || 0) + qty;
  if (next > num(product.stock_qty)) {
    return { ok: false, message: `Only ${num(product.stock_qty)} left of ${product.name}.` };
  }
  if (next <= 0) {
    state.cart = state.cart.filter((line) => Number(line.productId) !== Number(productId));
  } else if (existing) {
    existing.qty = next;
  } else {
    state.cart.push({ productId: product.id, qty: next });
  }
  persistCart();
  notify();
  return { ok: true };
}

export function setQty(productId, qty) {
  const product = productById(productId);
  const target = Math.max(0, Math.min(Math.round(qty), num(product?.stock_qty)));
  if (target <= 0) {
    removeFromCart(productId);
    return;
  }
  const line = state.cart.find((l) => Number(l.productId) === Number(productId));
  if (line) line.qty = target;
  else state.cart.push({ productId: Number(productId), qty: target });
  persistCart();
  notify();
}

export function removeFromCart(productId) {
  state.cart = state.cart.filter((line) => Number(line.productId) !== Number(productId));
  persistCart();
  notify();
}

export function clearCart() {
  state.cart = [];
  state.sale = emptySale();
  persistCart();
  notify();
}

export function updateSale(patch) {
  state.sale = { ...state.sale, ...patch };
  persistCart();
  notify();
}

export function buildOrderPayload() {
  const { lines, subtotal, discount, total, pv } = cartTotals();
  return {
    client_uuid: uuid(),
    customer_name: (state.sale.customer_name || '').trim() || 'Walk-in customer',
    customer_phone: (state.sale.customer_phone || '').trim(),
    dxn_member_id: (state.sale.dxn_member_id || '').trim(),
    is_member: state.tier === 'member',
    payment_method: state.sale.payment_method || 'CASH',
    discount,
    note: (state.sale.note || '').trim(),
    source: 'web',
    items: lines.map((line) => ({
      product_id: line.productId,
      quantity: line.qty,
      unit_price: line.price,
      unit_pv: line.pv,
    })),
    _totals: { subtotal, total, pv }, // stripped before it leaves the device
  };
}

// ── Outbox ──────────────────────────────────────────────────────────────────

export function enqueueOrder() {
  const payload = buildOrderPayload();
  if (!payload.items.length) return { ok: false, message: 'The cart is empty.' };
  const totals = payload._totals;
  delete payload._totals;

  state.outbox.push({
    id: payload.client_uuid,
    payload,
    totals,
    createdAt: Date.now(),
    attempts: 0,
    status: 'pending',
    error: '',
  });
  persistOutbox();
  clearCart();

  // Show the sale immediately: local stock only ever goes down optimistically.
  for (const item of payload.items) {
    const product = productById(item.product_id);
    if (product) product.stock_qty = num(product.stock_qty) - item.quantity;
  }
  persistCache();
  notify();

  syncNow();
  return { ok: true, payload };
}

export function dropFromOutbox(id) {
  state.outbox = state.outbox.filter((item) => item.id !== id);
  persistOutbox();
  notify();
}

export function retryOutboxItem(id) {
  const item = state.outbox.find((o) => o.id === id);
  if (!item) return;
  item.status = 'pending';
  item.error = '';
  item.attempts = 0;
  persistOutbox();
  notify();
  syncNow();
}

export function retryAll() {
  for (const item of state.outbox) {
    item.status = 'pending';
    item.error = '';
  }
  persistOutbox();
  notify();
  syncNow();
}

async function flushOutbox() {
  if (!state.outbox.length) return 0;
  let sent = 0;
  let blocked = false;

  for (const item of [...state.outbox]) {
    if (item.status === 'failed') continue;
    try {
      item.attempts += 1;
      const result = await api.createOrder(item.payload);
      state.outbox = state.outbox.filter((o) => o.id !== item.id);
      sent += 1;
      if (result?.order) mergeOrders([result.order]);
      if (result?.duplicate) mergeOrders([result.order]);
    } catch (error) {
      item.error = error.message;
      if (error instanceof ApiError && [400, 403, 404, 409, 422].includes(error.status)) {
        // A permanent rejection (e.g. stock ran out). Keep it visible so the
        // shop can decide; never retry it silently in a loop.
        item.status = 'failed';
      } else {
        blocked = true;
        break;
      }
    }
  }

  persistOutbox();
  if (sent) {
    state.lastPush = Date.now();
    persistCache();
  }
  if (blocked) {
    throw new ApiError('Still offline — queued sales will be pushed when the connection returns.');
  }
  return sent;
}

// ── Sync ────────────────────────────────────────────────────────────────────

function mergeProducts(incoming) {
  const map = new Map(state.products.map((p) => [Number(p.id), p]));
  for (const product of incoming) map.set(Number(product.id), decorateProduct(product));
  state.products = sortBy([...map.values()], 'name');
}

function mergeOrders(incoming) {
  const map = new Map(state.orders.map((o) => [Number(o.id), o]));
  for (const order of incoming) {
    const id = Number(order.id);
    if (!id) continue;
    map.set(id, { ...(map.get(id) || {}), ...order });
  }
  state.orders = [...map.values()]
    .sort((a, b) => String(b.created_at || '').localeCompare(String(a.created_at || '')) || Number(b.id) - Number(a.id))
    .slice(0, MAX_ORDERS);
}

/** Lets admin forms push a freshly saved row without waiting for the next poll. */
export function upsertProducts(list) {
  mergeProducts(list || []);
  persistCache();
  notify();
}

export function upsertOrders(list) {
  mergeOrders(list || []);
  persistCache();
  notify();
}

function mergeCustomers(incoming) {
  const map = new Map(state.customers.map((c) => [Number(c.id), c]));
  for (const customer of incoming) map.set(Number(customer.id), { ...(map.get(Number(customer.id)) || {}), ...customer });
  state.customers = [...map.values()].slice(0, 1000);
}

function applySync(payload) {
  if (Array.isArray(payload.products) && payload.products.length) mergeProducts(payload.products);
  if (Array.isArray(payload.orders) && payload.orders.length) mergeOrders(payload.orders);
  if (Array.isArray(payload.customers) && payload.customers.length) mergeCustomers(payload.customers);
  if (payload.settings && typeof payload.settings === 'object') {
    const { admin_pin_hash, ...safe } = payload.settings;
    state.settings = withDefaults({ ...state.settings, ...safe });
  }
  state.rev = Math.max(state.rev, num(payload.server_rev));
}

let syncing = false;

export async function syncNow({ quiet = false } = {}) {
  if (syncing) return;
  if (!hasBase()) {
    state.syncState = 'local';
    if (!quiet) notify();
    return;
  }
  if (!navigator.onLine) {
    state.syncState = 'offline';
    notify();
    return;
  }

  syncing = true;
  state.syncState = 'syncing';
  state.syncError = '';
  notify();

  try {
    await flushOutbox();

    let payload = await api.sync(state.rev);
    applySync(payload);

    let guard = 0;
    while (payload.truncated && guard < 12) {
      guard += 1;
      payload = await api.sync(state.rev);
      applySync(payload);
    }

    state.lastSyncAt = Date.now();
    state.syncState = state.outbox.length ? 'pending' : 'online';
    state.syncError = '';
    persistCache();
  } catch (error) {
    state.syncError = error.message || 'Sync failed';
    state.syncState = error?.status === 0 ? 'offline' : 'error';
  } finally {
    syncing = false;
    notify();
  }
}

export async function verifySession() {
  if (!state.isAdmin || !getToken()) return false;
  try {
    await api.me();
    return true;
  } catch (error) {
    if (error instanceof ApiError && error.isAuth) {
      setAdmin(false);
      return false;
    }
    return state.isAdmin;
  }
}

let loopStarted = false;

export function startSyncLoop() {
  if (loopStarted) return;
  loopStarted = true;

  window.addEventListener('online', () => {
    state.online = true;
    notify();
    syncNow();
  });

  window.addEventListener('offline', () => {
    state.online = false;
    state.syncState = 'offline';
    notify();
  });

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) syncNow({ quiet: true });
  });

  window.addEventListener('storage', (event) => {
    if (event.key === CACHE_KEY || event.key === OUTBOX_KEY) refreshFromStorage();
  });

  setInterval(() => {
    if (!document.hidden && navigator.onLine) syncNow({ quiet: true });
  }, POLL_MS);

  syncNow();
  verifySession();
}

export { DEFAULT_SETTINGS, getBase };
