# Promo Forecast

Веб-приложение для анализа товаров Wildberries и расчета параметров промоакции с оплатой за отзыв. Система хранит каталог WB, товары продавцов, исторические снимки, прогнозы и диагностическую информацию, а также предоставляет отдельные интерфейсы администратора и продавца.

## Основные возможности

- регистрация и авторизация продавцов;
- кабинет продавца с краткой аналитикой по товарам и прогнозам;
- добавление товаров продавца по артикулу Wildberries;
- расчет прогноза промоакции: ставка за отзыв, план отзывов, срок реализации, бюджет и качество прогноза;
- административная панель с пользователями, товарами, предложениями WB, прогнозами и диагностикой;
- просмотр многоуровневого каталога Wildberries;
- импорт и экспорт данных приложения;
- проверка качества данных;
- сбор и мониторинг данных Wildberries через HTTP-клиент, cookie и прокси;
- автоматические тесты backend-слоя, web-слоя и прогнозной модели.

## Технологии

- Java 17;
- Spring Boot 3.4.5;
- Spring MVC;
- Spring Security;
- Spring Data JPA;
- Hibernate;
- PostgreSQL;
- Thymeleaf;
- Bean Validation;
- Gradle 8.14;
- JUnit 5;
- AssertJ;
- Mockito;
- H2 для тестового окружения;
- JSoup для обработки HTML/данных;
- Playwright для браузерной проверки и скриншотов;
- Docker и Docker Compose для контейнерного запуска.

## Архитектура

Проект построен как монолитное Spring Boot приложение:

- `src/main/java/org/example/domain` - JPA-сущности;
- `src/main/java/org/example/repository` - репозитории Spring Data JPA;
- `src/main/java/org/example/security` - авторизация, роли и стартовые пользователи;
- `src/main/java/org/example/web` - MVC-контроллеры, формы и сервисы интерфейса;
- `src/main/java/org/example/forecast` - нейросетевое прогнозирование;
- `src/main/java/org/example/parser/wb` - интеграция с Wildberries;
- `src/main/resources/templates` - Thymeleaf-шаблоны;
- `src/main/resources/static/css` - стили интерфейса;
- `src/test/java` - автоматические тесты.

## База данных

По умолчанию приложение подключается к PostgreSQL:

```text
jdbc:postgresql://localhost:5432/stockforecasting_imported
```

Стандартные параметры из `application.yml`:

```text
DB_URL=jdbc:postgresql://localhost:5432/stockforecasting_imported
DB_USERNAME=postgres
DB_PASSWORD=admin
SERVER_PORT=8080
```

Hibernate работает в режиме:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

Это позволяет приложению автоматически создавать и обновлять таблицы при старте. Для финальной демонстрации рекомендуется использовать подготовленную базу `stockforecasting_imported`, так как в ней уже есть демонстрационные данные, товары, категории, исторические снимки и прогнозы.

## Роли и учетные записи

При запуске приложение создает базового администратора и демонстрационного продавца.

Администратор:

```text
Логин: admin
Пароль: admin
```

Продавец:

```text
Логин: seller_demo
Пароль: seller123
```

Администратор после входа перенаправляется в `/admin`, продавец - в `/app`.

## Локальный запуск

Требования:

- JDK 17 или выше;
- PostgreSQL;
- созданная база `stockforecasting_imported`;
- PowerShell или терминал с доступом к Gradle wrapper.

Запуск приложения:

```powershell
.\gradlew.bat bootRun
```

После старта сайт доступен по адресу:

```text
http://localhost:8080
```

Если нужно явно указать параметры подключения:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/stockforecasting_imported"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="admin"
.\gradlew.bat bootRun
```

Для демонстрационного запуска без фонового сбора данных Wildberries:

```powershell
$env:WB_CONTINUOUS_SCAN_ENABLED="false"
$env:WB_BASELINE_ON_STARTUP="false"
$env:WB_MONITORING_ENABLED="false"
$env:WB_PROXY_HEALTH_CHECK_ENABLED="false"
.\gradlew.bat bootRun
```

## Запуск через Docker

Сборка JAR и запуск контейнеров:

```powershell
.\scripts\docker-run.ps1
```

Ручной запуск через Docker Compose:

```powershell
.\gradlew.bat clean bootJar -x test
docker compose -f docker-compose.jar.yml up --build -d
```

Проверка контейнеров:

```powershell
docker compose -f docker-compose.jar.yml ps
```

Логи приложения:

```powershell
docker compose -f docker-compose.jar.yml logs -f app
```

Остановка:

```powershell
docker compose -f docker-compose.jar.yml down
```

## Автотесты

Запуск всех тестов:

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Запуск демонстрационных тестов для отчета:

```powershell
.\gradlew.bat test --tests "*Diploma*" --no-daemon --console=plain
```

HTML-отчет Gradle:

```text
build/reports/tests/test/index.html
```

Основные тестовые классы:

- `DiplomaForecastLayerTest` - проверка MLP-модели прогнозирования;
- `MultiLayerPerceptronRegressorTest` - проверка регрессора;
- `DiplomaWebInterfaceTest` - проверка авторизации и web-сценариев;
- `DiplomaDataCollectionReliabilityTest` - проверка слоя сбора данных;
- `WildberriesResponseParserTest` - проверка парсинга ответа WB;
- `CookieFileLoaderTest` - проверка загрузки cookie;
- `DataTransferServiceSmokeTest` - проверка импорта/экспорта данных.


## Прогнозная модель

В проекте используется компактная MLP-модель:

```text
Вход: 202 признака
Скрытый слой 1: 40 нейронов
Скрытый слой 2: 20 нейронов
Выход: 3 значения
```

Выходные значения:

- рекомендуемая ставка за отзыв;
- срок до/период реализации промо-сценария;
- ожидаемый остаток товара.

Размер входа считается так:

```text
24 временные точки * 8 признаков + 10 агрегированных признаков = 202 признака
```

Количество параметров модели:

```text
202 * 40 + 40 = 8120
40 * 20 + 20 = 820
20 * 3 + 3 = 63

