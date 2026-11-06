package eu.komars.forgescanner.model
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
/** 1.4.0 regression: the red unread-chat badge on the home screen was read as a close X ("Nie rozpoznaję głównego ekranu"). */
class HomeChatBadgeRegressionTest {
 @Test fun homeWithUnreadChatBadgeIsStillHome() {
  val dir=System.getenv("HOME_FIXTURES")?:File("../.local/home-fixtures").path
  val f=File(dir,"home-chat-badge-360.argb");assumeTrue(f.exists())
  DataInputStream(f.inputStream().buffered()).use {input ->
   val w=input.readInt();val h=input.readInt();val p=IntArray(w*h){input.readInt()}
   assertEquals(GameScreenKind.HOME,GameUiGeometryDetector(w,h,p).screenKind())
  }
 }
}
