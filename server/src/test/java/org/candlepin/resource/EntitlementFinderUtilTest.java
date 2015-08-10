package org.candlepin.resource;

import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.resource.util.EntitlementFinderUtil;
import org.junit.Test;
import static org.junit.Assert.*;


public class EntitlementFinderUtilTest {

	@Test
	public void nullFilterTest() {
		EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter(null, null);
		assertEquals(false, filters.hasMatchFilters());
	}

	@Test
	public void matchesFilterTest() {
		EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter("matchesFilterTest", null);
		assertEquals(true, filters.hasMatchFilters());
	}
}
