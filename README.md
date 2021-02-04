
    本工程使用java实现的Powershell进程环境。使用同步机制向powershell进程输出流写入脚本指令，与此同时有一个独立的线程A一直监听该进程执行命令的输出。线程A初步解析进程输出结果，并动态关联到具体的实现类service对象，调起Service对象的process或asyProcess方法(同步或异步，支持配置)。

    该组件提供，服务端向客户端下发powershell脚本指令。使用者需，1.编写powershell脚本功能代码 2.对脚本输出的处理service（若没有实现任何service，则该组件会把输出写入日志文件）

    针对该组件的测试工程（基于实战使用示例）已经发布：https://github.com/lll-666/ppowershell-test.git
