package com.labelhub.modules.dataset.service;

import java.io.InputStream;

/**
 * 数据集解析服务接口。
 */
public interface DatasetParseService {

    /**
     * 解析指定输入流并写入数据项。
     */
    void parseDatasetFile(Long datasetId, String format, InputStream inputStream, boolean clearOldItems);

    /**
     * 从 MinIO 对象异步解析数据集文件。
     */
    void parseDatasetFileAsync(Long datasetId, String format, String bucket, String objectName, boolean clearOldItems);
}
