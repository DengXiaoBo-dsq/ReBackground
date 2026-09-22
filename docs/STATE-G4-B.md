# G4-B Pressure Endpoints 状态

## 判定

PASS

## 改动

- Pressure 可通过独立曲线分别控制 size、opacity、flow。
- 三个参数各自支持 influence 与 minimum ratio，不再共用单一线性乘法。
- opacity/flow 改为逐 stamp 输出，避免末点参数反向覆盖整根笔触。

## 测试结果

- 对 P=0 与 P=1 分别检查 size、opacity、flow 六个端点。
- 最大相对误差 `1.49e-8`，阈值 `≤1%`。
- 证据：`evidence/2026-09-22/G4/B/uncommitted/`。

## 遗留

- 无 G4-B 遗留。
