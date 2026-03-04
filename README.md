# FOJ 代码沙箱 (FOJ Code Sandbox)

## 📖 项目介绍

FOJ-Sandbox 是一个基于 Spring Boot 开发的安全代码执行沙箱服务，主要用于在线判题系统（Online Judge）。该服务利用 Docker 容器技术来实现安全、隔离的代码运行环境，防止恶意代码对宿主机造成破坏，并对代码运行的时间和内存进行严格限制与统计。

**核心特性：**
* **多语言支持**：目前支持 `Java`、`Python` 和 `C++` 等主流编程语言。
* **隔离安全**：基于 Docker 容器化技术运行用户提交的代码，并使用**容器池技术**加速。
* **资源控制**：精确监控代码执行消耗的内存与时间限制。
* **轻量级通信**：提供易于调用的 RESTful API 接口，可轻松集成到任何微服务架构中。
* **异步消息队列处理**：支持通过 RabbitMQ 和 Redis 进行异步的**高并发**判题请求处理。

---

## 🛠 环境准备

### 1. 基础环境
* **系统要求: Ubuntu22.04 或 WSL2(docker desktop)**
* **Java**: JDK 1.8
* **Docker**: 必须安装并启动 Docker 服务
* **Redis**: 用于缓存等相关业务（可选）
* **RabbitMQ**: 用于异步消息队列通信（可选）

### 2. 环境配置

+ 安装 jdk 1.8
```bash
sudo apt update
sudo apt install openjdk-8-jdk-headless -y
```

+ 拉取 docker 镜像
```bash
# C++
docker pull frolvlad/alpine-gxx

# Python
docker pull python:3.11-alpine

# Java
docker pull eclipse-temurin:8-jdk-alpine
```

### 3. 配置文件修改

1. 自定义容器池大小和并发数，默认值为 1
2. 如果使用异步方式判题 `rabbitmq.enabled` 需设置为 `true`，并选择结果返回方式（Redis 或 RabbitMQ）

```yaml
sandbox:
  judge:
    pool-sizes:
      java: 1
      python: 1
      cpp: 1
    concurrences:
      java: 1
      python: 1
      cpp: 1
  rabbitmq:
    enabled: false
    result-type: mq # mq/redis
```

---

## 🚀 使用方式

### 1. HTTP API 接口（同步方式）

项目启动后，默认暴露出以下 HTTP 接口供主业务后端进行远程调用。

#### 1.1 健康检查
用于检测代码沙箱服务是否正常在线。
* **接口地址**：`GET /ping`
* **响应结果**：返回 `"pong"` 字符串。

#### 1.2 执行代码
核心业务接口，提交代码并返回执行结果。
* **接口地址**：`POST /executeCode`
* **请求头**：`Content-Type: application/json`
* **请求体 (Request Body)**：

```json
{
  "inputList": [
    "1 2",
    "1 2"
  ],
  "code": "a, b = map(int, input().split())\nprint(a + b)\n",
  "language": "python"
}
```
* **响应体 (Response Body)**：

```json
{
  "outputList": [
    "3\n",
    "3\n"
  ],
  "message": "执行成功",
  "status": 1,
  "judgeInfo": {
    "message": null,
    "time": 247,
    "memory": 1
  }
}
```

---

### 2. 消息队列（异步方式）

为了应对**高并发**判题请求，沙箱系统支持通过 RabbitMQ 进行异步处理：

#### 2.1 队列配置
每种编程语言对应一个专属的判题队列，并且消费者线程数与 Docker 容器池大小相同。
- **Java 队列**: `judge_queue_java`
- **Python 队列**: `judge_queue_python`
- **C++ 队列**: `judge_queue_cpp`


#### 2.2 异步请求体
向相应的语言队列发送 JSON 格式的字符串，结构与 API 请求相同：
```json
{
  "submissionId": "1001",
  "inputList": [
    "1 2",
    "3 4"
  ],
  "code": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args){\n        Scanner scanner = new Scanner(System.in);\n        int a = scanner.nextInt();\n        int b = scanner.nextInt();\n        System.out.println(a + b);\n    }\n}",
  "language": "java"
}
```

#### 2.3 处理结果返回机制

配置系统判题结果返回方式（在 `application.yaml` 中配置 `sandbox.rabbitmq.result-type: redis` 或是 `mq`）：

* **基于 Redis 的结果返回 (`redis`)**:
  沙箱系统会将判题结果写入 Redis （Key 格式如: `judge:result:{submissionId}`），同时通过 Redis 发布订阅频道（Channel: `judge_result_channel`）发出通知。

* **基于 RabbitMQ 的结果返回 (`mq`)**:
  沙箱系统在判题完成后，会将执行响应封装为 `JudgeResultEvent`，发送到 RabbitMQ 的 `judge_result_exchange` 交换机并带有 `judge_result_routing_key` 的路由键。主业务可以监听相应的队列来获取结果。