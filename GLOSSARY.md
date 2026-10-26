# Hereby Named

Minecraft Fabric 客户端与服务端区域划分、探索自动命名与地图集成模组。

## Language

**Biome Patch**:
探索区域内同种生物群系在空间上连续相邻构成的最小几何连通斑块。自动区域分析以群系斑块为图节点进行归并与跨度计算。
_Avoid_: Biome Chunk, Biome Area

**Tiny Patch Absorption**:
面积不超过 3 个区块且完全被单一主导斑块包围的微小异质群系斑块，自动并入主导斑块，避免区域内部产生未着色孔洞。
_Avoid_: Hole Filling, Micro Merge

**River Bridge**:
连接两侧同种主导分类且宽度不超过 128 格的狭窄河流斑块，作为拓扑粘合桥梁联通两岸斑块，并将河道区块纳入合并区域。
_Avoid_: River Connection, Water Seam

**Candidate**:
由一个或多个相邻的相容群系斑块聚合而成、待检验是否满足跨度与水体比例门槛的潜在自动区域。
_Avoid_: Draft Region, Region Proposal

**Region**:
具有唯一标识、显示名称、地形类型与明确区块所有权的命名地理区域，可在地图上高亮并在玩家进入时提示。
_Avoid_: Territory, Zone, Area

**Biome Override**:
玩家在配置界面显式指定的单个生物群系到特定地形分类的映射规则，优先于内置启发式推断。
_Avoid_: Custom Biome Rule, Biome Remap

**Default Name**:
预先定义的完整地域专名白名单，直接使用或根据其所属地形附加特定词尾。
_Avoid_: Whitelist Name, Static Name

**Root**:
用于随机组合生成全新专名素材的单字或精炼字素，具备独立的地形与气候偏好。
_Avoid_: Syllable, Word Piece, Prefix
