package comp3911.cwk2;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;

public class AppServer {
  public static void main(String[] args) throws Exception {
    Log.setLog(new StdErrLog());

    // Create thread pool
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMaxThreads(100);
    threadPool.setMinThreads(10);
    threadPool.setIdleTimeout(30000);

    // Create server with thread pool
    Server server = new Server(threadPool);

    // Configure connector
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8080);
    server.addConnector(connector);

    // Configure ServletHandler with SessionHandler
    ServletHandler servletHandler = new ServletHandler();
    servletHandler.addServletWithMapping(AppServlet.class, "/*");

    // Add SessionHandler to enable session support
    SessionHandler sessionHandler = new SessionHandler();
    sessionHandler.setHandler(servletHandler);

    // Set the handler with session support
    server.setHandler(sessionHandler);

    // Start the server
    server.start();
    server.join();
  }
}