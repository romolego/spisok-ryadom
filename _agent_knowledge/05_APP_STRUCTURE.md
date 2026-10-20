## Карточка товара0.19 ·20 октября2026

ProductShopSettings.kt: ProductShopDialog (ограниченный диалог с прокруткой тела), ProductOutletOptions (null/all, subset, независимый priority). ProductCardViewModel.configureShop — единое raw-изменение настроек магазина. BranchPhotoEditor.compact включается только карточкой товара. Entity/Room17/ZIP13 прежние; старые таблицы групп по точкам оставлены для совместимости. См.45.

## Карточка магазина0.18 ·20 октября2026

Room17 добавляет в shops optional iconKey/logoUri. ShopSymbol используется в списке магазинов, заголовках покупок и preview. Полная копияZIP13 переносит PNG логотипа; прежние данные/ID сохраняются. Подробнее44.

## Организация проекта и публикация ·20 октября2026

Текущее связное описание слоёв, сущностей и потоков находится в41_ARCHITECTURE_ATLAS.md; таблица файлов и правила проверки —42_MAINTENANCE_GUIDE.md. Исторические уточнения ниже сохраняют причины эволюции.

## Уточнение0.17.0

CreationPresetEntities/Dao, Room16. StandardCategories устанавливает30+30 через SupportSQLiteDatabase при onCreate/миграции и явный Settings command. ProductCreationDefaults хранит общий тип и fallbackединицу в shopping_list_prefs. ProductCard raw сериализует происхождение ручного recipient/type; существующие сырые записи сохраняют прежний явный выбор. Группа/магазин правила живут вFK-таблицах, а не строковых ключахID в preferences. ZIP12 переносит правила.

## Уточнение0.16.0

ProductEditingPreferences хранит autosave_product_cards в shopping_list_prefs. Raw ProductEditDraft.autoPersist(default true для старых записей) отличает восстанавливаемые автокарточки от временной тени ручного редактора. Только автокарточки восстанавливаются. VoiceSearchButton разделяется поиском каталога, каталога магазина и подбором товара целевого списка. Route ProductCard принимает URI-encoded initialName; отдельный ключ черновика поискового названия не подменяет незавершённый обычный новый товар.

## Аудит0.15.1 —19 октября2026

GoalItemField mask атомарно разделяет изменённые поля и текущие DB facts; ProductEditorTransactions.inGeneration защищает очереди команд от полной замены базы. MessageShareIntent — общий UI helper короткого текста/большого UTF-8 вложения. Room15/ZIP11 без новой миграции.

## 0.15.0 — модель списков

GoalListEntity/Dao/Repository, Room15 и миграция14→15. ShoppingListEntryEntity: listId FK CASCADE nullable, plannedUnitPrice nullable, customShopName/customShopAddress nullable. Общая DAO-проекция включает null-владельца и showInPurchases; полный backup читает все строки. Merge сравнивает listId/цену/текстовое место. UI goallists: hub/detail, редактор, каталог, текст/SAF, preview; единый перенос MoveToListSheet. GoalListTransfer — versioned text/JSON с независимыми DTO без local IDs. ZIP11 хранит списки и новые поля; старые форматы читаются с null/default. Подробности37_GOAL_LISTS_150.md.

## Уточнение0.14.0 —19 октября2026

AddToListViewModel наблюдает каталог, связи, доступность точек и preference предварительного списка. AddProductCandidates вычисляет доступные алфавитные/поисковые результаты. ShoppingTextViewModel имеет только чтение снимка, временный текст и перезагрузку; BulkScreen отправляет видимый текст. Прежние BulkViewModel/ShoppingTextParser перенесены в test-only harness: в APK их нет. Room14/ZIP10 без новой миграции; виджет неизменен. Подробности:36_ADD_BROWSE_SHARE_140.md.

## Изменения0.13.0

ShopEntity.shoppingBackground — необязательный сериализуемый цвет; Room14, пустая строка по умолчанию, MIGRATION_13_14 сохраняет все прежние строки/ID/связи. ZIP10 читает прежние форматы. При преобразовании рынка новые профили наследуют цвет. AppearanceColors — общий словарь RGB/preset для формы/валидации/backup.

