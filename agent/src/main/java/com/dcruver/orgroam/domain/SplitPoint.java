package com.dcruver.orgroam.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a potential point where a note could be split into two fragments.
 * Discovered via LLM analysis of note structure.
 */
@Data
@Builder
public class SplitPoint {
    /**
     * Character offset in the original content where the split should occur
     */
    private final int characterOffset;

    /**
     * The heading/topic before the split point
     */
    private final String beforeHeading;

    /**
     * The heading/topic after the split point
     */
    private final String afterHeading;

    /**
     * LLM's rationale for why the split should occur here
     */
    private final String rationale;

    /**
     * Confidence score from LLM (0.0-1.0)
     */
    private final double confidence;
}
