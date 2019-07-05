package com.atlassian.performance.tools.awsinfrastructure.api.dataset;

import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula;
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset;

/**
 * @since 2.12.0
 */
public interface DatasetHost {

    InfrastructureFormula<?> host(Dataset dataset);
}
