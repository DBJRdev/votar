package com.poinsart.votar.data;

import org.json.simple.JSONAware;

public class JsonString implements JSONAware {
	public String value;
	
	public JsonString(String s) {
		value=s;
	}
	
	public String toString() {
		return value;
	}
	
	@Override
	public String toJSONString() {
		return value;
	}
	

}
