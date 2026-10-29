# LabelHub

LabelHub 是一个数据标注与审核平台，包含 Vue 前端、Spring Boot 后端和 Python Agent，支持任务管理、人工标注、AI 建议和预审。

## 安全和部署

此版本尚未通过完整部署安全验收，仅供本地评估，不应直接暴露到互联网。复制 `deploy/docker.env.example` 为本地 `deploy/docker.env`，设置各项服务口令，并设置至少 32 字符的随机 `LABELHUB_AGENT_TOKEN`；不同服务必须使用相同的内部令牌。各服务端口默认只监听本机，Agent 不映射到宿主机。

媒体下载默认关闭。需要时将 `LABELHUB_MEDIA_ALLOWED_HOSTS` 设置为可信媒体主机的逗号分隔列表；仅支持 HTTP(S)，不跟随重定向，视频上限 32 MiB。仅允许可信运营者控制的主机，不要将内网服务加入允许列表。

首次启动后通过注册接口创建用户，再由数据库管理员在数据库内给该用户分配项目管理员角色。不要使用预设账号。管理员可在可信环境中，先确认目标用户名，再执行下列 SQL（将占位用户名改为实际值）：

```sql
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM user u JOIN role r ON r.role_key = 'PROJECT_ADMIN'
WHERE u.username = 'REPLACE_WITH_REGISTERED_USERNAME';
```

直接修改数据库后还需清除该用户的 Redis 角色和权限缓存，或等待缓存过期；权限变更应在可信的本地管理环境完成。使用容器之前需自行检查配置及网络边界。

```sh
docker compose --env-file deploy/docker.env build
docker compose --env-file deploy/docker.env --profile migrate run --rm agent-migrate
docker compose --env-file deploy/docker.env up -d
```

## 开发

前端：`cd Frontend && npm ci && npm run test:unit && npm run build`。后端使用 JDK 17 和 Maven：`cd Backend && mvn test`。Agent：安装 `Agent/requirements.txt`，在 `Agent` 目录运行 `python -m pytest tests/unit`。部分历史数据集测试需要额外数据，未包含在本仓库。

## 许可

项目以 [GNU AGPL v3.0](LICENSE) 发布。第三方库和素材分别适用其许可。