Итого: 9003 параметра
```

Даже если у товара больше 24 исторических снимков, вход модели остается фиксированным. История равномерно сэмплируется до 24 временных точек, а полная история дополнительно используется для агрегатов: средняя цена, средний остаток, средняя ставка, изменение цены, изменение остатка и диапазон цены.

Текущие параметры обучения:

```text
Эпох: 240
Batch size: 16
Максимум обучающих примеров: 1800
Learning rate: 0.008
L2-регуляризация: 0.0007
```

Для скорости прогноза приложение не использует весь каталог WB. Оно выбирает релевантных кандидатов по категории, родительской категории, цене и общей выборке. Это ограничивает время ответа и делает прогноз пригодным для интерактивной работы продавца.

## Основные маршруты

Публичные:

- `/` - стартовая страница;
- `/login` - вход;
- `/register` - регистрация продавца.

Продавец:

- `/app` - кабинет продавца;
- `/app/products` - товары продавца;
- `/app/products/new` - добавление товара;
- `/app/forecasts` - прогнозирование;
- `/app/profile/edit` - профиль продавца.

Администратор:

- `/admin` - административная панель;
- `/admin/users` - пользователи;
- `/admin/offers` - предложения WB;
- `/admin/products` - товары продавцов;
- `/admin/forecasts` - нейропрогнозы;
- `/admin/data-quality` - качество данных;
- `/admin/diagnostics` - диагностика, сбор данных, импорт и экспорт.

API администратора:

- `/api/parser/wildberries/rubles-for-reviews/import` - импорт акций WB;
- `/api/parser/wildberries/diagnostics` - диагностика парсера;
- `/api/parser/wildberries/discovery/scan` - сканирование каталога;
- `/api/parser/wildberries/monitoring/run` - мониторинг товаров;
- `/api/parser/wildberries/product/test` - тест получения данных товара.

## Импорт и экспорт данных

В интерфейсе администратора импорт и экспорт доступны в разделе:

```text
/admin/diagnostics
```

Backend-маршруты:

```text
GET  /admin/data/export
POST /admin/data/import
```

Импорт поддерживает большие архивы. Лимиты по умолчанию:

```text
DATA_IMPORT_MAX_FILE_SIZE=2GB
DATA_IMPORT_MAX_REQUEST_SIZE=2GB
```

## Настройки Wildberries

Основные переменные окружения:

```text
WB_COOKIE_FILE=cookie.txt
WB_PROXY_FILE=proxies.txt
WB_PROXY_ENABLED=true
WB_CONTINUOUS_SCAN_ENABLED=true
WB_MONITORING_ENABLED=false
WB_BASELINE_ON_STARTUP=true
WB_SCAN_PARALLELISM=20
WB_MONITOR_PARALLELISM=20
WB_REQUEST_TIMEOUT=20s
WB_REQUEST_DELAY=400ms
```

Для защиты и записи демонстрационного видео обычно удобнее отключить фоновые задачи, чтобы поведение сайта было стабильным:

```powershell
$env:WB_CONTINUOUS_SCAN_ENABLED="false"
$env:WB_BASELINE_ON_STARTUP="false"
$env:WB_MONITORING_ENABLED="false"
$env:WB_PROXY_HEALTH_CHECK_ENABLED="false"
```

## Рекомендуемый сценарий демонстрации

1. Открыть стартовую страницу `/`.
2. Показать вход администратора.
3. Открыть админ-панель `/admin`.
4. Показать пользователей и карточку продавца.
5. Показать каталог предложений WB и многоуровневые категории.
6. Показать качество данных и диагностику.
7. Выйти и войти под продавцом `seller_demo`.
8. Открыть кабинет продавца `/app`.
9. Показать товары продавца.
10. Добавить новый товар по артикулу Wildberries.
11. Открыть прогнозирование.
12. Рассчитать прогноз с бюджетом 13 000-15 000 рублей.
13. Показать появление результата в истории прогнозов.
14. Вернуться в админку и показать, что прогноз виден администратору.

Для демонстрации прогноза лучше выбирать товар с ненулевым остатком, нормальной базовой ценой и категорией, по которой уже есть исторические данные. Практичный бюджет для видео: `13 000-15 000 ₽`.