WidgetSettings хранит отдельные widget_* ключи в shopping_list_prefs; WidgetFrame атомарно объединяет настройки и WidgetItem.Purchase/Header. Header имеет отрицательный стабильный ID и только переход в приложение; Purchase использует прежний CAS. ShoppingWidgetData строит снимок одной транзакции Room с общими классификаторами, маршрутами и контекстом рынка. Таблицы/настройки уведомляют коллекции. Текущее состояние и доказательства —01/35.

## Уточнение0.10.0: приоритет физической точки товара

Room13 добавляет ProductBranchPriorityEntity(productId,branchId,shopId,displayOrder) с составными FK на ProductShopLink и ShopBranch. Это отдельное отношение порядка; доступность ProductBranchAvailability и общий ShopBranch.displayOrder не перегружаются. AssignmentCatalogue.outlet использует продуктовый порядок, затем общий order/id; Exact по-прежнему валидирует доступность, Unassigned остаётся отдельным выбором.

ProductCardUiState/ProductEditDraft сериализуют Map<shopId,List<branchId>>; загрузка/автосохранение включают порядок. saveWithLinks атомарно записывает его и пересчитывает pending при изменении маршрута; повторная запись заметки не переносит допустимые покупки. Старые невалидные назначения исправляются при изменении связей/availability. Явная смена приоритета товара применяется к его pending в их прежних городах, bought не меняется.

movePending принимает rememberPriority/extendAvailability. В одной транзакции проверяет весь набор, добавляет отсутствующие связи/доступность, переносит строки и при включённой настройке поднимает магазин/точку только этих товаров. Дополнительные места строятся из магазинов активного города, без подмены текущей доступности. New store создаётся явным inline действием; отмена до переноса не создаёт связи товара и не двигает покупку.

BackupSnapshot/ZIP9 включают productBranchPriorities; validate проверяет уникальные product/branch, неотрицательный order и совпадение владельцев. Старые форматы дают пустой порядок. Миграция12→13 добавляет пустую таблицу и индексы. Более ранние Room12/ZIP8 ниже — история. Детали30.

## Актуальное уточнение0.9.0

ProductEditDraft хранит сырые поля ProductCardUiState с UUID, исходным контекстом, links/pendingDepartments, branchSelections/overrides. ProductDraftStore использует локальные SharedPreferences product_card_drafts; незавершённые значения не меняют схему Room12/ZIP8. ProductRepository.saveEditorProduct транзакционно находит новую запись по UUID, проверяет идентичность существующей и дубли имени, затем вызывает общий saveWithLinks. Adopt существующего товара атомарно удаляет собственную новую строку только без покупок и добавляет связи выбранному товару.

ProductCardViewModel наблюдает изменения, сохраняет raw draft и сериализует debounce400мс записи общим ProductEditorTransactions.mutex. Back принудительно flush; onCleared сохраняет последний raw state. Save/Back/Delete/recover/полное восстановление не выполняют параллельные editor commits. Generation инвалидирует редакторы после успешного restore, чтобы устаревший экран не переписал восстановленные данные. Запоздалый autosave после Delete не воскрешает товар. Startup восстанавливает валидные raw drafts идемпотентно; ошибочные сохраняет для редактирования. PhotoStore защищает фото локальных draft от уборки.

SavedStateHandle focusProductId возвращает результат в исходный каталог/магазин; UI вычисляет индекс с заголовками и отделами, раскрывает нужную секцию и прокручивает к строке. Favorites — read model дополнительной секции, не отдельные entities. ShopListGroup — read model venue→shops над прежней нормализованной моделью; владение точками не меняется. Детали29. Старые утверждения о «все изменения карточки только до Save» ниже относятся к прошлым версиям.

# 05_APP_STRUCTURE

## Основа модели0.6.0 — Room11, расширенная в0.7.0 до Room12

Текущее уточнение: сохранённые настройки отдела/стандартной единицы обновляют некупленное; явный выбор без магазина отделён от ещё не настроенного автоматического выбора. Метаданные оформления и приоритет общего справочника добавлены в12. Новые правила — в разделе0.7.0 ниже и26_CATALOGUE_CLASSIFIERS.md; структура сети/комплекса/физической точки0.6.0 сохраняется.

Исторические описания ниже показывают развитие; для новых изменений использовать этот раздел и24_ARCHITECTURE_060.md.

