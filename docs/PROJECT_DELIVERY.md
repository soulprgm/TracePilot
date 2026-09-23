# TracePilot 最终交付说明

## 成品入口

- TracePilot 控制台：https://tracepilot-backend.onrender.com
- Jaeger 链路界面：https://tracepilot-jaeger.onrender.com
- GitHub 源码：https://github.com/soulprgm/TracePilot

Render 免费实例休眠后，首次打开可能需要等待服务启动。

## 已完成能力

1. **遥测采集**：接收标准 OTLP HTTP Protobuf 数据，并支持 gzip。
2. **分布式调用链**：订单服务依次调用库存与支付服务，包含成功、慢请求和失败三种真实场景。
3. **双路存储**：Collector 同时把链路发送到 TracePilot 和 Jaeger。
4. **可观测控制台**：展示请求量、错误率、平均耗时、慢链路、服务 P95/P99 和链路瀑布图。
5. **查询与排查**：按时间、服务、状态、耗时、Trace ID 和操作名组合筛选。
6. **一键演示**：在控制台直接运行成功、慢请求和失败场景，无需命令行。
7. **数据导出**：列表可导出 CSV；单条链路可复制 Trace ID、下载 JSON 或跳转 Jaeger。
8. **数据治理**：默认保留 30 天，每天自动清理过期 span 和分析记录。
9. **云端部署**：Render Blueprint 定义后端、数据库、Jaeger、Collector 和三个微服务。
10. **质量保障**：GitHub Actions 自动测试四个 Java 模块，并提供云端验收脚本。

## 验收方法

打开 TracePilot 控制台，在 **Generate distributed traces** 区域分别运行三个场景。约 5 秒后刷新页面：

- Success 应显示正常链路；
- Slow 应显示约 2 秒的支付 span；
- Failure 应显示错误状态和 HTTP 500 相关属性；
- 打开任一链路应看到跨服务瀑布图，并可跳转 Jaeger；
- 点击 **Export page CSV** 可下载当前页结果。

也可以在项目根目录运行：

```bash
./scripts/smoke-test-cloud.sh
```

## 日常维护

完成修改后执行：

```bash
cd /Users/ryan/program
git pull --rebase
git status
git diff
git add .
git commit -m "说明完成了什么"
git push
```

推送到 `main` 后，GitHub Actions 会运行测试。Render 会根据连接方式自动部署或等待手动 Deploy。

## 使用边界

当前云端采用 Render 免费套餐，适合作品展示和学习。需要承载正式业务时，应升级常驻实例与数据库，并增加登录鉴权、私有网络、采样、备份和告警。
