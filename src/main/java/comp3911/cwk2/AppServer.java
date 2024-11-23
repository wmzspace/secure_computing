package comp3911.cwk2;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;

public class AppServer {
  public static void main(String[] args) throws Exception {
    Log.setLog(new StdErrLog());

    // 创建线程池，设置线程池的最小和最大线程数
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(100); // 最大线程数
    threadPool.setMinThreads(10); // 最小线程数
    threadPool.setIdleTimeout(30000); // 空闲线程超时时间（毫秒）

    // 创建服务器并使用线程池
    Server server = new Server(threadPool);

    // 创建 ServerConnector 并设置端口
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8080); // 设置端口为 8080
    server.addConnector(connector);

    // 配置 ServletHandler
    ServletHandler handler = new ServletHandler();
    handler.addServletWithMapping(AppServlet.class, "/*");

    server.setHandler(handler);

    // 启动服务器
    server.start();
    server.join();
  }
}