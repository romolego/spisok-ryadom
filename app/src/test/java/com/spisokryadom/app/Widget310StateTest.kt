package com.spisokryadom.app

import android.app.Application
import com.spisokryadom.app.widget.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
class Widget310StateTest {
    private val settings=WidgetSettings()
    private fun row(id:Long=1,bought:Boolean=false,revision:String="pending",secret:Boolean=true)=WidgetItem.Purchase(WidgetPurchase(id,"stable-$id",100,200,bought,"Личный товар","2 упак",false,revision,secret))
    @Before fun reset(){WidgetInteractionState.clear()}
    @After fun clear(){WidgetInteractionState.clear()}
    @Test fun revealExpiresAndCannotLeakToAnotherWidgetPurchaseOrRevision(){
        val a=row();val b=row(2)
        WidgetInteractionState.present(10,"db",settings,listOf(a,b),now=0)
        WidgetInteractionState.present(11,"db",settings,listOf(a,b),now=0)
        val token=WidgetInteractionState.reveal(10,a.row,"",now=1)!!
        assertEquals(token,WidgetInteractionState.peekToken(10,a.row,now=4999))
        assertNull(WidgetInteractionState.peekToken(11,a.row,now=2));assertNull(WidgetInteractionState.peekToken(10,b.row,now=2))
        assertNull(WidgetInteractionState.peekToken(10,a.row.copy(revision="changed"),now=2))
        assertNull(WidgetInteractionState.peekToken(10,a.row,now=5001))
        assertNull(WidgetInteractionState.reveal(10,row(secret=false).row,"",now=3))
    }
    @Test fun repeatedTapDoesNotExtendPeekAndStaleTimeoutCannotHideNewPeek(){
        val a=row();WidgetInteractionState.present(10,"db",settings,listOf(a),now=0)
        val first=WidgetInteractionState.reveal(10,a.row,"",now=10)!!
        assertNull(WidgetInteractionState.reveal(10,a.row,"",now=20))
        WidgetInteractionState.reveal(10,a.row,first,now=30)
        assertNull(WidgetInteractionState.peekToken(10,a.row,now=31))
        val second=WidgetInteractionState.reveal(10,a.row,"",now=40)!!
        WidgetInteractionState.conceal(10,a.row.id,first)
        assertEquals(second,WidgetInteractionState.peekToken(10,a.row,now=41))
    }
    @Test fun processLossDatabaseReplacementAndSettingsChangeAllConceal(){
        val a=row()
        for(change in 0..2){
            WidgetInteractionState.present(10,"db",settings,listOf(a),now=0)
            WidgetInteractionState.reveal(10,a.row,"",now=1)
            when(change){0->WidgetInteractionState.clear();1->WidgetInteractionState.present(10,"restored",settings,listOf(a),now=2);else->WidgetInteractionState.present(10,"db",settings.copy(textSize=20),listOf(a),now=2)}
            assertNull(WidgetInteractionState.peekToken(10,a.row,now=3))
        }
    }
    @Test fun commitImmediatelyChangesCheckmarkWhileMotionRetainsPositionThenSettles(){
        val a=row();val b=row(2)
        WidgetInteractionState.present(10,"db",settings,listOf(a,b),now=0)
        val token=WidgetInteractionState.begin(10,a.row,"db",now=1)!!
        assertNull(WidgetInteractionState.begin(10,a.row,"db",now=2))
        val done=row(bought=true,revision="committed")
        val transition=WidgetInteractionState.present(10,"db",settings,listOf(b,done),now=3)
        assertEquals(listOf(1L,2L),transition.first.map{it.stableId});assertEquals(setOf(1L),transition.second)
        assertTrue((transition.first[0] as WidgetItem.Purchase).row.bought)
        WidgetInteractionState.settle(10,1,token)
        val settled=WidgetInteractionState.present(10,"db",settings,listOf(b,done),now=700)
        assertEquals(listOf(2L,1L),settled.first.map{it.stableId});assertTrue(settled.second.isEmpty())
    }
    @Test fun rapidDifferentChecksAndExpiredMotionCannotLeaveGhostRows(){
        val a=row();val b=row(2);val c=row(3)
        WidgetInteractionState.present(10,"db",settings,listOf(a,b,c),now=0)
        val first=WidgetInteractionState.begin(10,a.row,"db",now=1)!!
        val second=WidgetInteractionState.begin(10,b.row,"db",now=2)!!
        val actual=listOf(c,row(bought=true),row(2,bought=true))
        assertEquals(setOf(1L,2L),WidgetInteractionState.present(10,"db",settings,actual,now=3).second)
        WidgetInteractionState.settle(10,1,first)
        assertEquals(setOf(2L),WidgetInteractionState.present(10,"db",settings,actual,now=4).second)
        WidgetInteractionState.settle(10,2,second)
        assertEquals(actual,WidgetInteractionState.present(10,"db",settings,actual,now=5).first)
        WidgetInteractionState.begin(10,c.row,"db",now=6)
        assertEquals(listOf(row(bought=true)),WidgetInteractionState.present(10,"db",settings,listOf(row(bought=true)),now=7).first)
        assertTrue(WidgetInteractionState.present(10,"db",settings,listOf(row(bought=true)),now=3000).second.isEmpty())
    }
    @Test fun hideBoughtRetainsDepartingRowOnlyDuringTransitionAndUsesCurrentPrivacy(){
        val s=settings.copy(showBought=false);val header=WidgetItem.Header("g","Группа")
        val a=row(secret=false);val b=row(2)
        WidgetInteractionState.present(10,"db",s,listOf(header,a,b),now=0)
        val token=WidgetInteractionState.begin(10,a.row,"db",now=1)!!
        val bought=row(bought=true,revision="committed")
        val inFlight=WidgetInteractionState.present(10,"db",s,listOf(header,b),listOf(header,b,bought),now=3)
        assertTrue((inFlight.first[1] as WidgetItem.Purchase).row.isPrivate)
        assertEquals(setOf(1L),inFlight.second)
        WidgetInteractionState.settle(10,1,token)
        assertEquals(listOf(header,b),WidgetInteractionState.present(10,"db",s,listOf(header,b),now=700).first)
    }
}
