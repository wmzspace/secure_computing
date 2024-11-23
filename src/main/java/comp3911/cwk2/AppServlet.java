package comp3911.cwk2;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

@SuppressWarnings("serial")
public class AppServlet extends HttpServlet {

  private static final String CONNECTION_URL = "jdbc:sqlite:db.sqlite3";
  private static final String AUTH_QUERY = "select * from user where username=? and password=?";
  private static final String SEARCH_QUERY = "select * from patient where surname=? collate nocase";

  private final Configuration fm = new Configuration(Configuration.VERSION_2_3_28);
  private Connection database;

  @Override
  public void init() throws ServletException {
    configureTemplateEngine();
    connectToDatabase();
  }

  private void configureTemplateEngine() throws ServletException {
    try {
      fm.setDirectoryForTemplateLoading(new File("./templates"));
      fm.setDefaultEncoding("UTF-8");
      fm.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
      fm.setLogTemplateExceptions(false);
      fm.setWrapUncheckedExceptions(true);
    }
    catch (IOException error) {
      throw new ServletException(error.getMessage());
    }
  }

  private void connectToDatabase() throws ServletException {
    try {
      database = DriverManager.getConnection(CONNECTION_URL);
    }
    catch (SQLException error) {
      throw new ServletException(error.getMessage());
    }
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
   throws ServletException, IOException {
    try {
      Template template = fm.getTemplate("login.html");
      template.process(null, response.getWriter());
      response.setContentType("text/html");
      response.setStatus(HttpServletResponse.SC_OK);
    }
    catch (TemplateException error) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
   throws ServletException, IOException {
     // Get form parameters
    String username = request.getParameter("username");
    String password = request.getParameter("password");
    String surname = request.getParameter("surname");
    try{
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(password.getBytes());
      byte[] digest = md.digest();
      // 将字节转换为十六进制字符串
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      password = sb.toString();
      System.out.println(password);
    }catch (Exception error){
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    try {
      if (authenticated(username, password)) {
        // Get search results and merge with template
        Map<String, Object> model = new HashMap<>();
        model.put("records", searchResults(surname));
        Template template = fm.getTemplate("details.html");
        template.process(model, response.getWriter());
      }
      else {
        Template template = fm.getTemplate("invalid.html");
        template.process(null, response.getWriter());
      }
      response.setContentType("text/html");
      response.setStatus(HttpServletResponse.SC_OK);
    }
    catch (Exception error) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private boolean authenticated(String username, String password) throws SQLException {
    PreparedStatement query = database.prepareStatement(AUTH_QUERY);
    query.setString(1, username);
    query.setString(2, password);
    ResultSet results = query.executeQuery();
    return results.next();
  }

  private List<Record> searchResults(String surname) throws SQLException {
    List<Record> records = new ArrayList<>();
    // String query = String.format(SEARCH_QUERY, surname);
    PreparedStatement query = database.prepareStatement(SEARCH_QUERY);
    query.setString(1, surname);
    ResultSet results = query.executeQuery();
    while (results.next()) {
      Record rec = new Record();
      rec.setSurname(results.getString(2));
      rec.setForename(results.getString(3));
      rec.setAddress(results.getString(4));
      rec.setDateOfBirth(results.getString(5));
      rec.setDoctorId(results.getString(6));
      rec.setDiagnosis(results.getString(7));
      records.add(rec);
    }
    return records;
  }
}
