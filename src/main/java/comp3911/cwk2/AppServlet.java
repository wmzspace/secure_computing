package comp3911.cwk2;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.github.cdimascio.dotenv.Dotenv;
import org.mindrot.jbcrypt.BCrypt;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("serial")
public class AppServlet extends HttpServlet {
    //  private static final String CONNECTION_URL = "jdbc:sqlite:db.sqlite3";
    private static final Dotenv dotenv = Dotenv.load(); // 加载 .env 文件
    private static final String CONNECTION_URL = dotenv.get("DB_CONNECTION_URL"); // 从 .env 文件获取变量
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
        } catch (IOException error) {
            throw new ServletException(error.getMessage());
        }
    }

    private void connectToDatabase() throws ServletException {
        try {
            database = DriverManager.getConnection(CONNECTION_URL);
        } catch (SQLException error) {
            throw new ServletException(error.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            Template template = fm.getTemplate("login.html");
            template.process(null, response.getWriter());
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (TemplateException error) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }


    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }


    private boolean isInvalidateInput(String input) {
        return input == null || input.trim().isEmpty();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String surname = request.getParameter("surname");

        if (isInvalidateInput(username) || isInvalidateInput(password) || isInvalidateInput(surname)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input!");
            return;
        }


        try {
            if (authenticated(username, password)) {
                Map<String, Object> model = new HashMap<>();
                model.put("records", searchResults(surname));
                Template template = fm.getTemplate("details.html");
                template.process(model, response.getWriter());
            } else {
                Template template = fm.getTemplate("invalid.html");
                template.process(null, response.getWriter());
            }
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception error) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "The system is busy, please try again later.");
        }
    }

    private boolean authenticated(String username, String password) throws SQLException {
        try (PreparedStatement query = database.prepareStatement("SELECT password FROM user WHERE username = ?")) {
            query.setString(1, username);
            try (ResultSet results = query.executeQuery()) {
                if (results.next()) {
                    String storedHash = results.getString("password"); // 数据库中的哈希值
                    return BCrypt.checkpw(password, storedHash); // 验证用户输入的密码
                }
            }
        }
        return false; // 用户名不存在或密码不匹配
    }


    private List<Record> searchResults(String surname) throws SQLException {
        List<Record> records = new ArrayList<>();
        PreparedStatement query = database.prepareStatement(SEARCH_QUERY);
        query.setString(1, surname); // 参数绑定，防止 SQL 注入
        try (ResultSet results = query.executeQuery()) {
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
        }
        return records;
    }
}
