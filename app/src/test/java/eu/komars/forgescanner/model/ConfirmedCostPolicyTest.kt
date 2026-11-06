package eu.komars.forgescanner.model
import eu.komars.forgescanner.vision.*
import org.junit.Assert.*
import org.junit.Test
class ConfirmedCostPolicyTest {
 private fun c(value:Long,confidence:Float=.8f)=OcrCandidate(ResourceField.SKILL_COST,value,"cost",ScreenBounds(0,0,20,20),confidence,false,"")
 @Test fun historySupportsOnlyAValuePresentInCurrentImage() {assertEquals(188L,ConfirmedCostPolicy.choose(188,listOf(c(188),c(1880)))?.value);assertNull(ConfirmedCostPolicy.choose(188,listOf(c(180))));assertNull(ConfirmedCostPolicy.choose(188,emptyList()))}
 @Test fun strongNewReadingAndWeakOldMatchRequireReview() {assertNull(ConfirmedCostPolicy.choose(188,listOf(c(188),c(190,.98f))));assertNull(ConfirmedCostPolicy.choose(188,listOf(c(188,.4f))))}
}
