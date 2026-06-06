# StockForecasting Codex Handoff

## Project

- Workspace: `E:\ProjectJava\StockForecasting`
- Stack: Spring Boot 3.4.5, Java 21 target, Gradle, PostgreSQL
- Main active area: Wildberries parser / monitoring / proxy / cookie session handling

## Current Goal

Continue from the existing implementation. Do **not** restart from scratch.

The main unresolved problem is:

- Wildberries requests through proxy now work
- but after a few minutes the product detail endpoint starts returning `HTTP 498`
- specifically:
  - `https://www.wildberries.ru/__internal/u-card/cards/v4/detail?...`

We are now investigating how to obtain / refresh valid WB session cookies automatically instead of relying on a single manually copied cookie string.

## What Already Works

### 1. Secure HTTPS proxy support is working

The app now successfully uses HTTPS proxies from `proxies.txt` and logs real proxied successful requests like:

- `WB GET ... via net-...mcccx.com:8444 -> HTTP 200`

This means the previous proxy transport problem is solved.

### 2. Proxy file format

The project uses:

- `proxies.txt` in project root
- format: `host:port:username:password`

The current proxy endpoints are hostname-based `*.mcccx.com:8444` entries, not raw IPs.

### 3. Docker deployment is mostly ready

`docker-compose.jar.yml` was updated to mount the correct jar path:

- `./build/libs/StockForecasting-1.0-SNAPSHOT.jar`

Proxy- and cookie-related env wiring is already present there.

## Important Implemented Changes

### Proxy transport

Implemented a dedicated secure proxy path in:

- `src/main/java/org/example/parser/wb/WildberriesHttpClient.java`

For secure proxies, requests now go through:

1. TLS socket to proxy host
2. `CONNECT target:443`
3. nested TLS to Wildberries target
4. manual HTTP GET over the tunnel

Non-secure proxy / Jsoup path still exists for ordinary proxies.

### Proxy model/config

Updated:

- `src/main/java/org/example/parser/wb/WildberriesProxy.java`
- `src/main/java/org/example/parser/wb/WildberriesProxyFileLoader.java`
- `src/main/java/org/example/parser/wb/WildberriesParserProperties.java`

Notable additions:

- proxy `secure` flag
- `proxy-secure`
- support for inline credentials

### Monitoring throttling and cookie reload

Recently added to reduce `498` pressure:

- global throttling for WB detail requests in:
  - `src/main/java/org/example/parser/wb/WildberriesHttpClient.java`
- hot reload of `cookie.txt` when the file changes
- warning diagnostics when only very thin cookie sets are loaded
- `monitorParallelism` support in:
  - `src/main/java/org/example/parser/wb/WildberriesParserProperties.java`
  - `src/main/java/org/example/parser/wb/WildberriesProductMonitoringService.java`
  - `src/main/resources/application.yml`
  - `deploy/docker.env.example`
  - `docker-compose.jar.yml`

## Current Behavior / Diagnosis

### Proxy side

Healthy now.

Observed earlier:

- `Loaded 100 WB proxies from proxies.txt`
- `WB proxy health check: 95 of 100 proxies are reachable`
- many successful `HTTP 200` responses via proxy

### Cookie side

Current `cookie.txt` is too weak:

- only **1** cookie set is loaded
- that cookie set currently contains only:
  - `_wbauid`
  - `x_wbaas_token`

So the app has effectively one fragile WB session and almost nothing to rotate to.

### 498 meaning in this context

The current conclusion is:

- `498` is no longer a proxy transport issue
- it is likely WB anti-bot / session invalidation on detail requests
- high monitoring concurrency makes the session burn faster

## Important Configuration

Current relevant properties are in:

- `src/main/resources/application.yml`

Key settings:

- `wb.parser.proxy-secure`
- `wb.parser.proxy-enabled`
- `wb.parser.proxy-file`
- `wb.parser.request-delay`
- `wb.parser.monitor-batch-size`
- `wb.parser.monitor-parallelism`
- `wb.parser.cookie-switch-window-size`
- `wb.parser.cookie-switch-min-samples`
- `wb.parser.cookie-switch-http498-threshold`

