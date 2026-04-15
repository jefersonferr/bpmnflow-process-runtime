package org.bpmnflow.runtime.service.deploy;

/**
 * Discriminator for the polymorphic {@code owner_type} column in
 * {@code bpmn_extension_property}.
 *
 * <p>Using an enum instead of inline string literals ensures that a typo is
 * caught at compile time and that all valid values are visible in one place.</p>
 *
 * <p>The {@link #name()} of each constant matches the value stored in the
 * database column, so {@code ownerType.name()} is the correct way to persist
 * and query.</p>
 */
public enum ExtPropOwner {

    /** Version-level properties from {@code <bpmn:process>}. */
    PROCESS,

    /** Properties from a {@code <bpmn:participant>} element. */
    PARTICIPANT,

    /** Properties from a {@code <bpmn:lane>} element. */
    LANE,

    /** Properties from any task, event, or gateway element. */
    ELEMENT,

    /** Properties from a {@code <bpmn:sequenceFlow>} element. */
    SEQUENCE_FLOW
}