package comp3911.cwk2;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.github.cdimascio.dotenv.Dotenv;
import org.mindrot.jbcrypt.BCrypt;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.*;

@SuppressWarnings("serial")
public class AppServlet extends HttpServlet {
    //  private static final String CONNECTION_URL = "jdbc:sqlite:db.sqlite3";
    private static final Dotenv dotenv = Dotenv.load(); // 加载 .env 文件
    private static final String CONNECTION_URL = dotenv.get("DB_CONNECTION_URL"); // 从 .env 文件获取变量
    private static final String AUTH_QUERY = "select * from user where username=? and password=?";
    private static final String SEARCH_QUERY = "select * from patient where surname=? collate nocase";
    private static final String CAPTCHA_SESSION_KEY = "captcha";
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
            if (CONNECTION_URL != null) {
                database = DriverManager.getConnection(CONNECTION_URL);
            } else {
                throw new ServletException("Connection URL not specified");
            }
        } catch (SQLException error) {
            throw new ServletException(error.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if ("/captcha".equals(path)) {
            // Generate CAPTCHA and store it in the session
            String captchaText = generateCaptchaText();
            HttpSession session = request.getSession(true); // Create a new session if none exists
            session.setAttribute(CAPTCHA_SESSION_KEY, captchaText);

            BufferedImage captchaImage = generateCaptchaImage(captchaText);
            response.setContentType("image/png");
            ImageIO.write(captchaImage, "png", response.getOutputStream());
            return;
        }

        // Render login.html
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
        String captcha = request.getParameter("captcha");

        HttpSession session = request.getSession();
        String expectedCaptcha = (String) session.getAttribute(CAPTCHA_SESSION_KEY);

        if (isInvalidateInput(username) || isInvalidateInput(password) || isInvalidateInput(surname) || isInvalidateInput(captcha)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input!");
            return;
        }

        if (!captcha.equals(expectedCaptcha)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid captcha!");
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

    private String generateCaptchaText() {
        int length = 6;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder captcha = new StringBuilder();
        for (int i = 0; i < length; i++) {
            captcha.append(chars.charAt(random.nextInt(chars.length())));
        }
        return captcha.toString();
    }

    private BufferedImage generateCaptchaImage(String captchaText) {
        int width = 160, height = 50;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 40));
        g.drawString(captchaText, 20, 35);
        g.dispose();
        return image;
    }
}
