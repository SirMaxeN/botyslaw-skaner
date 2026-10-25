package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class ClipboardCommandTest {
    @Test fun completeScanProducesSevenNumericImportFields() {
        val session=ScanSession(); ResourceField.entries.forEach { session.setValue(it,50) }
        val command=ClipboardReportFormatter.command(session.snapshot())
        assertEquals("/import dane:{\"skill_tickets\":50,\"mount_keys\":50,\"mounts\":50,\"hammers\":50,\"pets\":50,\"skill_cost_x5\":50,\"mount_cost\":50}",command)
    }
    @Test(expected=IllegalArgumentException::class) fun incompleteScanCannotProduceImportCommand() {
        ClipboardReportFormatter.command(ScanSession().snapshot())
    }
}
