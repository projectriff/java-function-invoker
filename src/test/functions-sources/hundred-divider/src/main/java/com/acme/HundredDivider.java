package com.acme;

import java.util.function.Function;

public class HundredDivider implements Function<Integer, Integer> {

	public Integer apply(Integer input) {
		return 100 / input;
	}
}
