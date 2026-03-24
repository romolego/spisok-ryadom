package com.spisokryadom.app.data

import android.content.Context
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity

/**
 * Поставщик демонстрационных данных для первого запуска приложения.
 *
 * Демо-данные создают правдоподобный бытовой сценарий:
 * - несколько магазинов с отделами
 * - товары из разных групп, привязанные к магазинам
 * - текущий список с срочными и обычными позициями, часть купленных
 *
 * Чтобы отключить демо-данные: удалить вызов populateIfNeeded() из SpisokRyadomApp
 * или очистить SharedPreferences ключ DEMO_DATA_LOADED.
 */
object DemoDataProvider {

    private const val PREFS_NAME = "spisok_ryadom_prefs"
    private const val KEY_DEMO_DATA_LOADED = "DEMO_DATA_LOADED"

    fun isDemoLoaded(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEMO_DATA_LOADED, false)
    }

    suspend fun populateIfNeeded(context: Context, database: AppDatabase) {
        if (isDemoLoaded(context)) return
        populate(database)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEMO_DATA_LOADED, true)
            .apply()
    }

    /**
     * Сбросить флаг демо-данных (для повторного заполнения при следующем запуске).
     */
    fun resetDemoFlag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DEMO_DATA_LOADED)
            .apply()
    }

    private suspend fun populate(db: AppDatabase) {
        val groupDao = db.productGroupDao()
        val recipientDao = db.recipientDao()
        val shopDao = db.shopDao()
        val deptDao = db.shopDepartmentDao()
        val productDao = db.productDao()
        val linkDao = db.productShopLinkDao()
        val entryDao = db.shoppingListEntryDao()

        // === Товарные группы ===
        val gMilk = groupDao.insert(ProductGroupEntity(name = "Молочные продукты"))
        val gBread = groupDao.insert(ProductGroupEntity(name = "Хлеб и выпечка"))
        val gVegetables = groupDao.insert(ProductGroupEntity(name = "Овощи и фрукты"))
        val gMeat = groupDao.insert(ProductGroupEntity(name = "Мясо и рыба"))
        val gChem = groupDao.insert(ProductGroupEntity(name = "Бытовая химия"))

        // === Получатели ===
        val rAll = recipientDao.insert(RecipientEntity(name = "Для всех"))
        val rMom = recipientDao.insert(RecipientEntity(name = "Для мамы"))
        val rKids = recipientDao.insert(RecipientEntity(name = "Для детей"))

        // === Магазины ===
        val sPyat = shopDao.insert(ShopEntity(name = "Пятёрочка", address = "ул. Ленина, 15", displayOrder = 1))
        val sMagnit = shopDao.insert(ShopEntity(name = "Магнит", address = "пр. Мира, 42", displayOrder = 2))
        val sAshan = shopDao.insert(ShopEntity(name = "Ашан", address = "ТЦ Мега", displayOrder = 3))
        val sWb = shopDao.insert(ShopEntity(name = "Wildberries", note = "Онлайн-магазин", displayOrder = 4))

        // === Отделы Пятёрочки ===
        val dpMilk = deptDao.insert(ShopDepartmentEntity(shopId = sPyat, name = "Молочный", displayOrder = 1))
        val dpBread = deptDao.insert(ShopDepartmentEntity(shopId = sPyat, name = "Хлебный", displayOrder = 2))
        val dpVeg = deptDao.insert(ShopDepartmentEntity(shopId = sPyat, name = "Овощной", displayOrder = 3))
        val dpMeat = deptDao.insert(ShopDepartmentEntity(shopId = sPyat, name = "Мясной", displayOrder = 4))
        val dpChem = deptDao.insert(ShopDepartmentEntity(shopId = sPyat, name = "Бытовая химия", displayOrder = 5))

        // === Отделы Магнита ===
        val dmMilk = deptDao.insert(ShopDepartmentEntity(shopId = sMagnit, name = "Молочный", displayOrder = 1))
        val dmBake = deptDao.insert(ShopDepartmentEntity(shopId = sMagnit, name = "Выпечка", displayOrder = 2))
        val dmFruit = deptDao.insert(ShopDepartmentEntity(shopId = sMagnit, name = "Фрукты и овощи", displayOrder = 3))

        // === Отделы Ашана ===
        val daFood = deptDao.insert(ShopDepartmentEntity(shopId = sAshan, name = "Продукты", displayOrder = 1))
        val daChem = deptDao.insert(ShopDepartmentEntity(shopId = sAshan, name = "Бытовая химия", displayOrder = 2))
        val daHome = deptDao.insert(ShopDepartmentEntity(shopId = sAshan, name = "Товары для дома", displayOrder = 3))

        // === Товары ===
        val pMilk = productDao.insert(ProductEntity(
            name = "Молоко 3.2%", productGroupId = gMilk, recipientId = rAll,
            defaultUnit = "л", defaultQuantity = 1.0
        ))
        val pBread = productDao.insert(ProductEntity(
            name = "Хлеб белый", productGroupId = gBread, recipientId = rAll,
            defaultUnit = "шт", defaultQuantity = 1.0
        ))
        val pBanana = productDao.insert(ProductEntity(
            name = "Бананы", productGroupId = gVegetables, recipientId = rKids,
            defaultUnit = "кг", defaultQuantity = 1.0
        ))
        val pChicken = productDao.insert(ProductEntity(
            name = "Куриная грудка", productGroupId = gMeat, recipientId = rAll,
            defaultUnit = "кг", defaultQuantity = 0.5
        ))
        val pPowder = productDao.insert(ProductEntity(
            name = "Стиральный порошок", productGroupId = gChem,
            defaultUnit = "шт", defaultQuantity = 1.0
        ))
        val pCheese = productDao.insert(ProductEntity(
            name = "Сыр Российский", productGroupId = gMilk, recipientId = rAll,
            defaultUnit = "г", defaultQuantity = 300.0
        ))
        val pApple = productDao.insert(ProductEntity(
            name = "Яблоки", productGroupId = gVegetables, recipientId = rAll,
            defaultUnit = "кг", defaultQuantity = 1.5
        ))
        val pDish = productDao.insert(ProductEntity(
            name = "Средство для мытья посуды", productGroupId = gChem,
            defaultUnit = "шт", defaultQuantity = 1.0
        ))
        val pKasha = productDao.insert(ProductEntity(
            name = "Детская каша", productGroupId = gMilk, recipientId = rKids,
            defaultUnit = "шт", defaultQuantity = 2.0
        ))
        val pCase = productDao.insert(ProductEntity(
            name = "Чехол для телефона", purchaseType = "online",
            defaultUnit = "шт", defaultQuantity = 1.0,
            sellerUrl = "https://www.wildberries.ru", recipientId = rMom
        ))

        // === Привязки товаров к магазинам ===
        // Молоко → Пятёрочка (приоритет 1, молочный), Магнит (приоритет 2, молочный)
        linkDao.insert(ProductShopLinkEntity(productId = pMilk, shopId = sPyat, priority = 1, departmentId = dpMilk))
        linkDao.insert(ProductShopLinkEntity(productId = pMilk, shopId = sMagnit, priority = 2, departmentId = dmMilk))

        // Хлеб → Пятёрочка (хлебный), Магнит (выпечка)
        linkDao.insert(ProductShopLinkEntity(productId = pBread, shopId = sPyat, priority = 1, departmentId = dpBread))
        linkDao.insert(ProductShopLinkEntity(productId = pBread, shopId = sMagnit, priority = 2, departmentId = dmBake))

        // Бананы → Пятёрочка (овощной), Ашан (продукты)
        linkDao.insert(ProductShopLinkEntity(productId = pBanana, shopId = sPyat, priority = 1, departmentId = dpVeg))
        linkDao.insert(ProductShopLinkEntity(productId = pBanana, shopId = sAshan, priority = 2, departmentId = daFood))

        // Куриная грудка → Пятёрочка (мясной), Ашан (продукты)
        linkDao.insert(ProductShopLinkEntity(productId = pChicken, shopId = sPyat, priority = 1, departmentId = dpMeat))
        linkDao.insert(ProductShopLinkEntity(productId = pChicken, shopId = sAshan, priority = 2, departmentId = daFood))

        // Стиральный порошок → Ашан (бытовая химия)
        linkDao.insert(ProductShopLinkEntity(productId = pPowder, shopId = sAshan, priority = 1, departmentId = daChem))

        // Сыр → Пятёрочка (молочный), Магнит (молочный)
        linkDao.insert(ProductShopLinkEntity(productId = pCheese, shopId = sPyat, priority = 1, departmentId = dpMilk))
        linkDao.insert(ProductShopLinkEntity(productId = pCheese, shopId = sMagnit, priority = 2, departmentId = dmMilk))

        // Яблоки → Магнит (фрукты), Ашан (продукты)
        linkDao.insert(ProductShopLinkEntity(productId = pApple, shopId = sMagnit, priority = 1, departmentId = dmFruit))
        linkDao.insert(ProductShopLinkEntity(productId = pApple, shopId = sAshan, priority = 2, departmentId = daFood))

        // Средство для посуды → Пятёрочка (бытовая химия), Ашан (бытовая химия)
        linkDao.insert(ProductShopLinkEntity(productId = pDish, shopId = sPyat, priority = 1, departmentId = dpChem))
        linkDao.insert(ProductShopLinkEntity(productId = pDish, shopId = sAshan, priority = 2, departmentId = daChem))

        // Детская каша → Пятёрочка, Магнит
        linkDao.insert(ProductShopLinkEntity(productId = pKasha, shopId = sPyat, priority = 1))
        linkDao.insert(ProductShopLinkEntity(productId = pKasha, shopId = sMagnit, priority = 2))

        // Чехол → Wildberries
        linkDao.insert(ProductShopLinkEntity(productId = pCase, shopId = sWb, priority = 1))

        // === Записи текущего списка ===
        val now = System.currentTimeMillis()

        // Пятёрочка: молоко (срочно, количество отличается от дефолтного), хлеб (2 шт вместо 1), бананы, сыр (купленный)
        entryDao.insert(ShoppingListEntryEntity(
            productId = pMilk, quantity = 2.0, unit = "л",
            assignedShopId = sPyat, assignedDepartmentId = dpMilk,
            isUrgent = true, createdAt = now - 3600_000, updatedAt = now - 3600_000
        ))
        entryDao.insert(ShoppingListEntryEntity(
            productId = pBread, quantity = 2.0, unit = "шт",
            assignedShopId = sPyat, assignedDepartmentId = dpBread,
            createdAt = now - 7200_000, updatedAt = now - 7200_000
        ))
        entryDao.insert(ShoppingListEntryEntity(
            productId = pBanana, quantity = 1.5, unit = "кг",
            assignedShopId = sPyat, assignedDepartmentId = dpVeg,
            createdAt = now - 10800_000, updatedAt = now - 10800_000
        ))
        entryDao.insert(ShoppingListEntryEntity(
            productId = pCheese, quantity = 200.0, unit = "г",
            assignedShopId = sPyat, assignedDepartmentId = dpMilk,
            isBought = true, createdAt = now - 14400_000, updatedAt = now - 1800_000
        ))

        // Ашан: куриная грудка (срочно, 1 кг вместо дефолтных 0.5), стиральный порошок (2 шт вместо 1)
        entryDao.insert(ShoppingListEntryEntity(
            productId = pChicken, quantity = 1.0, unit = "кг",
            assignedShopId = sAshan, assignedDepartmentId = daFood,
            isUrgent = true, createdAt = now - 5400_000, updatedAt = now - 5400_000
        ))
        entryDao.insert(ShoppingListEntryEntity(
            productId = pPowder, quantity = 2.0, unit = "шт",
            assignedShopId = sAshan, assignedDepartmentId = daChem,
            createdAt = now - 86400_000, updatedAt = now - 86400_000
        ))

        // Магнит: яблоки
        entryDao.insert(ShoppingListEntryEntity(
            productId = pApple, quantity = 1.0, unit = "кг",
            assignedShopId = sMagnit, assignedDepartmentId = dmFruit,
            createdAt = now - 43200_000, updatedAt = now - 43200_000
        ))

        // Wildberries: чехол
        entryDao.insert(ShoppingListEntryEntity(
            productId = pCase, quantity = 1.0, unit = "шт",
            assignedShopId = sWb,
            createdAt = now - 172800_000, updatedAt = now - 172800_000
        ))

        // Без магазина: детская каша (срочно)
        entryDao.insert(ShoppingListEntryEntity(
            productId = pKasha, quantity = 2.0, unit = "шт",
            isUrgent = true, createdAt = now - 1800_000, updatedAt = now - 1800_000
        ))
    }
}
