package com.spisokryadom.app

import com.spisokryadom.app.util.*
import org.junit.Assert.*
import org.junit.Test

class SearchAndParserTest {
    @Test fun russianCaseAndYoAreNormalized() {
        assertEquals(0.0,FuzzySearch.score("  ЗЕЛЁНЫЙ  ЧАЙ ","зеленый чай"),0.0)
        assertEquals(listOf("Яблоки Гала"),FuzzySearch.filterAndSort(listOf("Молоко","Яблоки Гала"),"яблки"))
    }
    @Test fun exactAndPrefixPrecedeTypos() {
        val ranked=FuzzySearch.filterAndSort(listOf("Яблоко","Яблоки Гала","Яблоки","Сок яблоки"),"яблоки")
        assertEquals("Яблоки",ranked[0]);assertEquals("Яблоки Гала",ranked[1]);assertTrue(ranked.indexOf("Яблоко")>ranked.indexOf("Сок яблоки"))
    }
    @Test fun shortQueriesDoNotGiveRandomFuzzyMatches() { assertTrue(FuzzySearch.filterAndSort(listOf("Молоко","Бананы"),"ык").isEmpty()) }
    @Test fun shoppingNotationIsParsed() {
        val rows=ShoppingTextParser.parse("бананы 4\nяблоки Гала — 0,5 кг\n- груши: 7 шт.\n1. молоко 2 л\n2 хлеб\nйогурт 2%\nвода")
        assertEquals(7,rows.size)
        assertEquals(listOf("бананы","яблоки Гала","груши","молоко","хлеб","йогурт 2%","вода"),rows.map{it.name})
        assertEquals(listOf("4","0,5","7","2","2","1","1"),rows.map{it.quantity})
        assertEquals(listOf("","кг","шт","л","","",""),rows.map{it.unit})
        assertFalse(rows.last().explicitQuantity)
    }
    @Test fun negativeAmountRequiresCorrection(){val line=ShoppingTextParser.parse("Яблоки -2").single();assertEquals("Яблоки",line.name);assertNull(Quantities.parse(line.quantity))}
    @Test fun emptyLinesAndLimits() {assertTrue(ShoppingTextParser.parse("\n \n").isEmpty());assertThrows(IllegalArgumentException::class.java){ShoppingTextParser.parse((1..201).joinToString("\n"){"Товар $it"})}}
    @Test fun invalidNumbersAreRejected() {listOf("0","-1","NaN","Infinity","1e20","0.000000001").forEach{assertNull(it,Quantities.parse(it))};assertEquals(.5,Quantities.parse("0,5")!!,0.0);assertEquals(.3,Quantities.valid(.1+.2),0.0)}
    @Test fun rankingLargeCatalog() {
        val catalog=(1..5000).map{"Товар $it"}+"Яблоки Гала"
        repeat(3){FuzzySearch.filterAndSort(catalog,"яблки")}
        val start=System.nanoTime();val result=FuzzySearch.filterAndSort(catalog,"яблки");val ms=(System.nanoTime()-start)/1_000_000
        assertEquals("Яблоки Гала",result.first());println("Fuzzy search 5001 items: $ms ms (JVM, not device UI)")
    }

    @Test fun storeHeadersCarryToFollowingPurchasesAndAreNotProducts() {
        val rows=ShoppingTextParser.parse("Хлеб 1\nКрасный Яр:\n- Бананы 4\nЯблоки 0,5 кг\n\nБатон:\nМолоко 2 л\nБез магазина:\nГубка 2")
        assertEquals(listOf("Хлеб","Бананы","Яблоки","Молоко","Губка"),rows.map{it.name})
        assertEquals(listOf(null,"Красный Яр","Красный Яр","Батон","Без магазина"),rows.map{it.shopName})
        assertEquals("0,5",rows[2].quantity)
    }
    @Test fun emptyOrDanglingStoreHeadersRequireCorrection() {
        listOf(":\nМолоко 1", "Батон:", "Батон:\nКрасный Яр:\nМолоко 1").forEach{raw->
            assertThrows(raw,IllegalArgumentException::class.java){ShoppingTextParser.parse(raw)}
        }
    }
}
