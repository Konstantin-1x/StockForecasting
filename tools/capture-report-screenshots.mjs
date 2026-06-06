#!/usr/bin/env node

import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const baseUrl = normalizeBaseUrl(process.argv[2] || process.env.APP_URL || "http://localhost:8080");
const outputDir = path.resolve(process.argv[3] || "outputs/testing");

const adminUsername = process.env.APP_ADMIN_USERNAME || "admin";
const adminPassword = process.env.APP_ADMIN_PASSWORD || "admin";
const sellerUsername = process.env.APP_SELLER_USERNAME || "seller_demo";
const sellerPassword = process.env.APP_SELLER_PASSWORD || "seller123";

const timings = [];

await main().catch((error) => {
  console.error("[ERROR] Не удалось сформировать снимки для отчета.");
  console.error(`Причина: ${friendlyErrorMessage(error)}`);
  console.error("");
  console.error("Что проверить:");
  console.error(`1. Приложение открывается в браузере по адресу ${baseUrl}`);
  console.error("2. Если нет установленного браузера Chromium/Chrome/Edge, выполните: npx playwright install chromium");
  console.error("3. Если логин или пароль отличаются, задайте APP_ADMIN_USERNAME, APP_ADMIN_PASSWORD, APP_SELLER_USERNAME, APP_SELLER_PASSWORD");
  process.exitCode = 1;
});

async function main() {
  const { chromium } = await importPlaywright();
  await mkdir(outputDir, { recursive: true });

  console.log("[OK] Подготовка браузерной проверки веб-интерфейса");
  console.log(`[OK] Адрес приложения: ${baseUrl}`);
  console.log(`[OK] Каталог снимков: ${outputDir}`);

  const browser = await launchBrowser(chromium);
  try {
    const publicContext = await browser.newContext({ viewport: { width: 1440, height: 950 } });
    const publicPage = await publicContext.newPage();
    await capture(publicPage, "/", "01-start-page.png", "Стартовая страница");
    await capture(publicPage, "/login", "02-login-page.png", "Форма входа");
    await capture(publicPage, "/this-page-does-not-exist", "08-not-found-page.png", "Страница неправильной ссылки");
    await publicContext.close();

    const adminContext = await browser.newContext({ viewport: { width: 1440, height: 950 } });
    const adminPage = await adminContext.newPage();
    await login(adminPage, adminUsername, adminPassword, "/admin", "администратор");
    await capture(adminPage, "/admin", "03-admin-dashboard.png", "Административная панель");
    await adminContext.close();

    const sellerContext = await browser.newContext({ viewport: { width: 1440, height: 950 } });
    const sellerPage = await sellerContext.newPage();
    await login(sellerPage, sellerUsername, sellerPassword, "/app", "продавец");
    await capture(sellerPage, "/app/products", "04-seller-products.png", "Страница товаров продавца");
    await capture(sellerPage, "/app/products/new", "05-seller-product-form.png", "Форма добавления товара");
    await capture(sellerPage, "/app/forecasts", "06-seller-forecasts.png", "Страница прогнозирования");
    await sellerContext.close();

    await writeFile(
      path.join(outputDir, "frontend-timings.json"),
      JSON.stringify(timings, null, 2),
      "utf8"
    );

    const reportContext = await browser.newContext({ viewport: { width: 1440, height: 950 } });
    const reportPage = await reportContext.newPage();
    await reportPage.setContent(buildTimingReport(timings), { waitUntil: "networkidle" });
    await reportPage.screenshot({ path: path.join(outputDir, "07-frontend-timings.png"), fullPage: true });
    await reportContext.close();
  } finally {
    await browser.close();
  }

  console.log("[OK] Браузерная проверка завершена");
  console.log("[OK] Снимки для раздела тестирования:");
  for (const file of [
    "01-start-page.png",
    "02-login-page.png",
    "03-admin-dashboard.png",
    "04-seller-products.png",
    "05-seller-product-form.png",
    "06-seller-forecasts.png",
    "07-frontend-timings.png",
    "08-not-found-page.png"
  ]) {
    console.log(`     ${path.join(outputDir, file)}`);
  }
}

