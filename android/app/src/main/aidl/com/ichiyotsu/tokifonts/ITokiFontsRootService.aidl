package com.ichiyotsu.tokifonts;

/**
 * TokiFontsPro 自己的 Shizuku UserService 业务接口。
 *
 * 上游 Shizuku-API 只提供“把你的 service 拉起来”的机制，不提供业务接口，
 * 所以这个 AIDL 由 Pro 自己写（见 `docs/source-integration.md` §1 与
 * `~/workspace/work/tokifonts-source-recon/shizuku.md` §5.4）。
 *
 * 服务跑在 Shizuku server 以 root（uid 0）或 shell（uid 2000，无线调试启动）
 * 身份经 `app_process` 拉起的独立进程里，不是普通应用进程：这里的 Context
 * 能力受限（`registerReceiver` / `getContentResolver` 等不能用），只做
 * 文件 staging + 命令执行这两件特权进程里必须做的事。其它逻辑（预检、
 * 状态机、驱动选择）都在普通应用进程的 Kotlin 代码里。
 *
 * 方法约定：返回 0 表示成功；非 0 是失败（exit code / -1 内部错误 /
 * -2 超时）。注意 AIDL 的 `out` 参数只支持 Parcelable，String 只能是
 * `in` 参数，所以 `runCommand` 的输出不走 `out`，而是跑完后用
 * [getLastStdout] / [getLastStderr] 再取一次（加载器是顺序跑命令的，
 * 不会有竞态）。
 */
interface ITokiFontsRootService {
    /**
     * 把 [content] 写到特权进程可见的 [destPath]（如 /data/local/tmp/w/ 下），
     * [executable] 为 true 时 chmod 755，否则 644。父目录不存在时创建。
     * 返回 0 成功，-1 写失败。
     */
    int stageFile(String destPath, in byte[] content, boolean executable);

    /**
     * 以特权身份跑 `sh -c [command]`，[timeoutMs] 毫秒超时。
     * 返回进程 exit code；超时返回 -2，启动失败返回 -1。
     * 输出用 [getLastStdout] / [getLastStderr] 取（各截断到约 64 KiB）。
     */
    int runCommand(String command, int timeoutMs);

    /** 最近一次 [runCommand] 的 stdout（还没跑过返回空串）。 */
    String getLastStdout();

    /** 最近一次 [runCommand] 的 stderr（还没跑过返回空串）。 */
    String getLastStderr();

    /**
     * 读特权进程可见的 [path]（如 /proc/modules、/data/local/tmp/w/*.log），
     * 最多 [maxBytes] 字节。读不到返回 null。
     */
    String readFile(String path, int maxBytes);

    /** 删文件或目录（rm -rf 语义，目录递归删）。返回 0 成功，-1 失败。 */
    int removePath(String path);
}
