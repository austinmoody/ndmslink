package com.lantanagroup.link.ndms;

import lombok.Getter;
import org.hl7.fhir.r4.model.Coding;

import java.util.Objects;

@Getter
public class BedTallyKey {
    private final Coding bedType;
    private final Coding tallyType;

    public BedTallyKey(Coding bedType, Coding tallyType) {
        this.bedType = bedType;
        this.tallyType = tallyType;
    }

    private boolean compareCoding(Coding c1, Coding c2) {
        if (c1 == c2) return true;
        if (c1 == null || c2 == null) return false;
        return Objects.equals(c1.getSystem(), c2.getSystem()) &&
                Objects.equals(c1.getCode(), c2.getCode()) &&
                Objects.equals(c1.getDisplay(), c2.getDisplay());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BedTallyKey that = (BedTallyKey) o;
        return compareCoding(bedType, that.bedType) && compareCoding(tallyType, that.tallyType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bedType.getCode(),
                bedType.getSystem(),
                bedType.getDisplay(),
                tallyType.getCode(),
                tallyType.getSystem(),
                tallyType.getDisplay());
    }
}