## Recommended Temporary Runtime Settings While Investigating 498

Use calmer values:

```text
WB_MONITOR_PARALLELISM=4
WB_REQUEST_DELAY=1s
```

If still unstable:

```text
WB_MONITOR_PARALLELISM=2
WB_REQUEST_DELAY=1500ms
```

## Docker Status

Artifact-based deploy path is ready:

1. build jar:

```powershell
.\gradlew.bat bootJar
```

2. run:

```powershell
docker compose -f docker-compose.jar.yml up -d
```

Important:

- `cookie.txt` and `proxies.txt` must exist next to `docker-compose.jar.yml`
- compose mounts:
  - `/app/cookie.txt`
  - `/app/proxies.txt`

## Files Changed During This Work

- `src/main/java/org/example/parser/wb/WildberriesHttpClient.java`
- `src/main/java/org/example/parser/wb/WildberriesProxy.java`
- `src/main/java/org/example/parser/wb/WildberriesProxyFileLoader.java`
- `src/main/java/org/example/parser/wb/WildberriesParserProperties.java`
- `src/main/java/org/example/parser/wb/WildberriesProductMonitoringService.java`
- `src/main/resources/application.yml`
- `docker-compose.jar.yml`
- `deploy/docker.env.example`
- `src/test/java/org/example/parser/wb/WildberriesProxyFileLoaderTest.java`

## Tests

Local project tests passed after the latest changes:

- `.\gradlew.bat test --no-daemon`
- result: `BUILD SUCCESSFUL`

## Wildberries Anti-Bot Investigation Status

We started analyzing how WB creates a fresh usable cookie/session.

Current findings:

1. `_wbauid` appears to be simple / locally generatable.
2. `x_wbaas_token` appears to be the important anti-bot token.
3. Public traces suggest WB uses an anti-bot flow involving:
   - `GET /` returning `498`
   - `POST /__wbaas/challenges/antibot/api/v1/find-frontend-settings`
   - `POST /__wbaas/challenges/antibot/api/v1/create-token`
   - challenge / fingerprint JS assets
4. A decoded public sample of `x_wbaas_token` appears to include IP, User-Agent, timestamps, and other structured fields.
5. Therefore, it likely cannot be safely hand-crafted as a static string.

### Important limitation during this analysis

The previous Codex environment could not directly resolve `www.wildberries.ru` from shell / Node runtime due DNS/network restrictions, so the anti-bot analysis was based on:

- public `urlscan.io` traces
- current local cookie structure
- local decoding of a public sample token

## Next Best Step

The next step is to capture **real browser network traffic** from a fresh Wildberries session on the user's machine.

We need either:

- HAR export with content

or at minimum:

- `Copy as cURL` for:
  1. first page load `GET /`
  2. `find-frontend-settings`
  3. failing `create-token`
  4. successful `create-token`

Needed details:

- request URL
- method
- status
- request headers
- response headers
- request body
- response body
- `Cookie`
- `Set-Cookie`
- `X-Wb-Antibot-*` headers

Do **not** strip WB cookies/tokens from that capture if the goal is reverse engineering the session bootstrap.

## What The Next Codex Should Do

1. Assume proxy transport is already fixed.
2. Do not spend time re-debugging proxy CONNECT unless new evidence appears.
3. Focus on session bootstrap / anti-bot flow for WB.
4. Help analyze HAR / cURL captures from browser.
5. Goal is to design either:
   - a browser-based bootstrapper that refreshes valid WB cookies automatically
   - or, if feasible, a lower-level client that reproduces the anti-bot token flow

## Suggested First Message For The Next Codex

Use something like:

> Continue from the current repo state. Do not restart the proxy work. Proxy transport is already fixed and Wildberries requests go through `*.mcccx.com:8444`. The unresolved problem is `HTTP 498` on `__internal/u-card/cards/v4/detail` after a few minutes. Please focus on analyzing HAR/cURL traffic for Wildberries anti-bot session bootstrap and help build an automatic cookie/session refresh mechanism.

