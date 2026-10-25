package eu.komars.forgescanner.model

import org.junit.Assert.*
import org.junit.Test

class ScanSessionTest {
    @Test fun keepsValuesAcrossStepsAndRequiresReview() {
        val session = ScanSession { 1000 }
        session.setValue(ResourceField.TICKETS, 9880, confirmed = false, approximate = true)
        session.next()
        assertEquals(9880L, session.snapshot().value(ResourceField.TICKETS))
        assertFalse(session.snapshot().readyToExport)
        session.confirm(ResourceField.TICKETS)
        assertTrue(session.snapshot().values[ResourceField.TICKETS]!!.confirmed)
        assertNull(session.snapshot().value(ResourceField.PETS))
        assertTrue(ClipboardReportFormatter.format(session.snapshot()).contains("nie odczytano"))
        assertTrue(ClipboardReportFormatter.format(session.snapshot()).contains("≈ 9880"))
    }

    @Test fun snapshotDoesNotMutateWhenSessionChanges() {
        val session = ScanSession()
        session.setValue(ResourceField.HAMMERS, 2)
        val old = session.snapshot()
        session.setValue(ResourceField.HAMMERS, 3)
        assertEquals(2L, old.value(ResourceField.HAMMERS))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsZeroCosts() {
        ScanSession().setValue(ResourceField.SKILL_COST, 0)
    }

    @Test fun finalInventorySubtractsBaseOnlyOnce() {
        assertEquals(28L, InventoryMath.count(fullRows = 6, remainder = 1, excludedBase = 3))
        assertEquals(95L, InventoryMath.count(fullRows = 19, remainder = 1, excludedBase = 1))
    }
}
