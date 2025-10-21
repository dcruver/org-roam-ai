package com.dcruver.orgroam.domain;

/**
 * Types of notes in the Org-roam knowledge base.
 */
public enum NoteType {
    /**
     * Verbatim transcripts/docs - read-only except metadata
     */
    SOURCE,

    /**
     * Faithful summaries with citations
     */
    LITERATURE,

    /**
     * Atomic, one idea; heavy linking
     */
    PERMANENT,

    /**
     * Unknown or unclassified
     */
    UNKNOWN
}
