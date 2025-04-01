# CustomAutoJS 测试框架

这个仓库包含用于 CustomAutoJS 监控模块的全面测试框架，支持多种测试类型和场景。

## 功能特点

- **多样化测试类型**：
  - 单元测试
  - 集成测试
  - 性能测试
  - 压力测试
  - 内存泄漏测试
  - UI 可视化测试

- **自动化测试**：
  - 支持 CI/CD 环境
  - 生成详细测试报告
  - 可配置的测试执行

- **全面的测试覆盖**：
  - 推荐系统测试
  - JavaScript 集成测试
  - API 功能测试
  - 性能基准测试

## 使用方法

### 运行全部测试

```bash
./autojs-api/monitor/scripts/run_tests.sh --all-tests
```

### 运行特定类型的测试

```bash
# 运行单元测试
./autojs-api/monitor/scripts/run_tests.sh --unit-only

# 运行集成测试
./autojs-api/monitor/scripts/run_tests.sh --integration-only

# 运行性能测试
./autojs-api/monitor/scripts/run_tests.sh --performance-test

# 运行压力测试
./autojs-api/monitor/scripts/run_tests.sh --stress-test

# 运行内存测试
./autojs-api/monitor/scripts/run_tests.sh --memory-test

# 运行包含视觉测试
./autojs-api/monitor/scripts/run_tests.sh --visual-test
```

### 生成测试报告

```bash
./autojs-api/monitor/scripts/run_tests.sh --all-tests --report ./test-reports
```

### CI 模式

```bash
./autojs-api/monitor/scripts/run_tests.sh --all-tests --ci-mode
```

## 文件结构

- `autojs-api/monitor/scripts/run_tests.sh` - 主测试执行脚本
- `autojs-api/monitor/src/test/resources/test_config.json` - 测试配置文件
- `autojs-api/monitor/src/test/resources/test_runner.js` - JavaScript 测试运行器
- `autojs-api/monitor/src/test/resources/visual_recommendation_test.js` - UI 可视化测试
- `autojs-api/monitor/src/test/java/` - 包含 Kotlin 测试类

## 配置测试

修改 `test_config.json` 文件以配置不同的测试参数:

```json
{
  "testConfig": {
    "unitTests": {
      "enabled": true,
      "timeoutMs": 30000
    },
    "performanceTests": {
      "iterations": 100,
      "warmupIterations": 10
    },
    "stressTests": {
      "concurrentThreads": 4,
      "iterationsPerThread": 50
    }
  }
}
```

## 许可证

© 2025 Innora Solutions