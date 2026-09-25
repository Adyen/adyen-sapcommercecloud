package com.adyen.cockpit.setup.dto;

import java.util.List;

/**
 * @param steps the steps attempted, in order. A run that stops early lists fewer; a step not listed was
 *              never reached, which is not the same as having failed.
 */
public record ProvisionReport(boolean complete, List<ProvisionStep> steps)
{
}