- RetailNetworkEntity — общая сеть без привязки к городу.
- ShopEntity — локальный профиль продавца в городе, optional networkId. У города есть FK RESTRICT. Это не торговый комплекс.
- VenueEntity — рынок/ТЦ в городе. ShopBranchEntity — единственная физическая точка продавца, независимо optional venueId. Одна точка одновременно относится через Shop к сети и напрямую к Venue. City consistency защищена репозиторием и SQLite triggers.
- DepartmentTypeEntity — общая группа со стабильным ID, именем и нормализованным ключом создания. ShopDepartmentPlacementEntity — её локальное размещение/порядок. ShopDepartmentEntity теперь joined read DTO, не таблица с повторяющимися именами. Rename сохраняет type ID; название меняется в одном месте.
- BranchDepartmentOrderEntity — порядок общей группы в конкретной физической точке; отсутствие означает наследование маршрута продавца. ProductBranchDepartmentEntity — группа товара в конкретной точке, с FK на владельца-привязку/точку/тип.
- ProductBranchAvailability содержит shopId; composite FK на product_shop_links(productId,shopId) и shop_branches(id,shopId). Отвязка каскадно удаляет своё подмножество, покупка остаётся отдельным фактом. saveWithLinks обновляет существующие связи с сохранением ID.
- PurchaseAssignment/AssignmentCatalogue — чистая политика с typed Automatic/Unassigned/Exact, единый индексированный снимок на пакет/preview. У покупки фиксированы назначенные значения; изменение только количества/заметки их не пересчитывает.
- Product.preferCityShopOrder: старые записи false сохраняют свой приоритет; новые формы true используют порядок магазинов города. «Свой порядок магазинов» включает продуктовый override. Временный выбор текущей покупки не меняет каталог.
- MediaLifecycle собирает все ссылки обоих владельцев. UUID-файлы неизменяемы; export pin/lease защищает снимок. ZIP I/O выполняется после короткой DB-транзакции. Прямое удаление общего photoUri из VM исключено.

Shop.address оставлен только для старых копий/совместимости; рабочие адреса берутся из физических точек. Compose/Room/AppContainer сохранены. UI не обращается к репозиторию из MovePurchasesDialog. ZIP7 читает4–6 и JSON1–3; все новые связи входят в полную копию. Миграция10→11 сохраняет ID товаров, покупок, магазинов, отделов, точек и связей; удаляет только прежнюю осиротевшую availability. Перед первым обновлением AppDatabase сохраняет recovery checkpoint старой БД/WAL/медиа/настроек в files/recovery/schema-10-before-11 (не механизм установки старого APK поверх новой схемы).

## История до архитектурной итерации0.6.0

**Назначение:** зафиксировать каркас приложения, центральные сущности и ключевые связи, чтобы агент не ломал модель при следующих изменениях.
**Когда читать:** при работе с моделью данных, экранами, навигацией, формами и базовыми пользовательскими сценариями.
**Когда обновлять:** если меняются центральные сущности, связи между ними, минимальный каркас первой версии или обязательные экраны.

## Центральные сущности приложения

### Карточка товара

Главная переиспользуемая сущность. Хранит информацию о товаре, который затем многократно добавляется в текущий список.

### Магазин

Отдельная сущность для хранения торговых точек, порядка их прохождения и связанного контекста.

### Отдел магазина (в UI — группа магазина)

Зависимая сущность, привязанная к конкретному магазину. Нужна для сценариев прохождения магазина по внутреннему маршруту.
Во внутренней модели и БД используется термин «отдел», а в пользовательском интерфейсе — более универсальный термин «группа магазина».

### Связь товара с магазином

Отдельная сущность, потому что один товар может покупаться в нескольких магазинах. В этой связи хранится доступность товара в магазине, приоритет магазина и, при необходимости, отдел внутри этого магазина.

### Запись текущего списка

Рабочая запись в одном общем текущем списке. Она всегда ссылается на карточку товара и содержит конкретный контекст текущей покупки: количество, статус, срочность, назначенный магазин и заметку.

### Пользовательские группы товаров

Пользовательские классификаторы для группировки товаров. Они не должны быть жёстко зашиты в приложение. В этой базе знаний формулировка «пользовательские группы» означает именно пользовательские группы товаров, то есть товарные группы.

### Получатели

Пользовательские сущности для фиксации того, для кого покупается товар.

