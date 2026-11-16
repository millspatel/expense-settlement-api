package com.splitwise.model;

public enum SplitType {
    /** Divide the amount equally among the participants. */
    EQUAL,
    /** Each participant pays a percentage; percentages must add up to 100. */
    PERCENTAGE,
    /** Each participant pays an exact amount; amounts must add up to the total. */
    EXACT
}
