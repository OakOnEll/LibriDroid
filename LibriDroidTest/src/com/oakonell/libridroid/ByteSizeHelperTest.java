package com.oakonell.libridroid;

import com.oakonell.utils.ByteSizeHelper;

import junit.framework.TestCase;

public class ByteSizeHelperTest extends TestCase {
	public void testSimple() {
		assertEquals("1000 bytes", ByteSizeHelper.getDisplayable(1000));
		assertEquals("1.20 Kb",
				ByteSizeHelper.getDisplayable((long) (1.2 * 1024)));
		assertEquals("999.90 Kb",
				ByteSizeHelper.getDisplayable((long) (999.9 * 1024)));
		assertEquals("1.50 Mb",
				ByteSizeHelper.getDisplayable((long) (1.5 * 1024 * 1024)));
		assertEquals("1000.00 Mb",
				ByteSizeHelper.getDisplayable((1000 * 1024 * 1024)));

		assertEquals("1.10 Gb",
				ByteSizeHelper
						.getDisplayable((long) (1.1 * 1024 * 1024 * 1024)));
	}
}
