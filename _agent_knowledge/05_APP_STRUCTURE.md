# 05_APP_STRUCTURE

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
  data/entity/ — 7 Room-сущностей
  data/dao/ — 7 DAO-интерфейсов
  data/repository/ — ProductRepository, ShopRepository, ShoppingListRepository, ClassifierRepository
  data/ — DemoDataProvider.kt (демо-данные для первого запуска)
  ui/theme/ — Theme.kt, Color.kt, Type.kt
  ui/shoppinglist/ — ShoppingListScreen.kt, ShoppingListViewModel.kt (ViewMode, EntryGroup, ShoppingListEntryUi, ShoppingListUiState)
  ui/productdb/ — ProductDatabaseScreen.kt, ProductDatabaseViewModel.kt
  ui/productcard/ — ProductCardScreen.kt, ProductCardViewModel.kt
  ui/shoplist/ — ShopListScreen.kt, ShopListViewModel.kt
  ui/shopcard/ — ShopCardScreen.kt, ShopCardViewModel.kt
  ui/components/ — CommonComponents.kt (ConfirmDialog, InputDialog, DropdownSelector, QuantityUnitInput, QuantityUnitInputWithSuggestions, PurchaseTypeSelector, SectionHeader)
```

### Room-сущности и их поля

- **ProductEntity** (products): id, name, productGroupId?, recipientId?, defaultUnit?, defaultQuantity?, note?, purchaseType, sellerUrl?, productUrl?, photoUri?
- **ShopEntity** (shops): id, name, address?, note?, displayOrder
- **ShopDepartmentEntity** (shop_departments): id, shopId (FK → shops, CASCADE), name, displayOrder
- **ProductShopLinkEntity** (product_shop_links): id, productId (FK → products, CASCADE), shopId (FK → shops, CASCADE), priority, departmentId? (FK → shop_departments, SET_NULL)
- **ShoppingListEntryEntity** (shopping_list_entries): id, productId (FK → products, CASCADE), quantity, unit, assignedShopId? (FK → shops, SET_NULL), assignedDepartmentId? (FK → shop_departments, SET_NULL), isBought, isUrgent, note?, createdAt, updatedAt
- **ProductGroupEntity** (product_groups): id, name
- **RecipientEntity** (recipients): id, name

### Навигация

- Bottom bar: Список (ShoppingList), Товары (ProductDatabase), Магазины (ShopList)
- Detail screens: ProductCard(productId), ShopCard(shopId)
- -1L означает создание нового объекта

## Что можно достраивать позже

В общей концепции уже присутствуют история изменений списка и справочник «Не покупать», но они не являются текущим центром минимального каркаса первой практической реализации. Их нужно учитывать как согласованный контекст, но не тянуть раньше времени в базовый Android-скелет.
