# Nowlog V1 - Design Spec

## Overview

Nowlog 是一个随手记 Android 应用。用户可以快速输入文字笔记，系统自动记录当前时间，形成日记式条目。

## Architecture

**双 Activity 架构**，便于后续扩展：

- `MainActivity` — 笔记列表页
- `NoteEditActivity` — 新建笔记页

## Data Model

### Note Entity (Room)

| Field     | Type   | Description            |
|-----------|--------|------------------------|
| id        | long   | 主键，自增              |
| content   | String | 笔记文字内容            |
| createdAt | long   | 创建时间戳（毫秒）       |

### NoteDao

- `insert(Note)` — 插入新笔记
- `getAll()` — 查询所有笔记，按 createdAt 倒序
- `delete(Note)` — 删除指定笔记

### AppDatabase

- Room 数据库单例，版本 1

## UI Design

### MainActivity（笔记列表页）

- **Toolbar**：显示应用名 "Nowlog"
- **RecyclerView**：卡片式列表
  - 每张卡片：笔记内容（最多3行，超出省略）+ 时间标签
  - 空状态：无笔记时显示提示文字
- **FAB**：右下角浮动按钮，点击跳转 NoteEditActivity
- **删除交互**：长按卡片弹出确认对话框

### NoteEditActivity（新建笔记页）

- **Toolbar**：带返回箭头
- **EditText**：多行输入，自动获取焦点弹出键盘
- **保存按钮**：底部按钮
- **校验**：空内容不能保存，提示用户
- 保存后自动返回列表页

## Time Display

使用 `util/TimeFormatter.java` 工具类：

- 3天内：相对时间（"刚刚"、"5分钟前"、"X小时前"、"昨天"、"X天前"）
- 超过3天：中文日期格式（"2026年5月28日 20:30"）

## Async Database Operations

- 使用 `ExecutorService` 在后台线程执行 Room 操作
- 通过 `runOnUiThread` 回到主线程更新 UI
- 在 `onResume()` 中刷新列表，确保从 NoteEditActivity 返回后自动更新

## Project Structure

```
com.example.nowlog/
├── MainActivity.java          // 笔记列表
├── NoteEditActivity.java      // 新建笔记
├── data/
│   ├── Note.java              // Room 实体
│   ├── NoteDao.java           // DAO 接口
│   └── AppDatabase.java       // Room 数据库单例
├── adapter/
│   └── NoteAdapter.java       // RecyclerView 适配器
└── util/
    └── TimeFormatter.java     // 时间格式化工具
```

## Dependencies

在现有依赖基础上添加：
- `androidx.room:room-runtime`
- `androidx.room:room-compiler` (annotationProcessor)

## Key Flows

1. **启动** → MainActivity 在 onResume 从 Room 加载笔记 → 按时间倒序显示
2. **新建** → FAB → NoteEditActivity → 输入内容 → 保存（校验非空）→ 写入 Room → 返回列表
3. **删除** → 长按卡片 → 确认对话框 → 删除 → 列表刷新
