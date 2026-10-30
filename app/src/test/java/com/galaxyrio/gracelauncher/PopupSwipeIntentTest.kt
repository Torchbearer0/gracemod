package com.galaxyrio.gracelauncher

import com.galaxyrio.gracelauncher.ui.components.PopupSwipeIntent
import com.galaxyrio.gracelauncher.ui.components.resistedPopupPull
import org.junit.Assert.*
import org.junit.Test

class PopupSwipeIntentTest {
    @Test fun livePullStartsBeforeCommitAndTracksReversalAndReset() {
        val swipe = PopupSwipeIntent(8f, 40f)
        swipe.drag(-15f)
        assertEquals(15f, swipe.firstItemPullDistance, 0f)
        assertFalse(swipe.opensFirst)
        swipe.drag(-50f)
        assertEquals(65f, swipe.firstItemPullDistance, 0f)
        swipe.drag(60f)
        assertEquals(5f, swipe.firstItemPullDistance, 0f)
        assertFalse(swipe.opensFirst)
        swipe.reset()
        assertEquals(0f, swipe.firstItemPullDistance, 0f)
    }

    @Test fun disabledLeftActionAndPopupReversalHaveNoRowPull() {
        val disabled = PopupSwipeIntent(8f)
        disabled.drag(-120f)
        assertEquals(0f, disabled.firstItemPullDistance, 0f)
        val popup = PopupSwipeIntent(8f, 40f)
        popup.drag(20f)
        popup.drag(-120f)
        assertEquals(0f, popup.firstItemPullDistance, 0f)
        assertFalse(popup.opensFirst)
    }

    @Test fun rubberBandHasIncreasingResistanceAndScalesWithDensity() {
        assertEquals(0f, resistedPopupPull(0f, 72f), 0f)
        assertEquals(0f, resistedPopupPull(-10f, 72f), 0f)
        val short = -resistedPopupPull(40f, 72f)
        val long = -resistedPopupPull(80f, 72f)
        assertTrue(short > 0f && short < 40f)
        assertTrue(long > short && long - short < short)
        assertTrue(-resistedPopupPull(10_000f, 72f) < 72f * .42f)
        assertEquals(resistedPopupPull(40f, 72f) * 2, resistedPopupPull(80f, 144f), .001f)
    }

    @Test fun leftGestureCommitsOnlyBeyondItsThresholdAndNeverRevealsAPopup() {
        val swipe = PopupSwipeIntent(8f, 40f)
        assertNull(swipe.drag(-39f))
        assertFalse(swipe.opensFirst)
        assertNull(swipe.drag(-1f))
        assertTrue(swipe.opensFirst)
        assertFalse(swipe.revealed)
        swipe.reset()
        assertFalse(swipe.opensFirst)
        assertEquals(true, swipe.drag(20f))
    }

    @Test fun returningTowardTheStartCancelsLeftLaunch() {
        val swipe = PopupSwipeIntent(8f, 40f)
        swipe.drag(-80f)
        assertTrue(swipe.opensFirst)
        swipe.drag(60f)
        assertFalse(swipe.opensFirst)
        assertFalse(swipe.revealed)
    }

    @Test fun closingAnOpenPopupDoesNotLaunchItsFirstItem() {
        val swipe = PopupSwipeIntent(8f, 40f)
        assertEquals(true, swipe.drag(80f))
        assertEquals(false, swipe.drag(-150f))
        assertFalse(swipe.opensFirst)
        assertEquals(true, swipe.drag(10f))
        assertFalse(swipe.opensFirst)
    }

    @Test fun firstRightMovementAfterTouchSlopTriggersOpening() {
        val swipe = PopupSwipeIntent(8f)
        assertNull(swipe.drag(-20f))
        assertEquals(true, swipe.drag(1f))
        assertTrue(swipe.expanded)
        assertNull(swipe.drag(0f))
        assertTrue(swipe.expanded)
    }

    @Test fun directionReversalDoesNotNeedToUndoALongDrag() {
        val swipe = PopupSwipeIntent(8f)
        swipe.drag(200f)
        assertNull(swipe.drag(-3f))
        assertEquals(false, swipe.drag(-5f))
        assertNull(swipe.drag(3f))
        assertEquals(true, swipe.drag(5f))
    }

    @Test fun smallJitterDoesNotRetargetAndNewGestureResetsIntent() {
        val swipe = PopupSwipeIntent(8f)
        swipe.drag(1f)
        repeat(20) { assertNull(swipe.drag(-2f)); assertNull(swipe.drag(2f)) }
        assertTrue(swipe.expanded)
        swipe.reset()
        assertFalse(swipe.revealed)
        assertFalse(swipe.expanded)
    }
}
