# 竹编斗笠

在客户端的 **渲染 → 饰品** 中启用模块，然后把 **头饰** 设为 **竹编斗笠**。选择“不佩戴”或关闭模块可取下斗笠。现有配置系统保存模块状态和头饰选项。

斗笠仅对本地玩家生效，可在第三人称和背包人物预览中显示。模型继承原版玩家根骨骼与头部变换，随头部转向、俯仰和玩家姿态移动。它不会替换头盔装备，也不会将饰品状态发送到服务器。较厚的头盔可能与斗笠边缘轻微交叠。

| 文件 | 用途 |
| --- | --- |
| `bamboo_douli.bbmodel` | Blockbench 原生源文件，含内嵌贴图、25 个可编辑网格和分组 |
| `bamboo_douli-preview.png` | Blockbench 导出的模型预览 |
| `../../src/main/resources/assets/setsuna/cosmetics/bamboo_douli.json` | 客户端网格资源 |
| `../../src/main/resources/assets/setsuna/textures/cosmetics/bamboo_douli.png` | 256 × 256 竹编贴图 |
| `../../tools/blockbench/setsuna_douli.js` | Blockbench 本地建模与导出插件 |
| `../../tools/blockbench-api.cjs` | 通过本地脚本接口调用插件的命令行工具 |

模型由 Blockbench 5.2.0 的 `Mesh`、`MeshFace`、`Texture` 和原生项目编码器创建。外层竹编、内层竹架、帽檐和红色系绳分别成组。最终资源含 1,700 个面，其中三角形用重复末顶点的方式导出为四顶点记录。

## 在 Blockbench 中修改

1. 打开 `bamboo_douli.bbmodel`。模型使用 **Generic Model** 格式，贴图随源文件保存，无需另行查找贴图。
2. 在插件窗口选择“从文件加载”，加载 `tools/blockbench/setsuna_douli.js`。本次开发已在当前 Blockbench 中加载此插件。
3. 编辑网格顶点、旋转、UV 或贴图。保留贴图名称 `bamboo_douli.png` 和 256 × 256 的 UV 尺寸。新增方块需要先转换为网格，或取消导出该方块。
4. 选择 **Tools → Export Setsuna headwear resources**，再选择项目根目录 `Syink-Main`。插件保存源文件、网格 JSON 和 PNG。
5. 重新构建客户端。游戏中的资源重新加载会重新建立饰品渲染层及其模型缓存。

插件中的 **Create Setsuna bamboo douli** 会新建初始模型。已有人工修改时，使用导出操作即可，无需重新生成。

## 通过脚本调用插件

这些命令直接调用 Blockbench 插件 API，不使用桌面鼠标或键盘自动化。需要 Node.js 与 `playwright` 包；无需下载 Playwright 浏览器。可通过 `NODE_PATH` 指向已有依赖目录。

先启动 Blockbench，并仅在本机地址开放脚本连接。例如：

```powershell
Start-Process -FilePath 'D:\Program Files\Blockbench\Blockbench.exe' -ArgumentList '--remote-debugging-port=9222','--remote-debugging-address=127.0.0.1' -WindowStyle Hidden
```

在项目根目录运行其中一个操作：

```powershell
# 重新生成初始模型，并覆盖本项目的斗笠源文件和客户端资源。
node tools/blockbench-api.cjs create

# 导出当前在 Blockbench 中选中的斗笠模型。
node tools/blockbench-api.cjs export

# 重开源文件，导出到 build/reports/douli/roundtrip，并与客户端网格逐项比较。
node tools/blockbench-api.cjs verify
```

源文件坐标使用像素单位，头部旋转中心为 `(0, 0, 0)`，`+Y` 向上，`+Z` 朝脸部。玩家头部顶部为 `Y = 8`。客户端加载器将位置转换为 `(x, -y, -z) / 16`，UV 保持左上角为原点的 `[0, 1]` 范围。渲染层随后应用原版 `root().translateAndRotate` 与 `translateToHead`。

## 验证

客户端和认证协议的构建命令如下。使用 JDK 25。任务名称前的冒号将 `build` 限定在根客户端项目。

```powershell
.\gradlew.bat :auth-protocol:test :build
```

测试覆盖网格解析、坐标转换、无效数据拒绝、资源与贴图打包、中文标签及头饰配置保存。`verify` 命令另外检查 Blockbench 原生源文件重开后与运行时网格一致。

本次客户端构建通过，282 项客户端测试与 8 项认证协议测试均无失败，JAR 完整性校验通过。已核对最终 JAR 包含 1,700 个面的最新模型及贴图。启动检查确认 `AvatarRendererMixin`、`CosmeticHeadwearLayer` 和认证界面成功加载；没有进入世界实测佩戴。检查到认证界面后主动停止测试进程，因此启动日志末尾的进程退出 `-1` 不表示启动异常。

开发验证日志保存在 `build/reports/douli/`。仓库 README 的完整服务端验证另有两个现有失败：PHP 认证测试调用未定义的 `user_rank_color()`，IRC 集成测试等待 `417` 响应超时。这些服务端问题不属于斗笠修改范围。

第一次启动检查使用了现有 `runs/client`，游戏保存了其中的 `options.txt`。没有运行前副本可比较或恢复，因此保留该文件。后续启动检查已改为隔离目录 `build/reports/douli/smoke-run`。原有代码修改均保留。

游戏内检查方法：在单人世界启用斗笠，切换前后第三人称，转头、抬头、低头和潜行，再打开背包确认预览。关闭模块和选择“不佩戴”均应立即隐藏模型。重新打开客户端后，确认上次选择已恢复。
