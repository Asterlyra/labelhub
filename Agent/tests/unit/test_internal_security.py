"""覆盖内部服务认证和媒体来源限制。"""
import pytest
from annotation_agent import create_app
from annotation_agent.infrastructure.media.media_fetcher import MediaFetcher


def test_internal_routes_require_a_configured_token(monkeypatch):
    monkeypatch.delenv('LABELHUB_AGENT_TOKEN', raising=False)
    client = create_app(container=object()).test_client()
    assert client.get('/api/unknown').status_code == 503
    monkeypatch.setenv('LABELHUB_AGENT_TOKEN', 'test-only-' + 'x' * 32)
    assert client.get('/api/unknown').status_code == 401
    assert client.get('/api/unknown', headers={'X-LabelHub-Agent-Token': 'bad'}).status_code == 401
    assert client.get('/api/unknown', headers={'X-LabelHub-Agent-Token': 'test-only-' + 'x' * 32}).status_code == 404
    assert client.get('/health').status_code == 200

@pytest.mark.parametrize('url', ['file:///etc/passwd', 'http://127.0.0.1/metadata', 'http://allowed.example@evil.example/', 'http://evil.example/'])
def test_unapproved_media_urls_are_rejected(monkeypatch, url):
    monkeypatch.setenv('LABELHUB_MEDIA_ALLOWED_HOSTS', 'allowed.example')
    with pytest.raises(ValueError):
        MediaFetcher().validate_image(url, 1)
