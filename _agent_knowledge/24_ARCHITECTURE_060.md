# 24_ARCHITECTURE_060

Архитектурная переработка после независимого аудита0.5.0. Подробная действующая модель:05_APP_STRUCTURE.md, правила и сценарии:02/06/07/08.

## Сохранение и переход

До правок сохранён before-0.5.0.zip (app/src,schemas,knowledge,Gradle/README), прежний подписанный APK остаётся в outputs. На устройстве до первого открытия Room для upgrade создаётся приватный recovery checkpoint старой БД с WAL/медиа/shared_prefs. BeforeOpenHelperFactory выполняет это при фоновом открытии, конструктор Application/container не копирует фотографии на UI-потоке; отдельный тест проверяет отложенность, поток и порядок до миграции. Это страховочная копия разработчика, а не обещание установки versionCode8 поверх схемы11.

Room10→11 сохраняет существующие ID и все покупки; нормализует общие группы и очищает только известные orphan availability. Старые рынки автоматически не угадываются. Для них есть явная атомарная команда преобразования: venue + продавцы-павильоны, прежние physical point/product/purchase IDs. Формат ZIP7 содержит новые роли, типы, маршруты и overrides;4/5/6 адаптируются на входе, JSON1–3 остаётся merge каталога.

## Покрытие аудита

- A01: composite ownership availability→link+branch, stable link IDs, unlink/re-add/export regression.
- A02: MediaLifecycle с обоими владельцами и каталогами, restored/aged/startup cleanup, удаление orphan, запрет VM-удаления общего файла.
- A03: city FK, cross-owner triggers, typed pure assignment policy, historical assignment semantics, UI query through VM.
- A04: global DepartmentType ID/name, local placements/order. Переименование сохраняет ID, local/global deletion собирает зависимые point settings.
- A05: RetailNetwork/City Shop/Venue/physical branch как разные роли, межгородская сеть и та же физическая точка внутри комплекса.
- A06: все pending purchases, явный ID/unit/context каждой; никаких сумм разных единиц или молчаливого выбора первой строки.
- A07: ProductContextIndex за O(P+L+S), фоновая подготовка; единый AssignmentCatalogue для пакета и preview. Проверка10000 товаров/30000 связей не является измерением FPS телефона.
- A08: preferCityShopOrder для новых товаров, явный продуктовый override, прежние порядки сохраняются. Профили адресов проживания отложены.
- A09: короткий transactional snapshot + file leases, ZIP вне DB transaction; блокируемый тестовый OutputStream проверяет параллельную реальную запись и сохранность экспортируемого фото.

## Проверка и границы

171/171 проверок; failures/errors/skipped=0. Release и lint BUILD SUCCESSFUL; lint0 ошибок/106 предупреждений. APK0.6.0 (9), schema11/ZIP7, прежний пакет и сертификат. SHA2567a917ae7e0f91d2312fd0d1a07bbbbaf8dafc724a4217f2aff68f8a907ef6bf1. Исходные 132 файлов совпадают со снимком начала итоговой проверки.

API28/35, настоящая Room/SQLite и native graphics Robolectric; физического телефона нет. Сохраняются все прежние функциональные регрессии0.5.0. Несохранённый черновик карточки при уничтожении процесса пока может потеряться; текстовый черновик и записанные данные устойчивы. Не заявлять абсолютную оптимальность/отсутствие любых багов.

## Knowledge review

Проверены AGENTS/router03, обязательные01/08/09/12/13 и профильные02/05/06/07/10/14. Обновлены01/02/05/06/07/08/09/11/13/14/README, добавлен24.12/10 без изменения правил. Прежние отчёты и поставки сохранены; push/PR не выполнялся.
