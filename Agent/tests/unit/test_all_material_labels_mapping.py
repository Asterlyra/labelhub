"""测试所有素材标签类型的映射是否正确。"""

from annotation_agent.infrastructure.integrations.backend_contract_mapper import backend_context_to_audit_payload


# 构造覆盖所有素材标签类型的测试事件
def sample_all_material_labels_event():
    return {
        "taskItemId": 2022,
        "taskId": 17,
        "taskTitle": "\u5168 label MQ \u6d4b\u8bd5\u4efb\u52a1",
        "taskDescription": "\u8986\u76d6\u6240\u6709 material label \u7684\u6620\u5c04\u4e0e\u6d4b\u8bd5\u6570\u636e\u7ed3\u6784\u3002",
        "materials": [
            {
                "field": "display_field",
                "label": "\u6587\u672c\u8bf4\u660e",
                "required": 0,
                "config": {"source": "raw.prompt"},
            },
            {
                "field": "radio_field",
                "label": "\u5355\u9009\u6846",
                "required": 1,
                "config": {"options": ["A", "B", "C"]},
            },
            {
                "field": "checkbox_field",
                "label": "\u591a\u9009\u6846",
                "required": 1,
                "config": {"options": ["tag-a", "tag-b", "tag-c"]},
            },
            {
                "field": "select_field",
                "label": "\u4e0b\u62c9\u9009\u9879\u6846",
                "required": 1,
                "config": {"options": ["low", "medium", "high"], "multiple": False},
            },
            {
                "field": "text_field",
                "label": "\u8f93\u5165\u6846",
                "required": 1,
                "config": {"maxLength": 80, "placeholder": "\u8bf7\u8f93\u5165\u7b80\u77ed\u7ed3\u8bba"},
            },
            {
                "field": "detail_field",
                "label": "\u8be6\u60c5\u9875\u5185\u5bb9",
                "required": 0,
                "config": {"maxLength": 500, "placeholder": "\u8bf7\u8f93\u5165\u8be6\u7ec6\u8bf4\u660e"},
            },
            {
                "field": "multiline_field",
                "label": "\u591a\u884c\u6587\u672c\u6846",
                "required": 0,
                "config": {"maxLength": 500, "placeholder": "\u8bf7\u8f93\u5165\u591a\u884c\u5185\u5bb9"},
            },
            {
                "field": "image_field",
                "label": "\u56fe\u7247\u7ec4\u4ef6",
                "required": 0,
                "config": {"accept": ".jpg,.png"},
            },
            {
                "field": "file_field",
                "label": "\u6587\u4ef6\u7ec4\u4ef6",
                "required": 0,
                "config": {"accept": ".pdf,.doc,.docx"},
            },
            {
                "field": "rating_field",
                "label": "\u8bc4\u5206\u7ec4\u4ef6",
                "required": 1,
                "config": {"options": [1, 2, 3, 4, 5]},
            },
        ],
        "reviewConfig": {
            "reviewPrompt": "\u8bf7\u68c0\u67e5\u6807\u6ce8\u7ed3\u679c\u4e0e\u5404\u7ec4\u4ef6\u914d\u7f6e\u662f\u5426\u4e00\u81f4\u3002",
            "dimensions": [
                {"field": "radio_field", "weight": 15, "passScore": 80},
                {"field": "checkbox_field", "weight": 10, "passScore": 70},
                {"field": "select_field", "weight": 10, "passScore": 70},
                {"field": "text_field", "weight": 15, "passScore": 70},
                {"field": "detail_field", "weight": 10, "passScore": 70},
                {"field": "multiline_field", "weight": 10, "passScore": 70},
                {"field": "image_field", "weight": 5, "passScore": 60},
                {"field": "file_field", "weight": 5, "passScore": 60},
                {"field": "rating_field", "weight": 20, "passScore": 80},
            ],
            "passThreshold": 85.0,
            "reviewThreshold": 70.0,
        },
        "rawData": {
            "id": "ALL_LABELS_001",
            "category": "full-material-coverage",
            "lang": "zh",
            "media_type": "mixed",
            "prompt": "\u8bf7\u6839\u636e\u6a21\u677f\u5b8c\u6210\u5168\u7ec4\u4ef6\u586b\u5199\u3002",
            "reference": "\u786e\u4fdd\u6240\u6709 label \u90fd\u53ef\u6b63\u786e\u6620\u5c04\u3002",
        },
        "annotationData": {
            "radio_field": "A",
            "checkbox_field": ["tag-a", "tag-c"],
            "select_field": "medium",
            "text_field": "\u6574\u4f53\u6807\u6ce8\u5b8c\u6574\u3002",
            "detail_field": "\u8be6\u60c5\u9875\u5185\u5bb9\u586b\u5199\u6b63\u5e38\u3002",
            "multiline_field": "\u7b2c\u4e00\u884c\u8bf4\u660e\n\u7b2c\u4e8c\u884c\u8865\u5145",
            "image_field": "https://example.com/demo.png",
            "file_field": "https://example.com/demo.pdf",
            "rating_field": 4,
        },
    }


# 验证所有素材标签类型映射到正确的组件类型
def test_all_material_labels_map_to_expected_component_types():
    # 将后端事件转换为内部审核载荷
    payload = backend_context_to_audit_payload(sample_all_material_labels_event())

    # 提取各组件的field->type映射
    component_types = {
        component["field"]: component["type"]
        for component in payload["annotationSchema"]["components"]
    }

    # 验证taskItemId正确传递
    assert payload["taskItemId"] == "2022"
    # 验证每种素材标签映射到预期的组件类型
    assert component_types == {
        "display_field": "display",
        "radio_field": "radio",
        "checkbox_field": "checkbox",
        "select_field": "select",
        "text_field": "text",
        "detail_field": "textarea",
        "multiline_field": "textarea",
        "image_field": "text",
        "file_field": "text",
        "rating_field": "rating",
    }
    assert payload["annotationResult"]["rating_field"] == 4
    assert payload["annotationSchema"]["components"][2]["options"] == ["tag-a", "tag-b", "tag-c"]
