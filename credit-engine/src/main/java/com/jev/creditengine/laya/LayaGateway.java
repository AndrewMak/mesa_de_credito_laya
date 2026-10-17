package com.jev.creditengine.laya;

import java.util.Map;

public interface LayaGateway {
    LayaResponse classify(String text);

    LayaResponse classify(String text, Map<String, Object> structured);
}
