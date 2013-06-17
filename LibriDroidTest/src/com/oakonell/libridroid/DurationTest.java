package com.oakonell.libridroid;

import com.oakonell.utils.Duration;

import junit.framework.TestCase;

public class DurationTest extends TestCase {

	public void testNormalization() {
		Duration duration = new Duration(0, 0, 59);
		assertEquals(0, duration.getHours());
		assertEquals(0, duration.getMinutes());
		assertEquals(59, duration.getSeconds());

		duration = new Duration(0, 0, 60);
		assertEquals(0, duration.getHours());
		assertEquals(1, duration.getMinutes());
		assertEquals(0, duration.getSeconds());

		duration = new Duration(0, 0, 61);
		assertEquals(0, duration.getHours());
		assertEquals(1, duration.getMinutes());
		assertEquals(1, duration.getSeconds());

		duration = new Duration(0, 0, 3599);
		assertEquals(0, duration.getHours());
		assertEquals(59, duration.getMinutes());
		assertEquals(59, duration.getSeconds());

		duration = new Duration(0, 0, 3600);
		assertEquals(1, duration.getHours());
		assertEquals(0, duration.getMinutes());
		assertEquals(0, duration.getSeconds());

		duration = new Duration(0, 0, 3601);
		assertEquals(1, duration.getHours());
		assertEquals(0, duration.getMinutes());
		assertEquals(1, duration.getSeconds());
	}

	public void testSimple() {
		Duration duration = new Duration(0, 0, 1);
		assertEquals(0, duration.getHours());
		assertEquals(0, duration.getMinutes());
		assertEquals(1, duration.getSeconds());

		duration = new Duration(0, 2, 1);
		assertEquals(0, duration.getHours());
		assertEquals(2, duration.getMinutes());
		assertEquals(1, duration.getSeconds());

		duration = new Duration(3, 2, 1);
		assertEquals(3, duration.getHours());
		assertEquals(2, duration.getMinutes());
		assertEquals(1, duration.getSeconds());
	}

	public void testParsing() {
		Duration duration = Duration.from("5:20");
		assertEquals(0, duration.getHours());
		assertEquals(5, duration.getMinutes());
		assertEquals(20, duration.getSeconds());

		duration = Duration.from("20");
		assertEquals(0, duration.getHours());
		assertEquals(0, duration.getMinutes());
		assertEquals(20, duration.getSeconds());

		duration = Duration.from("1:2:20");
		assertEquals(1, duration.getHours());
		assertEquals(2, duration.getMinutes());
		assertEquals(20, duration.getSeconds());

		duration = Duration.from("1:2:3");
		assertEquals(1, duration.getHours());
		assertEquals(2, duration.getMinutes());
		assertEquals(3, duration.getSeconds());
	}

	public void testAdd() {
		Duration dur1 = new Duration(3, 2, 1);
		Duration dur2 = new Duration(4, 3, 2);
		Duration result = dur1.add(dur2);
		assertEquals(7, result.getHours());
		assertEquals(5, result.getMinutes());
		assertEquals(3, result.getSeconds());

		dur1 = new Duration(0, 58, 59);
		dur2 = new Duration(0, 1, 1);
		result = dur1.add(dur2);
		System.out.println(result.toString());
		assertEquals(1, result.getHours());
		assertEquals(0, result.getMinutes());
		assertEquals(0, result.getSeconds());

		dur1 = new Duration(0, 58, 59);
		dur2 = new Duration(0, 2, 2);
		result = dur1.add(dur2);
		System.out.println(result.toString());
		assertEquals(1, result.getHours());
		assertEquals(1, result.getMinutes());
		assertEquals(1, result.getSeconds());

	}
}
