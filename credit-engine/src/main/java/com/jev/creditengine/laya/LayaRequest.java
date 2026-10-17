package com.jev.creditengine.laya;

import java.util.Map;

public record LayaRequest(String text, Map<String, Object> structured) {
}
