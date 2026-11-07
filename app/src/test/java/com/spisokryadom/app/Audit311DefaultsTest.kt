package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.db.StandardCategories
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductInputRepository
import com.spisokryadom.app.domain.ProductHints
import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Audit311DefaultsTest {
    private lateinit var db:AppDatabase
    private lateinit var input:ProductInputRepository

    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
        RetailConstraints.install(db.openHelper.writableDatabase)
        StandardCategories.install(db.openHelper.writableDatabase)
        input=ProductInputRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun preservedStandardDepartmentAliasStillSuppliesNewProductDefaults():Unit=runBlocking {
        val original=db.shopDepartmentDao().types().single{it.name=="Бакалея (мука, сахар, масло)"}
        db.shopDepartmentDao().renameType(original.id,"Бакалея")
        assertEquals(0,StandardCategories.install(db.openHelper.writableDatabase))
        val before=db.shopDepartmentDao().types()

        assertEquals(original.id,input.suggest("сахар").departmentTypeId)
        assertEquals(before,db.shopDepartmentDao().types())
        assertTrue(db.shopDepartmentDao().getAllSync().isEmpty())
        assertTrue(db.productDao().getAllSync().isEmpty())
    }

    @Test fun preservedStandardCategoryAliasStillSuppliesNewProductDefaults():Unit=runBlocking {
        val original=db.productGroupDao().getAllSync().single{it.name=="Гигиена и косметика"}
        db.productGroupDao().update(original.copy(name="Гигиена"))
        assertEquals(0,StandardCategories.install(db.openHelper.writableDatabase))
        val before=db.productGroupDao().getAllSync()

        assertEquals(original.id,input.suggest("шампунь").categoryId)
        assertEquals(before,db.productGroupDao().getAllSync())
        assertTrue(db.productDao().getAllSync().isEmpty())
    }

    @Test fun exactClassifierWinsAndDeletedOrCustomRenamedClassifiersAreNotInvented():Unit=runBlocking {
        val canonical=db.productGroupDao().getAllSync().single{it.name=="Гигиена и косметика"}
        val alias=db.productGroupDao().insert(ProductGroupEntity(name="Гигиена",displayOrder=-1))
        assertEquals(canonical.id,input.suggest("шампунь").categoryId)
        db.productGroupDao().delete(canonical)
        assertEquals(alias,input.suggest("шампунь").categoryId)
        db.productGroupDao().update(db.productGroupDao().getById(alias)!!.copy(name="Моя полка"))
        assertNull(input.suggest("шампунь").categoryId)
        val department=db.shopDepartmentDao().types().single{it.name=="Гигиена и косметика"}
        db.shopDepartmentDao().deleteType(department.id)
        assertNull(input.suggest("шампунь").departmentTypeId)
        assertEquals("шт",input.suggest("шампунь").unit)
        assertTrue(db.productDao().getAllSync().isEmpty())
        assertTrue(db.shopDepartmentDao().getAllSync().isEmpty())
    }

    @Test fun typosAmongEquivalentHintsStillSupplyMetadata():Unit=runBlocking {
        for(name in listOf("молко","молока")) {
            val hint=ProductHints.match(name)
            assertNotNull(name,hint)
            assertEquals("Продукты",hint!!.category)
            assertEquals("Молочка",hint.department)
            assertEquals("упак",hint.unit)
        }
        val sugar=ProductHints.match("сахр")
        assertNotNull(sugar)
        assertEquals("Бакалея (мука, сахар, масло)",sugar!!.department)
        assertEquals("упак",input.suggest("сахр").unit)
        assertEquals("Бананы",ProductHints.match("бананс")!!.name)
        assertTrue(db.productDao().getAllSync().isEmpty())
    }

    @Test fun dictionaryAliasesAreUniqueResolvableAndOnlyUseExistingClassifiers() {
        val byAlias=ProductHints.entries.flatMap{hint->hint.aliases.map{FuzzySearch.normalize(it) to hint.name}}
            .groupBy({it.first},{it.second})
        assertTrue(byAlias.filterValues{it.distinct().size>1}.toString(),byAlias.values.all{it.distinct().size==1})
        assertEquals(ProductHints.entries.size,ProductHints.entries.map{it.name}.distinct().size)
        for(hint in ProductHints.entries) {
            assertTrue(hint.name,StandardCategories.groups.any{it.name==hint.category})
            assertTrue(hint.name,StandardCategories.departments.any{it.name==hint.department})
            assertTrue(hint.name,hint.unit in setOf("шт","упак"))
            hint.aliases.forEach{alias->
                assertFalse(alias,Regex("[A-Za-z]").containsMatchIn(alias))
                assertEquals(alias,hint.name,ProductHints.match(alias)?.name)
            }
        }
    }

    @Test fun everydayHouseholdBabyAndPetGapsUsePurchaseUnitsWithoutSeedingCatalogue():Unit=runBlocking {
        val examples=listOf(
            Triple("подгузники","Детские товары","упак"),
            Triple("детское пюре","Детские товары","упак"),
            Triple("кошачий корм","Корма и товары для животных","упак"),
            Triple("корм для собак","Корма и товары для животных","упак"),
            Triple("пакеты для мусора","Товары для дома","упак"),
            Triple("лампочка","Товары для дома","шт"),
            Triple("пергамент для выпечки","Посуда и кухня","упак"),
            Triple("батарейки","Товары для дома","упак"),
            Triple("готовый салат","Гастроном (готовая еда)","упак"),
            Triple("банан","Овощи/фрукты","шт"),
            Triple("гречка","Крупы","упак"),
            Triple("колбаса","Мясо/колбаса","шт")
        )
        val groups=db.productGroupDao().getAllSync()
        val departments=db.shopDepartmentDao().types()
        for((name,department,unit) in examples) {
            val hint=ProductHints.match(name)
            assertNotNull(name,hint)
            assertEquals(name,department,hint!!.department)
            val suggestion=input.suggest(name)
            assertEquals(name,departments.single{it.name==department}.id,suggestion.departmentTypeId)
            assertEquals(name,unit,suggestion.unit)
            assertNotNull(name,suggestion.categoryId)
        }
        assertEquals(groups,db.productGroupDao().getAllSync())
        assertEquals(departments,db.shopDepartmentDao().types())
        assertTrue(db.productDao().getAllSync().isEmpty())
        assertTrue(db.shoppingListEntryDao().getAllSync().isEmpty())
        assertTrue(db.shopDepartmentDao().getAllSync().isEmpty())
        assertTrue(db.unitDao().getAllSync().isEmpty())
    }

    @Test fun ambiguousGenericNamesAndCompetingMetadataAreNotGuessed() {
        for(name in listOf("масло","крем","пакет","средство","корм","перец","масла","солко","йцукенабракадабра")) {
            assertNull(name,ProductHints.match(name))
        }
        assertEquals("Молочка",ProductHints.match("масло сливочное")!!.department)
        assertEquals("Бакалея (мука, сахар, масло)",ProductHints.match("масло растительное")!!.department)
        assertEquals("Товары для дома",ProductHints.match("пакеты для мусора")!!.department)
        assertEquals("Посуда и кухня",ProductHints.match("пакеты для заморозки")!!.department)
    }

    @Test fun confidenceMarginIncludesCompetitorsJustOutsideAcceptanceThreshold() {
        // This is close to both lemons (pieces) and lemonade (packs). A missing
        // runner-up caused the old truncated search to incorrectly claim confidence.
        val lemon=FuzzySearch.score("лимоны","лимно")
        val lemonade=FuzzySearch.score("лимонад","лимно")
        assertTrue(lemon<0.42)
        assertTrue(lemonade>0.42&&lemonade-lemon<0.10)
        assertNull(ProductHints.match("лимно"))
    }
}