### Единицы измерения

Справочник `UnitEntity` с собственным порядком `displayOrder`. Таблица `units` добавлена миграцией Room 6→7; значения в товарах и записях списка остаются строковыми, без внешнего ключа на справочник.

## Ключевые связи между сущностями

- Один товар может быть связан с несколькими магазинами.
- Один магазин может содержать много отделов.
- Одна запись текущего списка всегда ссылается на один товар.
- Запись текущего списка может иметь назначенный магазин и отдел, но они не обязательны для каждой записи.
- У товара есть пользовательская группа товаров и получатель.
- В первой версии существует только один общий текущий список; параллельные текущие списки не вводятся.

## Минимальный каркас первой версии

Для каркаса первой версии обязательны:
- база карточек товаров;
- база магазинов;
- отделы магазинов как часть модели;
- связь товара с магазином;
- один общий текущий список покупок;
- пользовательские группы товаров;
- получатели.

## Какие экраны обязательны для каркаса

Минимально обязательные экраны:
- текущий список покупок;
- база товаров;
- карточка товара;
- список магазинов;
- карточка магазина с управлением группами магазина (технически: отделами).

## Техническая реализация (первая итерация)

### Пакетная структура

```
com.spisokryadom.app/
  SpisokRyadomApp.kt, AppContainer.kt, MainActivity.kt
  navigation/ — Routes.kt, NavGraph.kt
  data/db/ — AppDatabase.kt
  data/entity/ — 8 Room-сущностей
  data/dao/ — 8 DAO-интерфейсов
  data/repository/ — ProductRepository, ShopRepository, ShoppingListRepository, ClassifierRepository
  data/ — DemoDataProvider.kt (старый поставщик демо; автоматический вызов отключён)
  ui/theme/ — Theme.kt, Color.kt, Type.kt
  ui/shoppinglist/ — ShoppingListScreen.kt, ShoppingListViewModel.kt (ViewMode, EntryGroup, ShoppingListEntryUi, ShoppingListUiState)
  ui/productdb/ — ProductDatabaseScreen.kt, ProductDatabaseViewModel.kt (ProductViewMode, ProductSection, ProductDatabaseUiState)
  ui/productcard/ — ProductCardScreen.kt, ProductCardViewModel.kt (ShopLinkUi, PendingShopLink, ProductCardUiState)
  ui/shoplist/ — ShopListScreen.kt, ShopListViewModel.kt
  ui/shopcard/ — ShopCardScreen.kt, ShopCardViewModel.kt
  ui/addtolist/ — AddToListScreen.kt, AddToListViewModel.kt (AddToListUiState)
  ui/shopcontent/ — ShopContentScreen.kt, ShopContentViewModel.kt
  ui/settings/ — SettingsScreen.kt, SettingsViewModel.kt
  ui/components/ — CommonComponents.kt (ConfirmDialog, InputDialog, DropdownSelector, QuantityUnitInput, QuantityUnitInputWithSuggestions, PurchaseTypeSelector, SectionHeader)
  util/ — FuzzySearch.kt (приблизительный поиск по названиям)
```

### Room-сущности и их поля

- **ProductEntity** (products): id, name, productGroupId?, recipientId?, defaultUnit?, defaultQuantity?, note?, purchaseType, sellerUrl?, productUrl?, photoUri?, priceValue?, priceDate?, isTemporary
- **ShopEntity** (shops): id, name, address?, note?, displayOrder, showUrgentInCollapsed?, boostUrgentShop?, manualOrderOverride
- **ShopDepartmentEntity** (shop_departments): id, shopId (FK → shops, CASCADE), name, displayOrder
- **ProductShopLinkEntity** (product_shop_links): id, productId (FK → products, CASCADE), shopId (FK → shops, CASCADE), priority, departmentId? (FK → shop_departments, SET_NULL)
- **ShoppingListEntryEntity** (shopping_list_entries): id, productId (FK → products, CASCADE), quantity, unit, assignedShopId? (FK → shops, SET_NULL), assignedDepartmentId? (FK → shop_departments, SET_NULL), isBought, isUrgent, note?, comment?, createdAt, updatedAt
- **ProductGroupEntity** (product_groups): id, name, displayOrder
- **RecipientEntity** (recipients): id, name, displayOrder
- **UnitEntity** (units): id, name, displayOrder

