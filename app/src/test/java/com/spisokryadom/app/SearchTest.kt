package com.spisokryadom.app

import com.spisokryadom.app.util.*
import org.junit.Assert.*
import org.junit.Test

class SearchTest {
    @Test fun russianCaseAndYoAreNormalized() {
        assertEquals(0.0,FuzzySearch.score("  ЗЕЛЁНЫЙ  ЧАЙ ","зеленый чай"),0.0)
        assertEquals(listOf("Яблоки Гала"),FuzzySearch.filterAndSort(listOf("Молоко","Яблоки Гала"),"яблки"))
    }
    @Test fun exactAndPrefixPrecedeTypos() {
        val ranked=FuzzySearch.filterAndSort(listOf("Яблоко","Яблоки Гала","Яблоки","Сок яблоки"),"яблоки")
        assertEquals("Яблоки",ranked[0]);assertEquals("Яблоки Гала",ranked[1]);assertTrue(ranked.indexOf("Яблоко")>ranked.indexOf("Сок яблоки"))
    }
    @Test fun shortQueriesDoNotGiveRandomFuzzyMatches() { assertTrue(FuzzySearch.filterAndSort(listOf("Молоко","Бананы"),"ык").isEmpty()) }

    @Test fun invalidNumbersAreRejected() {listOf("0","-1","NaN","Infinity","1e20","0.000000001").forEach{assertNull(it,Quantities.parse(it))};assertEquals(.5,Quantities.parse("0,5")!!,0.0);assertEquals(.3,Quantities.valid(.1+.2),0.0)}
    @Test fun rankingLargeCatalog() {
        val catalog=(1..5000).map{"Товар $it"}+"Яблоки Гала"
        repeat(3){FuzzySearch.filterAndSort(catalog,"яблки")}
        val start=System.nanoTime();val result=FuzzySearch.filterAndSort(catalog,"яблки");val ms=(System.nanoTime()-start)/1_000_000
        assertEquals("Яблоки Гала",result.first());println("Fuzzy search 5001 items: $ms ms (JVM, not device UI)")
    }

}
