# 灾变幻化资源

本目录对应 `D:\codex\灾变_武器与装备美术资源` 中的 L_Ender's Cataclysm 3.31 美术资源。客户端只改变本地渲染结果，不改变物品栈、组件、网络数据或原版物品行为。

在 ClickGUI 的 **渲染 → 装备幻化** 中启用模块。盔甲、武器和工具分别按槽位选择外观。`NONE` 保留原版渲染。

已接入内容：

- 盔甲：焰魔、焰魔鞘翅胸甲、咒魂、骨龙、恶兽头盔、花岩肩甲。恢复了原版继承的人体甲片、镜像 UV、局部膨胀、零厚度面、腿部贴图和鞘翅姿态。
- 武器和工具：36 个选项，覆盖剑、斧、镐、锹、锄、重锤、弓、弩、盾、三叉戟和箭；其中 25 个是原生立体模型，11 个是原版平面物品模型。
- 饰品：活力安卡、战魂护符、粘性手套、炽热之握、甲壳巨钳、初学者腰带、巨兽腰带、瓶中沙暴、不破之颅、结实皮靴。保留了原版 67 个模型部件、细臂偏移、第一人称手部姿态和两组发光层。

资源来源中只有图标、没有原版穿戴模型的 3 个饰品没有被伪造为 3D 穿戴物：怨怒之戒、沙漠项链、缚魂之链。

显示关系按对应部位处理：

1. 没有成功的幻化时，原版装备和对应饰品可以同时显示。
2. 选择的幻化只有在模型、贴图和骨骼都成功加载后才生效。
3. 成功幻化后，隐藏同一部位的原版装备和对应饰品。其他部位继续显示。
4. 幻化资源缺失、空模型、退化模型或重新加载失败时，恢复原版装备和饰品。

对应关系如下：头部幻化隐藏斗笠；胸部幻化隐藏项链和双手饰品；腿部幻化隐藏腰饰和护符；足部幻化隐藏足饰；主手或副手武器幻化只隐藏对应手饰。原版盔甲或原版手持物本身不会隐藏饰品。

模型资源保持原版坐标、UV 和层级。原版 3D 物品的 GUI、第一人称、第三人称、左右手、投掷、格挡、拉弓和特殊变体变换来自 `source_item.json` 及其变体文件。弓在未拉弦时隐藏箭和沙环；绞肉锯和激光加特林保留待机或使用动画。附魔静态模型也标记为动画状态，以保持 GUI 光效更新。

主要资源路径：

- `src/main/resources/assets/setsuna/cataclysm/armor/`：盔甲原生网格定义、OBJ 来源和贴图。
- `src/main/resources/assets/setsuna/cataclysm/items/`：25 个立体物品的网格、材质、原始物品模型和变体。
- `src/main/resources/assets/setsuna/items/cataclysm/` 与 `models/item/cataclysm/`：11 个平面物品的客户端模型入口。
- `src/main/resources/assets/setsuna/textures/item/cataclysm/`：平面剑、斧、镐、锹、锄等物品贴图。
- `src/main/resources/assets/setsuna/textures/cosmetics/cataclysm/`：饰品贴图和发光层。
- `src/main/resources/assets/setsuna/cataclysm/items/manifest.json`：资源清单、原始来源 JAR SHA-256、贴图哈希和变体信息。

可重复导入资源：

```powershell
$py = 'C:\Users\Zmjjk\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
& $py tools/import-cataclysm-items.py `
  --art-dir 'D:\codex\灾变_武器与装备美术资源' `
  --mod-jar 'D:\PCL2\.minecraft\versions\Immersive Fight 4.2.9\mods\L_Enders_Cataclysm-3.31.jar'
& $py tools/import-cataclysm-armor.py `
  --exporter 'D:\codex\tools\pack_cataclysm_art.py'
& $py tools/cataclysm/import_accessories.py `
  --source 'D:\codex\tools\cataclysm_models_src' `
  --jar 'D:\PCL2\.minecraft\versions\Immersive Fight 4.2.9\mods\L_Enders_Cataclysm-3.31.jar'
& $py tools/export_cataclysm_item_meshes.py `
  --exporter 'D:\codex\tools\pack_cataclysm_art.py' `
  --model-sources 'D:\codex\tools\cataclysm_models_src'
```

最后一个脚本把原版物品模型代码重新解析成保留镜像、UV、局部骨骼和零厚度面的网格定义。资源重新导入后需要重新构建客户端。

验证结果：JDK 25 下 `:auth-protocol:test :build` 通过，303 项客户端测试和 8 项认证协议测试全部通过，客户端完整性校验通过 1,249 个条目。最终 JAR 为 `build/libs/setsuna-26.1.2-B14.jar`。`build/reports/cataclysm/source-asset-audit.json` 记录了 100 张本地灾变贴图与原始 JAR 的字节哈希核对，以及 25 个立体物品的原始骨骼指纹核对。

隔离启动检查确认 `AvatarRendererMixin`、`HumanoidArmorLayerMixin`、`WingsLayerMixin`、`ItemModelResolverMixin` 和 `CataclysmModelManagerMixin` 已应用，灾变饰品层和认证界面已初始化。检查在认证界面后停止，没有登录或进入世界，因此没有执行真实账号下的第一人称和第三人称实机画面确认。检查使用隔离的 `build/reports/cataclysm/smoke-run`，原有 `runs/client/options.txt` 的哈希未改变。