### Навигация

- Bottom bar: Список (ShoppingList), Товары (ProductDatabase), Магазины (ShopList)
- Detail screens: ProductCard(productId, preselectedShopId?), ShopCard(shopId), AddToList
- -1L означает создание нового объекта
- preselectedShopId — опциональный параметр для автоматической привязки магазина при создании товара из экрана магазина
- AddToList — самостоятельный экран быстрого добавления позиции в текущий список, вызывается по FAB «+» с главной

## Что можно достраивать позже

В общей концепции уже присутствуют история изменений списка и справочник «Не покупать», но они не являются текущим центром минимального каркаса первой практической реализации. Их нужно учитывать как согласованный контекст, но не тянуть раньше времени в базовый Android-скелет.

## Текущие дополнения, 17 октября 2026

Актуальная схема — Room 8: `CityEntity/cities`, `ShopEntity.cityId`, уникальная `ProductEntity.stableId`, уникальная пара productId/shopId. City FK проверяется командами репозиториев и валидатором импорта, старые таблицы магазинов не пересоздавались. Внешние ключи старых сущностей сохранены.

`LocationRepository` управляет городом и активными магазинами; `ShoppingListRepository` — единственная бизнес-логика добавления, атомарных количеств, пакета/отмены и переноса некупленных. `ProductRepository.saveWithLinks` и `ShopRepository.saveWithDepartments` сохраняют карточки транзакционно. `ClassifierRepository` содержит согласованные операции переименования единиц и порядка.

Новые компоненты: `CityPicker`, `ui/bulk/BulkScreen/BulkViewModel`, `ShoppingTextParser`, `Quantities`, `PhotoStore`, `BackupJson`. Резервная копия: streaming ZIP v4 вместо JSON с Base64 всего каталога в памяти; JSON v1–3 остаётся импортом каталога. Тесты и fixtures находятся в `app/src/test`, Room schema в `app/schemas`.

Build flavors standard/parallel различаются applicationId и названием; UI/логика общие. Детали текущего состояния и проверки: `01_CURRENT_STATE.md`, `16_IMPLEMENTATION_VERIFICATION.md`.

## Уточнения навигации и текста — 0.2.2

Город меняется в Settings через CityPicker. ShoppingAddButton: short click → AddToList, long click → меню → BulkScreen. В основном toolbar CityPicker отсутствует. NavHost потребляет outer padding, внешняя Scaffold не добавляет системные отступы повторно; редакторы потребляют padding собственной Scaffold перед imePadding.

ParsedPurchase хранит shopName заголовка раздела. DraftLine хранит requestedShop, shopId, shopResolved и shopMatches; список до записи остаётся черновиком без изменения БД. BulkViewModel сопоставляет магазины снимка reviewCity через LocationRepository.shopsForCity, подтверждает неоднозначность, формирует отдельный PurchaseRequest каждой строки. Schema8 и формат backup не менялись. Текущая проверка:19_UI_REFINEMENT_VERIFICATION.md.

## Room9 и второе представление — 0.3.0

ShopEntity остаётся сетью/магазином города, ShopBranchEntity(shop_branches) — её адресом с displayOrder. Старое address совместимо с предыдущим форматом и отражает основную точку. ProductShopLinkEntity.restrictBranches + ProductBranchAvailabilityEntity(product_branch_availability, productId/branchId) задают выбранные точки; false означает всю сеть. При true нужен хотя бы один доступный адрес. Ссылки проверяются репозиториями и валидатором копии. У ShoppingListEntryEntity nullable assignedBranchId с FK SET_NULL и индексом; merge сравнивает также адрес.

ShoppingListRepository.resolveBranch/movePending/moveOptions отвечают за точки и атомарный перенос. textSnapshot/replacePendingText синхронизируют только некупленное с проверкой исходного снимка. PurchaseRequest передаёт entryId/branchId; BatchReceipt хранит before/after для undo. BulkViewModel(textView=true) обслуживает постоянный текстовый вид; прежний bulk API(defaultfalse) сохранён для совместимости/регрессий. Парсер понимает заголовки сети/адреса, явную запись «Название — число единица».

