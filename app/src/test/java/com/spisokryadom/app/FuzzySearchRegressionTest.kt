package com.spisokryadom.app

import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class FuzzySearchRegressionTest {
    /** Independent unbounded reference to protect ranking while adding pruning and top-K. */
    private fun reference(candidate:String,query:String):Double {
        val c=FuzzySearch.normalize(candidate);val q=FuzzySearch.normalize(query)
        if(q.isEmpty()||c==q)return 0.0
        if(c.startsWith(q))return .02
        val words=c.split(' ','-','/')
        if(words.any{it.startsWith(q)})return .04
        if(c.contains(q))return .06
        if(q.length<3)return 1.0
        fun distance(a:String,b:String):Int {
            val table=Array(a.length+1){IntArray(b.length+1)}
            for(i in 0..a.length)table[i][0]=i
            for(j in 0..b.length)table[0][j]=j
            for(i in 1..a.length)for(j in 1..b.length)table[i][j]=minOf(table[i-1][j]+1,table[i][j-1]+1,table[i-1][j-1]+if(a[i-1]==b[j-1])0 else 1)
            return table[a.length][b.length]
        }
        return .15+.8*(listOf(c)+words.filter{it.isNotEmpty()}).minOf{distance(it,q).toDouble()/maxOf(it.length,q.length)}
    }
    @Test fun boundedRankingAndTopFiveMatchFullReference() {
        val random=Random(7521);val alphabet="абвгдеёжзиклмноп abc-/"
        fun word()=(1..random.nextInt(1,19)).map{alphabet[random.nextInt(alphabet.length)]}.joinToString("")
        val names=List(180){word()}+listOf("Яблоки Гала","яблоки Гала","ЗЕЛЁНЫЙ ЧАЙ","", "бананы")
        val index=FuzzySearch.index(names){it}
        val queries=(List(80){word()}+listOf("яблки","ЗЕЛЕНЫЙ ЧАЙ","бан","ё","Гала")).filter{it.isNotBlank()}
        for(query in queries)for(threshold in listOf(.02,.06,.3,.5,.7,.950001,1.1)) {
            val expected=names.withIndex().map{Triple(it.index,it.value,reference(it.value,query))}.filter{it.third<threshold}
                .sortedWith(compareBy<Triple<Int,String,Double>>{it.third}.thenBy{FuzzySearch.normalize(it.second)}.thenBy{it.first}).map{it.second}
            assertEquals("query=$query threshold=$threshold",expected,index.search(query,threshold))
            assertEquals(expected.take(5),index.search(query,threshold,limit=5))
        }
    }
    @Test fun acceptedLongInputHasBoundedWork() {
        val names=(1..5000).map{"Товар $it"}+"Яблоки Гала"
        val index=FuzzySearch.index(names){it}
        index.search("яблки")
        val start=System.nanoTime()
        repeat(200){assertTrue(index.search("я".repeat(249),limit=5).isEmpty())}
        assertTrue(index.search("я".repeat(50_000),limit=5).isEmpty())
        val ms=(System.nanoTime()-start)/1_000_000
        println("Optimized 5001-name search: 200x249 chars plus 50000 chars = $ms ms (JVM)")
        assertTrue("Long accepted input must avoid the former 16–19s quadratic path; actual=${ms}ms",ms<3_000)
    }
    @Test fun cancellationIsCheckedDuringDistanceAndCatalogueTraversal() {
        var checks=0
        assertThrows(CancellationException::class.java){FuzzySearch.index(listOf("а".repeat(800))){it}.search("б".repeat(800),checkCancelled={if(++checks==4)throw CancellationException("cancelled")})}
        checks=0
        assertThrows(CancellationException::class.java){FuzzySearch.index(List(5001){"Товар $it"}){it}.search("яблки",checkCancelled={if(++checks==100)throw CancellationException("cancelled")})}
    }
}