async function launchBrowser(chromium) {
  const launchModes = [
    { name: "Playwright Chromium", options: { headless: true } },
    { name: "Google Chrome", options: { channel: "chrome", headless: true } },
    { name: "Microsoft Edge", options: { channel: "msedge", headless: true } }
  ];
  let lastError = null;
  for (const mode of launchModes) {
    try {
      const browser = await chromium.launch(mode.options);
      console.log(`[OK] Используется браузер: ${mode.name}`);
      return browser;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

async function login(page, username, password, expectedPath, roleLabel) {
  console.log(`[OK] Вход в систему: ${roleLabel}`);
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle", timeout: 30_000 });
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await Promise.all([
    page.waitForURL(`**${expectedPath}`, { timeout: 30_000 }),
    page.click('button[type="submit"]')
  ]);
}

async function capture(page, route, fileName, label) {
  const url = `${baseUrl}${route}`;
  console.log(`[OK] Проверка страницы: ${label}`);
  const startedAt = Date.now();
  const response = await page.goto(url, { waitUntil: "networkidle", timeout: 30_000 });
  const finishedAt = Date.now();
  const navigation = await page.evaluate(() => {
    const entry = performance.getEntriesByType("navigation")[0];
    if (!entry) {
      return null;
    }
    return {
      domContentLoadedMs: Math.round(entry.domContentLoadedEventEnd),
      loadEventMs: Math.round(entry.loadEventEnd),
      transferSize: entry.transferSize || 0,
      encodedBodySize: entry.encodedBodySize || 0,
      decodedBodySize: entry.decodedBodySize || 0
    };
  });

  timings.push({
    label,
    route,
    status: response ? response.status() : null,
    wallTimeMs: finishedAt - startedAt,
    navigation
  });

  await page.screenshot({ path: path.join(outputDir, fileName), fullPage: true });
  console.log(`     HTTP ${response ? response.status() : "-"}, ${finishedAt - startedAt} мс, файл: ${fileName}`);
}

function buildTimingReport(rows) {
  const tableRows = rows.map((row) => `
      <tr>
        <td>${escapeHtml(row.label)}</td>
        <td><code>${escapeHtml(row.route)}</code></td>
        <td>${row.status ?? "-"}</td>
        <td>${row.wallTimeMs}</td>
        <td>${row.navigation?.domContentLoadedMs ?? "-"}</td>
        <td>${row.navigation?.loadEventMs ?? "-"}</td>
        <td>${row.navigation?.decodedBodySize ?? "-"}</td>
      </tr>
  `).join("");

  return `<!doctype html>
  <html lang="ru">
  <head>
    <meta charset="utf-8">
    <title>Frontend timing report</title>
    <style>
      body { margin: 0; padding: 32px; background: #f5f7fb; color: #172033; font-family: Arial, sans-serif; }
      h1 { margin: 0 0 8px; font-size: 30px; }
      p { margin: 0 0 24px; color: #5c6575; }
      table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #d9deea; }
      th, td { padding: 12px 14px; border-bottom: 1px solid #e5e9f2; text-align: left; font-size: 14px; }
      th { background: #eef2f8; color: #243047; }
      code { font-family: Consolas, monospace; }
    </style>
  </head>
  <body>
    <h1>Проверка времени отклика веб-интерфейса</h1>
    <p>Снимок сформирован автоматически браузерным сценарием Playwright.</p>
    <table>
      <thead>
        <tr>
          <th>Страница</th>
          <th>Маршрут</th>
          <th>HTTP</th>
          <th>Полное время, мс</th>
          <th>DOMContentLoaded, мс</th>
          <th>Load, мс</th>
          <th>Размер HTML, байт</th>
        </tr>
      </thead>
      <tbody>${tableRows}</tbody>
    </table>
  </body>
  </html>`;
}

function normalizeBaseUrl(value) {
  return value.replace(/\/+$/, "");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function importPlaywright() {
  try {
    return await import("playwright");
  } catch (error) {
    const pnpmPlaywright = path.resolve(
      "node_modules/.pnpm/playwright@1.59.1/node_modules/playwright/index.mjs"
    );
    try {
      return await import(pathToFileURL(pnpmPlaywright).href);
    } catch {
      throw error;
    }
  }
}

function friendlyErrorMessage(error) {
  const message = error && error.message ? String(error.message) : String(error);
  if (message.includes("Executable doesn't exist") || message.includes("browserType.launch")) {
    return "Playwright найден, но браузер для автоматического запуска не установлен.";
  }
  if (message.includes("ERR_MODULE_NOT_FOUND")) {
    return "Не найден пакет Playwright. Установите его командой npm install playwright.";
  }
  if (message.includes("net::ERR_CONNECTION_REFUSED") || message.includes("ECONNREFUSED")) {
    return `Приложение не отвечает по адресу ${baseUrl}. Сначала запустите Spring Boot приложение.`;
  }
  if (message.includes("Timeout")) {
    return "Истекло время ожидания страницы. Проверьте, что приложение запущено, а учетные данные корректны.";
  }
  return message.split("\n")[0];
}