GroupExpansion/rememberGroups хранят свёрнутые ID в существующих shopping_list_prefs/product_db_prefs, включённых в backup. BranchEditors и MovePurchasesDialog — общие UI-компоненты. Миграция8→9 добавляет таблицы, переносит адреса и пересоздаёт shopping_list_entries с сохранением полей/FK. Backup ZIP5, чтение старых ZIP4; исходные JSON1–3 не содержат новых данных точек.

## Карточки и controls — 0.3.1

BranchesEditor хранит editingId по ID точки; поле адреса в самой карточке, key сохраняет идентичность при reorder. Удаление и reorder только для редактируемой точки. Добавление создаёт черновик с уникальным отрицательным ID; пустой адрес блокирует общий save. Группы магазина в режиме правок имеют inline поля и единый renderer для сохранённых/новых групп; их пустое имя блокирует save. Изменения остаются в ShopCardViewModel до saveWithDepartments. Общий ReorderButtons используется также в перечне магазинов, связях товара, справочниках настроек и заголовках корзины. Схема Room9/ZIP5 не изменены.

## Точки, группировка и повторное использование — 0.4.0

ShopBranchEntity дополнен note/photoUri (nullable). Room10: ALTER двух nullable TEXT, миграция9→10 сохраняет ID/связи/покупки. Фото в отдельном branch_photos, PhotoStore импортирует/масштабирует/поворачивает по EXIF; FileProvider выдаёт только camera_captures из cache. BranchPhotoEditor выбирает файл/камеру, BranchPhotoPreview декодирует в IO. Изменения note/photoUri остаются черновиком ShopCardViewModel до общего save.

ShopContentUiState.sections: точка/название/заметка/фото/список товаров. Ограниченные товары в отмеченных точках, неограниченные один раз в «Во всех точках». Одна позиция может присутствовать у нескольких доступных точек. Контролы корзины в разделе относятся к конкретному branchId; addProduct получил последний optional branchId. Ключи LazyColumn включают point/link, сворачивание сохранено в product_db_prefs по shop/point ID.

ProductCardViewModel сопоставляет имя с общим каталогом в фоне с debounce120ms; до8 fuzzy-кандидатов. ensureShopLinks транзакционно добавляет недостающие связи существующего ID, не трогает его данные или прежние приоритеты. New-name exact совпадение блокирует обычный save до inline-действия «Создать отдельный товар». При входе из магазина выбор точек поднят под имя. ZIP6 упаковывает branch_photos/id.jpg, note и ограничения; читает ZIP4/5, legacyJSON1–3 остаётся merge каталога.

## Привязка товара и движения — 0.5.0

ProductCardUiState.pendingDepartments хранит новые ShopDepartmentEntity с отрицательными ID. ProductRepository.saveWithLinks разрешает только использованные черновики, повторно использует нормализованное имя в том же магазине, записывает товар/группы/связи/доступность в одной Room-транзакции. ensureShopLinks принимает новые draftLinks, группы и точки; прежние ID/приоритеты/метаданные не переписываются. При закрытии формы черновые группы не остаются в БД.

BranchAvailabilityEditor используется в привязке и основной карточке; переключатель только если точек больше одной. Null/отсутствующий ключ — все точки, Set ID — ограничение. Пустое ограничение не сохраняется. Строка целиком переключает checkbox/switch. Выбор при привязке становится branchSelections, дальнейшее редактирование использует те же данные.

AnimatedOrderColumn хранит Animatable по ID и анимирует placement измеренных строк, включая разные высоты и обратное движение. LazyColumn использует animateItem и ReorderViewport для явных перестановок. Ключи остаются ID, принудительная прокрутка к переставляемой строке удалена. ProductContext получает словарь метаданных из потоков каталога/связей/магазинов; запросов к БД на каждую строку нет.

Общий BranchPhotoEditor параметризован заголовком/директорией для товара и точки. Фото товара импортируется в product_photos, точки — branch_photos, оба уже входят в ZIP6. Room10/ZIP6 без миграции в этой итерации.

## Проекция физических мест0.6.1

Модель хранения0.6.0 сохраняется. EntryGroup.groupId остаётся идентификатором продавца для команд, branchId/address определяют физический блок, placeKey=shopId:branchId — его стабильный ключ. Не подменять shopId идентификатором точки. BY_SHOPS группирует покупки по этой паре, затем по локальным группам. Ни карточки товаров, ни продавцы не дублируются ради представления.

