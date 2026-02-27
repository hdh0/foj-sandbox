package com.ff.fojsandbox.pool;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class ContainerPool {

    @Resource
    private DockerClient dockerClient;

    // 每种语言分配的容器数量
    private static final int POOL_SIZE_PER_LANG = 3;

    // 定义镜像映射
    private static final Map<String, String> IMAGE_MAP = new HashMap<>();
    static {
        IMAGE_MAP.put("java", "eclipse-temurin:8-jdk-alpine");
        IMAGE_MAP.put("python", "python:3.11-alpine");
        IMAGE_MAP.put("cpp", "frolvlad/alpine-gxx");
    }

    // 每种语言对应的阻塞队列
    private final Map<String, BlockingQueue<String>> langPoolMap = new HashMap<>();

    @PostConstruct
    public void initPool() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("正在初始化容器池...");
        // 初始化阻塞队列 Map
        IMAGE_MAP.keySet().forEach(lang ->
                langPoolMap.put(lang, new LinkedBlockingQueue<>(POOL_SIZE_PER_LANG))
        );
        // 计算总任务数，使用固定大小的线程池
        int totalContainers = IMAGE_MAP.size() * POOL_SIZE_PER_LANG;
        int threadCount = Math.min(totalContainers, 20);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // 提交任务
        IMAGE_MAP.forEach((lang, image) -> {
            BlockingQueue<String> queue = langPoolMap.get(lang);
            for (int i = 0; i < POOL_SIZE_PER_LANG; i++) {
                // 为每个容器创建提交一个异步任务
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String containerId = createAndStartContainer(image);
                        boolean offered = queue.offer(containerId);
                    } catch (Exception e) {
                        log.error("初始化容器失败: language={}, image={}", lang, image, e);
                    }
                }, executor);
                futures.add(future);
            }
        });
        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            stopWatch.stop();
            log.info("容器池初始化完成，耗时: {} ms，当前可用容器总数: {}", stopWatch.getTotalTimeMillis(), totalContainers);
        } catch (Exception e) {
            log.error("容器池初始化过程中发生异常", e);
            throw new RuntimeException("容器池初始化失败", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 租借指定语言的容器
     */
    public String borrowContainer(String language) throws InterruptedException {
        BlockingQueue<String> queue = langPoolMap.get(language.toLowerCase());
        // 死等，直到该语言有空闲容器
        return queue.take();
    }

    /**
     * 归还并检查容器
     */
    public void returnContainer(String language, String containerId) {
        // 健康检查
        boolean isHealthy = isContainerHealthy(language, containerId);
        if (isHealthy) {
            boolean cleanSuccess = cleanContainer(containerId);
            if (cleanSuccess) {
                // 只有健康且清理成功的容器才放回池中
                langPoolMap.get(language).offer(containerId);
                return;
            } else {
                log.warn("容器清理失败，视为损坏: {}", containerId);
            }
        } else {
            log.warn("容器健康检查未通过: {}", containerId);
        }

        // 销毁重建
        log.error("正在销毁并补充容器: {}", containerId);
        replaceBrokenContainer(language, containerId);
    }

    // 判定容器是否健康
    private boolean isContainerHealthy(String language, String containerId) {
        try {
            InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = info.getState();
            // 检查运行状态
            if (!Boolean.TRUE.equals(state.getRunning())) return false;
            if (Boolean.TRUE.equals(state.getPaused())) return false;
            // 检查是否发生过 OOM
            if (Boolean.TRUE.equals(state.getOOMKilled())) {
                log.warn("容器曾发生OOM: {}", containerId);
                return false;
            }
            // 运行时功能检查 针对不同语言，检查编译器是否正常工作
            String checkCmd = getHealthCheckCmd(language);
            return execCmdWithExitCodeCheck(containerId, checkCmd);
        } catch (Exception e) {
            log.error("健康检查异常: {}", e.getMessage());
            return false;
        }
    }

    // 清理容器 杀死所有进程 + 清理文件
    private boolean cleanContainer(String containerId) {
        try {
            // ps -o pid,comm : 列出 PID 和 进程名
            // awk ... : 排除 pid 1, 排除 sh, 排除 ps, 排除 tail
            // xargs kill : 杀掉剩下的
            String cleanCmd = "ps -o pid,comm | awk 'NR>1 && $1!=1 && $2!=\"sh\" && $2!=\"ps\" && $2!=\"tail\" {print $1}' | xargs -r kill -9 || true && rm -rf /app/*";
            String execId = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", cleanCmd)
                    .exec()
                    .getId();
            dockerClient.execStartCmd(execId)
                    .exec(new ResultCallback.Adapter<>())
                    .awaitCompletion();
            Long exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCodeLong();
            return exitCode != null && exitCode == 0;

        } catch (Exception e) {
            log.error("清理容器异常: {}", containerId, e);
            return false;
        }
    }

    // 在容器内执行命令并检查退出码
    private boolean execCmdWithExitCodeCheck(String containerId, String cmd) {
        try {
            // 创建执行命令
            String execId = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true) //虽然不看输出，但通常需要attach才能正确执行
                    .withAttachStderr(true)
                    .withCmd("sh", "-c", cmd)
                    .exec()
                    .getId();

            // 启动执行
            boolean completed = dockerClient.execStartCmd(execId)
                    .exec(new ResultCallback.Adapter<>())
                    .awaitCompletion(2, TimeUnit.SECONDS); // 2秒超时

            if (!completed) return false; // 超时视为失败

            // 检查退出码
            InspectExecResponse response = dockerClient.inspectExecCmd(execId).exec();
            Long exitCode = response.getExitCodeLong();
            return exitCode != null && exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // 获取对应语言的健康检查命令
    private String getHealthCheckCmd(String language) {
        switch (language.toLowerCase()) {
            case "java": return "java -version";
            case "python": return "python3 --version";
            case "cpp": return "g++ --version";
            default: return "ls /app";
        }
    }

    // 销毁旧容器并补充新容器
    private void replaceBrokenContainer(String language, String oldContainerId) {
        try {
            // 销毁旧容器
            try {
                dockerClient.removeContainerCmd(oldContainerId).withForce(true).exec();
            } catch (Exception e) {
                log.warn("销毁旧容器出错: {}", e.getMessage());
            }
            // 创建容器
            String image = IMAGE_MAP.get(language);
            String newContainerId = createAndStartContainer(image);
            langPoolMap.get(language).offer(newContainerId);
            log.info("已成功补充新容器: {}", newContainerId);
        } catch (Exception e) {
            log.error("补充容器失败，池中将缺少容器! : {}", e.getMessage());
        }
    }

    // 创建并启动容器，返回容器ID
    private String createAndStartContainer(String imageName) {
        HostConfig hostConfig = new HostConfig()
                .withMemory(64 * 1024 * 1024L)
                .withMemorySwap(0L)
                .withCpuCount(1L)
                .withPidsLimit(100L) // 限制进程数量
                .withInit(true)      // 使用 init 进程处理僵尸进程
                .withSecurityOpts(Collections.singletonList("no-new-privileges"))
                .withCapDrop(Capability.ALL);

        CreateContainerResponse response = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withWorkingDir("/app")
                .withCmd("sh", "-c", "mkdir -p /app && tail -f /dev/null")
                .exec();

        String containerId = response.getId();
        dockerClient.startContainerCmd(containerId).exec();
        return containerId;
    }

    @PreDestroy
    public void destroyPool() {
        log.info("正在销毁容器池并清理容器...");
        // 先把所有队列里的容器 ID 取出来放到一个 List 里
        List<String> allContainerIds = new ArrayList<>();
        langPoolMap.forEach((lang, queue) -> {
            List<String> ids = new ArrayList<>();
            queue.drainTo(ids); // 原子性地将队列排空到 List
            if (!ids.isEmpty()) {
                log.info("准备清理语言: {}, 数量: {}", lang, ids.size());
                allContainerIds.addAll(ids);
            }
        });

        if (allContainerIds.isEmpty()) {
            log.info("容器池为空，无需清理。");
            return;
        }

        // 创建线程池
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            // 等待所有任务完成
            CompletableFuture<Void> allTask = CompletableFuture.allOf(
                    allContainerIds.stream()
                            .map(containerId -> CompletableFuture.runAsync(() -> {
                                try {
                                    dockerClient.removeContainerCmd(containerId)
                                            .withForce(true)
                                            .exec();
                                    log.info("成功销毁容器: {}", containerId);
                                } catch (com.github.dockerjava.api.exception.NotFoundException e) {
                                    log.warn("容器已不存在，跳过: {}", containerId);
                                } catch (Exception e) {
                                    log.error("销毁容器失败: {}, 原因: {}", containerId, e.getMessage());
                                }
                            }, executor)).toArray(CompletableFuture[]::new)
            );
            // 设置全局超时时间
            allTask.get(10, TimeUnit.SECONDS);
            log.info("容器池清理完成，共处理 {} 个容器。", allContainerIds.size());
        } catch (TimeoutException e) {
            log.error("容器清理超时！部分容器可能未被移除。请检查 Docker 守护进程状态。");
        } catch (Exception e) {
            log.error("容器清理过程中发生异常: {}", e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }
}