package com.labelhub.modules.template.handler;

import com.labelhub.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MaterialHandlerFactory {

    private final Map<String, MaterialHandler> handlerMap = new HashMap<>();

    public MaterialHandlerFactory(List<MaterialHandler> handlers) {
        for (MaterialHandler handler : handlers) {
            handlerMap.put(handler.supportType(), handler);
            log.info("注册模板物料处理器：type={}，class={}",
                    handler.supportType(), handler.getClass().getSimpleName());
        }
    }

    public MaterialHandler getHandler(String type) {
        MaterialHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new BusinessException("不支持的物料类型：" + type);
        }
        return handler;
    }
}