ShoppingListRepository.purchasePlaces возвращает typed PurchasePlace(shopId,branchId,shopName,address) по текущему городу, каталожным связям и пересечению allowed branch IDs. movePending повторно проверяет допустимость под транзакцией. clearBoughtInPlace работает с точной парой. Copy использует идемпотентный ProductRepository.ensureShopLink, без записи покупок.

В старом StringSet collapsed_shops числовые ID сохраняют смысл наследуемого состояния продавца; open:shop:branch/closed:shop:branch задают явное состояние точки. При восстановлении ZIP остаются непрозрачными строками прежнего формата настроек. Room11 и ZIP7 менять не потребовалось. Навигация AddToList передаёт optional preselectedBranchId до команды репозитория.

Команда updatePurchaseDetails принимает только количество/заметку/комментарий и обновляет текущую запись под транзакцией, без старого shopId из UI. Явная перестановка физических точек включает manualOrderOverride продавца; при равных displayOrder сохраняется алфавитный порядок остальных продавцов.

## Уточнение0.7.0 — Room12

Добавочные поля без универсальной графовой сущности: Product.isFavorite/accent; ProductGroup/Recipient.accent/iconKey; DepartmentType.displayOrder/accent/iconKey. Нейтральные defaults сохраняют прежнее оформление. Глобальный порядок DepartmentType — порядок справочника, не перезапись маршрутов ShopDepartmentPlacement/BranchDepartmentOrder.

refreshPending выполняется внутри saveWithLinks: отдел вычисляется по действующей паре shop/branch и текущему point override, стандартная единица обновляется только у пустой/совпавшей с прежним default. Пропавшее доступное место перевыбирается только у некупленного, в прежнем городе; существующее допустимое место сохраняется. Купленное неизменно. Unit rename/clear обновляет только некупленные соответствующие единицы. OutletChoice.Unassigned (UI sentinel0) означает отсутствие точного адреса; Preferred (null/-1) выбирает приоритетный.

Venue grouping — read model одной поездки над прежними физическими группами, без дублирования покупок/переноса владения. Состояние сворачивания venue сохраняется независимо от точек. ZIP8 включает новые поля, читает4–7 и старый JSON. Recovery checkpoint теперь перед12. Миграция11→12 сохраняет ID/FK/покупки, дополняет метаданные и исправляет известные старые проекции некупленного.

Ненастроенное автоматическое назначение хранит awaitingShopCityId: при последующей настройке карточки выбирает магазин первоначального города. Явное «Без магазина» хранит null и не назначается автоматически. Такие разные намерения не сливаются в одну покупку. Для старых записей неизвестное намерение не угадывается; nullable hint мигрирует нейтральным. Это контекст подбора, не владение города покупкой; после явного переноса очищается.

explicitNoShop отдельно хранит явный выбор, чтобы не путать его с нейтральным старым NULL. Старые записи могут объединяться с новым автоматическим добавлением при однозначном городе и совместимых фактах/единицах; разные города и явное «Без магазина» с автоматическим назначением не сливаются.

## Инварианты команд после аудита0.7.1

Общий ProductRepository.refreshPending вызывается всеми командами удаления привязки. ShopRepository.saveWithDepartments принимает convertToVenue=false; true сохраняет черновик и преобразует внутри той же Room-транзакции. После удаления точки/локального отдела сравнивает снимок pending до операции и корректирует только изменившиеся FK-проекции; неизменённые покупки и timestamps сохраняются. ClassifierRepository сопоставляет строковые единицы канонически. ProductDatabaseViewModel слушает SharedPreferences. Схема12/формат8 прежние. Детали27.

Активный import staging регистрируется directory lease в PhotoStore под MediaLifecycle.mutex; cleanup пропускает leased-поддерево. finally снимает защиту в NonCancellable под тем же mutex. Передача ответственности lease→DB refs не допускает устаревшего cleanup snapshot. Счётчики pin/unpin атомарны по ключу.

deleteDepartment/deleteDepartmentGlobally используют тот же ограниченный refresh после FK-отвязки; удалённый point override наследует действующий базовый отдел связи. Купленные получают только неизбежный FK SET_NULL удалённого справочного ID.

## Оформление и ресурсы0.8.0

