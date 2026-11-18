package io.casehub.ledger.api.spi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RelevanceAssessmentTest {

    @Test
    void neutral_hasWeight1() {
        assertEquals(1.0, RelevanceAssessment.NEUTRAL.weight());
        assertNull(RelevanceAssessment.NEUTRAL.reason());
    }

    @Test
    void validWeight_accepted() {
        var a = new RelevanceAssessment(0.7, "org-distance:3");
        assertEquals(0.7, a.weight());
        assertEquals("org-distance:3", a.reason());
    }

    @Test
    void zeroWeight_accepted() {
        var a = new RelevanceAssessment(0.0, null);
        assertEquals(0.0, a.weight());
    }

    @Test
    void negativeWeight_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelevanceAssessment(-0.1, null));
    }

    @Test
    void weightAboveOne_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelevanceAssessment(1.01, null));
    }

    @Test
    void nanWeight_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelevanceAssessment(Double.NaN, null));
    }

    @Test
    void nullReason_accepted() {
        var a = new RelevanceAssessment(0.5, null);
        assertNull(a.reason());
    }
}