Название берётся из main/parallel string resources, без текстового manifest placeholder в Gradle. accent:String совместим с прежними именованными цветами и непрозрачным #RRGGBB; общий renderer используется всеми потребителями. Набор значков60 имеет неизменённые прежние ключи, новые семантические ключи и6 оригинальных ImageVector. Изменения не требуют Room/backup migration. Личная база присутствует только в тестовых ZIP fixtures и выдаваемом файле, в main assets её нет.

Выбор адреса BranchChoiceField следует тому же порядку: реальные адреса, «По приоритету для каждого товара», «Без конкретного адреса», «Новый адрес…». Проверены порядок и различия ID конкретной точки/нулевого адреса наAPI28/35.

## Виджет и общие подписи0.11.0

ShoppingWidgetProvider/ShoppingWidgetService/RemoteViewsFactory — Android-проекция той же Room-корзины. ShoppingWidgetData читает транзакционный снимок и выполняет compare-and-set через существующие markBought/markNotBought команды. Токен содержит entry ID, product UUID, createdAt/updatedAt/bought и SHA256 полного entry; расходящийся или удалённый снимок не меняет покупку. Сервис закрыт BIND_REMOTEVIEWS, receiver не экспортирован; mutable PendingIntent нужен только явно направленному шаблону коллекции, открытие/добавление immutable. Application invalidation observer покрывает purchases/products/shops/branches/venues; notify сохраняет прокрутку вместо постоянного пересоздания адаптера. Widget ID входит в data URI адаптера и шаблона.

WidgetIntents передаёт действия открытия/добавления в MainActivity; холодный onCreate и тёплый onNewIntent обрабатываются навигацией. Отдельной базы или самостоятельных списков нет. PlaceLabels — pure read model, LocalPlaceLabels обновляется по branch/venue Flow. getShopsForDisplay используется только для строк контекста кандидатов; возвращаемые презентационные копии никогда не сохраняются как ShopEntity. PurchasePlace и TextSnapshot включают venue context для тех же правил. Схема13/ZIP9 не изменены.


## Массовые операции0.12.0

BatchRepository — атомарные commands поверх Room13, без новой схемы. ProductBatchChange разделяет Recipient/Group/Favorite/Shops/Department(shop,type,point?). UI BatchViewModel и BatchSelection используют productId/shopId/entryId, а не отображённую строку. Синхронизация ProductEditDraft частичная под editor mutex. Политика pending/availability/истории прежняя; детали32.

## Общая граница внешних правок0.12.1

CatalogueEdits — контракт data/repository, ProductDraftSynchronizer — реализация над SharedPreferences raw drafts, инъекция общей реализации в AppContainer. Репозитории не зависят от Android UI. До/после успешной внешней транзакции снимаются канонические факты; только изменённые поля/связи переносятся в черновики. Общий ProductEditorTransactions.locked reentrant в coroutine context; nested repository commands не снимают промежуточные снимки внутри outer DB transaction. После начала мутации DB и prefs завершаются NonCancellable. Неправильный raw number/заметка не заменяются canonical values, nullable result не повторяет команду. Полное восстановление использует ту же границу редакторов, generation и неделимое завершение ownership медиа. Room13/ZIP9 не менялись.

ActiveProductDraft согласует external delta с живой ViewModel: JSON-структурное сравнение raw фактов, массивы связей по shopId, временные группы по ID, subset по разнице множеств. Сохраняются несвязанный локальный ввод и UI readmodel/flags. Перед пользовательским действием читается свежая raw версия; запись, init/finish и последнее закрытие — под общим lock. Listener только читает; собственная запись заранее становится merge-base, поэтому не запускает петлю переписывания.

DatabaseRevision — локальный UUID в отдельном SharedPreferences local_database_revision, не в transferable allowlist. После полного commit сохраняется новая revision. Общий BatchSelectionSaver сохраняет её вместе с ID, invalidation закрывает старые batch sheets; покупки пересоздают transient UI по revision. Числовые ID после restore не считаются доказательством прежней идентичности. Независимая AtomicLong generation по-прежнему защищает editor jobs текущего процесса. Room/ZIP не расширяются.

Преобразование магазина в рынок сохраняет физические branch IDs, продуктовый порядок выбранных точек и относительный порядок остальных продавцов. Общий городской порядок наследуется, если представим после разделения; иначе только затронутый товар получает материализованный маршрут. История/ID покупок сохраняются.
