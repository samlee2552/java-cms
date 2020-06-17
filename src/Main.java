import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class Main {
	public static void main(String[] args) {
		App app = new App();
		app.start();
	}
}

// Session
// 현재 사용자가 이용중인 정보
// 이 안의 정보는 사용자가 프로그램을 사용할 때 동안은 계속 유지된다.
class Session {
	private Board currentBoard;

	public Board getCurrentBoard() {
		return currentBoard;
	}

	public void setCurrentBoard(Board currentBoard) {
		this.currentBoard = currentBoard;
	}

}

// DB 커넥션(진짜 DB와의 연결을 담당)
class DBConnection {
	private Connection connection;

	public void connect() {
		String url = "jdbc:mysql://localhost:3306/site5?serverTimezone=UTC";
		String user = "sbsst";
		String password = "sbs123414";
		String driverName = "com.mysql.cj.jdbc.Driver";

		try {
			// ① 로드(카카오 택시에 `com.mysql.cj.jdbc.Driver` 라는 실제 택시 드라이버를 등록)
			// 하지만 개발자는 실제로 `com.mysql.cj.jdbc.Driver`를 다룰 일은 없다.
			// 내부적으로 JDBC가 알아서 다 해주기 때문에 우리는 JDBC의 DriverManager 를 통해서 DB와의 연결을 얻으면 된다.
			Class.forName(driverName);

			// ② 연결
			connection = DriverManager.getConnection(url, user, password);
			System.out.println("연결 성공");
		} catch (ClassNotFoundException e) {
			// `com.mysql.cj.jdbc.Driver` 라는 클래스가 라이브러리로 추가되지 않았다면 오류발생
			System.out.println("[로드 오류]\n" + e.getStackTrace());
		} catch (SQLException e) {
			// DB접속정보가 틀렸다면 오류발생
			System.out.println("[연결 오류]\n" + e.getStackTrace());
		}
	}

	public int selectRowIntValue(String sql) {
		Map<String, Object> row = selectRow(sql);

		for (String key : row.keySet()) {
			Object value = row.get(key);

			if (value instanceof String) {
				return Integer.parseInt((String) value);
			}
			if (value instanceof Long) {
				return (int) (long) value;
			} else {
				return (int) value;
			}
		}

		return -1;
	}

	public String selectRowStringValue(String sql) {
		Map<String, Object> row = selectRow(sql);

		for (String key : row.keySet()) {
			Object value = row.get(key);

			return value + "";
		}

		return "";
	}

	public boolean selectRowBooleanValue(String sql) {
		int rs = selectRowIntValue(sql);

		return rs == 1;
	}

	public Map<String, Object> selectRow(String sql) {
		List<Map<String, Object>> rows = selectRows(sql);

		if (rows.size() > 0) {
			return rows.get(0);
		}

		return new HashMap<>();
	}

	public List<Map<String, Object>> selectRows(String sql) {
		// SQL을 적는 문서파일
		Statement statement = null;
		ResultSet rs = null;

		List<Map<String, Object>> rows = new ArrayList<>();

		try {
			statement = connection.createStatement();
			rs = statement.executeQuery(sql);
			// ResultSet 의 MetaData를 가져온다.
			ResultSetMetaData metaData = rs.getMetaData();
			// ResultSet 의 Column의 갯수를 가져온다.
			int columnSize = metaData.getColumnCount();

			// rs의 내용을 돌려준다.
			while (rs.next()) {
				// 내부에서 map을 초기화
				Map<String, Object> row = new HashMap<>();

				for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
					String columnName = metaData.getColumnName(columnIndex + 1);
					// map에 값을 입력 map.put(columnName, columnName으로 getString)
					row.put(columnName, rs.getObject(columnName));
				}
				// list에 저장
				rows.add(row);
			}
		} catch (SQLException e) {
			System.err.printf("[SELECT 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}

			if (rs != null) {
				rs.close();
			}
		} catch (SQLException e) {
			System.err.println("[SELECT 종료 오류]\n" + e.getStackTrace());
		}

		return rows;
	}

	public int update(String sql) {
		// UPDATE 명령으로 몇개의 데이터가 수정되었는지
		int affectedRows = 0;

		// SQL을 적는 문서파일
		Statement statement = null;

		try {
			statement = connection.createStatement();
			affectedRows = statement.executeUpdate(sql);
		} catch (SQLException e) {
			System.err.printf("[UPDATE 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}
		} catch (SQLException e) {
			System.err.println("[UPDATE 종료 오류]\n" + e.getStackTrace());
		}

		return affectedRows;
	}

	public int insert(String sql) {
		int id = -1;

		// SQL을 적는 문서파일
		Statement statement = null;
		// SQL의 실행결과 보고서
		ResultSet rs = null;

		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
			rs = statement.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}
		} catch (SQLException e) {
			System.err.printf("[INSERT 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}

			if (rs != null) {
				rs.close();
			}
		} catch (SQLException e) {
			System.err.println("[INSERT 종료 오류]\n" + e.getStackTrace());
		}

		return id;
	}

	public void close() {
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (SQLException e) {
			System.err.println("[닫기 오류]\n" + e.getStackTrace());
		}
	}

	public List<Article> getArticlesByBoardCode(String code) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<Board> getBoards() {
		// TODO Auto-generated method stub
		return null;
	}

	public Board getBoardByCode(String code) {
		// TODO Auto-generated method stub
		return null;
	}

	public int saveBoard(Board board) {
		// TODO Auto-generated method stub
		return 0;
	}

	public Board getBoard(int id) {
		// TODO Auto-generated method stub
		return null;
	}
}

// Factory
// 프로그램 전체에서 공유되는 객체 리모콘을 보관하는 클래스

class Factory {
	private static Session session;
//	private static DB db;
	private static DBConnection dbConnection;
	private static BuildService buildService;
	private static ArticleService articleService;
	private static ArticleDao articleDao;

	private static Scanner scanner;

	public static DBConnection getDBConnection() {
		if (dbConnection == null) {
			dbConnection = new DBConnection();
		}

		return dbConnection;
	}

	public static Session getSession() {
		if (session == null) {
			session = new Session();
		}

		return session;
	}

	public static Scanner getScanner() {
		if (scanner == null) {
			scanner = new Scanner(System.in);
		}

		return scanner;
	}

	public static ArticleService getArticleService() {
		if (articleService == null) {
			articleService = new ArticleService();
		}

		return articleService;
	}

	public static ArticleDao getArticleDao() {
		if (articleDao == null) {
			articleDao = new ArticleDao();
		}

		return articleDao;
	}

	public static BuildService getBuildService() {
		if (buildService == null) {
			buildService = new BuildService();
		}

		return buildService;
	}
}

// App
class App {
	private Map<String, Controller> controllers;

	// 컨트롤러 만들고 한곳에 정리
	// 나중에 컨트롤러 이름으로 쉽게 찾아쓸 수 있게 하려고 Map 사용
	void initControllers() {
		controllers = new HashMap<>();
		controllers.put("build", new BuildController());
		controllers.put("article", new ArticleController());

	}

	public App() {
		// 컨트롤러 등록
		initControllers();

		Factory.getDBConnection().connect();

		// 관리자 회원 생성
//		Factory.getMemberService().join("admin", "admin", "관리자");

		// 공지사항 게시판 생성
		Factory.getArticleService().makeBoard("공지시항", "notice");
		// 자유 게시판 생성
		Factory.getArticleService().makeBoard("자유게시판", "free");

		// 현재 게시판을 1번 게시판으로 선택
		Factory.getSession().setCurrentBoard(Factory.getArticleService().getBoard(1));
		// 임시 : 현재 로그인 된 회원은 1번 회원으로 지정, 이건 나중에 회원가입, 로그인 추가되면 제거해야함
	}

	public void start() {

		while (true) {
			System.out.printf("명령어 : ");
			String command = Factory.getScanner().nextLine().trim();

			if (command.length() == 0) {
				continue;
			} else if (command.equals("exit")) {
				break;
			}

			Request reqeust = new Request(command);

			if (reqeust.isValidRequest() == false) {
				continue;
			}

			if (controllers.containsKey(reqeust.getControllerName()) == false) {
				continue;
			}

			controllers.get(reqeust.getControllerName()).doAction(reqeust);
		}

		Factory.getDBConnection().close();
		Factory.getScanner().close();
	}
}

// Request
class Request {
	private String requestStr;
	private String controllerName;
	private String actionName;
	private String arg1;
	private String arg2;
	private String arg3;

	boolean isValidRequest() {
		return actionName != null;
	}

	Request(String requestStr) {
		this.requestStr = requestStr;
		String[] requestStrBits = requestStr.split(" ");
		this.controllerName = requestStrBits[0];

		if (requestStrBits.length > 1) {
			this.actionName = requestStrBits[1];
		}

		if (requestStrBits.length > 2) {
			this.arg1 = requestStrBits[2];
		}

		if (requestStrBits.length > 3) {
			this.arg2 = requestStrBits[3];
		}

		if (requestStrBits.length > 4) {
			this.arg3 = requestStrBits[4];
		}
	}

	public String getControllerName() {
		return controllerName;
	}

	public void setControllerName(String controllerName) {
		this.controllerName = controllerName;
	}

	public String getActionName() {
		return actionName;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public String getArg1() {
		return arg1;
	}

	public void setArg1(String arg1) {
		this.arg1 = arg1;
	}

	public String getArg2() {
		return arg2;
	}

	public void setArg2(String arg2) {
		this.arg2 = arg2;
	}

	public String getArg3() {
		return arg3;
	}

	public void setArg3(String arg3) {
		this.arg3 = arg3;
	}
}

// Controller
abstract class Controller {
	abstract void doAction(Request reqeust);
}

class ArticleController extends Controller {
	private ArticleService articleService;

	ArticleController() {
		articleService = Factory.getArticleService();
	}

	public void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("list")) {
			actionList(reqeust);
		} else if (reqeust.getActionName().equals("write")) {
			actionWrite(reqeust);
		} else if (reqeust.getActionName().equals("modify")) {
			if (reqeust.getArg1() == null) {
				System.out.println("게시물 번호를 입력해 주세요.");
			} else {
				int id = Integer.parseInt(reqeust.getArg1());
				actionModify(id);
			}
		} else if (reqeust.getActionName().equals("delete")) {
			if (reqeust.getArg1() == null) {
				System.out.println("삭제할 게시물 번호를 입력해 주세요.");
			} else {
				int id = Integer.parseInt(reqeust.getArg1());
				actionDelete(id);
			}
		} else if (reqeust.getActionName().equals("detail")) {
			if (reqeust.getArg1() == null) {
				System.out.println("조회할 게시물 번호를 입력해 주세요.");
			} else {
				int id = Integer.parseInt(reqeust.getArg1());
				actionDetail(id);
			}
		} else if (reqeust.getActionName().equals("makeboard")) {
			actionMakeBoard(reqeust);
		}
	}

	private void actionMakeBoard(Request request) {
		List<Board> boards = articleService.getBoards();
		String boardName;
		String boardCode;

		while (true) {
			System.out.printf("생성하실 게시판 이름을 입력해 주세요:");
			boardName = Factory.getScanner().nextLine();
			System.out.printf("생성하실 게시판 코드를 입력해 주세요:");
			boardCode = Factory.getScanner().nextLine();
			if (articleService.makeBoard(boardName, boardCode) == -1) {
				System.out.println("이미 사용중인 코드입니다.");
				continue;
			} else {
				break;
			}
		}
		articleService.makeBoard(boardName, boardCode);
	}

	private void actionDetail(int id) {

		Article article = articleService.getArticleById(id);
		System.out.printf("id = %d, boardId = %d, title = %s, body = %s\n", article.getId(), article.getBoardId(),
				article.getTitle(), article.getBody());
	}

	private void actionDelete(int id) {
		articleService.delete(id);
	}

	private void actionModify(int id) {

		System.out.println("== 게시물 수정 ==");
		System.out.printf("새 제목 : ");
		String title = Factory.getScanner().nextLine();
		System.out.printf("새 내용 : ");
		String body = Factory.getScanner().nextLine();

		articleService.modify(id, title, body);
		System.out.println("게시물이 수정되었습니다");
	}

	private void actionList(Request reqeust) {
		List<Article> articles = articleService.getArticles();

		System.out.println("== 게시물 리스트 시작 ==");
		for (Article article : articles) {
			System.out.printf("%d, %d, %s, %s\n", article.getId(), article.getBoardId(), article.getRegDate(),
					article.getTitle());
		}
		System.out.println("== 게시물 리스트 끝 ==");
	}

	private void actionWrite(Request reqeust) {
		System.out.printf("제목 : ");
		String title = Factory.getScanner().nextLine();
		System.out.printf("내용 : ");
		String body = Factory.getScanner().nextLine();

		// 현재 게시판 id 가져오기
		int boardId = Factory.getSession().getCurrentBoard().getId();
		System.out.println(boardId);

		// 현재 로그인한 회원의 id 가져오기
//		int memberId = Factory.getSession().getLoginedMember().getId();
		int newId = articleService.write(boardId, title, body);

		System.out.printf("%d번 글이 생성되었습니다.\n", newId);
	}
}

class BuildController extends Controller {
	private BuildService buildService;

	BuildController() {
		buildService = Factory.getBuildService();
	}

	@Override
	void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("site")) {
			actionSite(reqeust);
		}
	}

	private void actionSite(Request reqeust) {
		buildService.buildSite();
	}
}

// Service
class BuildService {
	ArticleService articleService;

	BuildService() {
		articleService = Factory.getArticleService();
	}

	public void buildSite() {
		Util.makeDir("site");
		Util.makeDir("site/article");

		String head = Util.getFileContents("site_template/part/head.html");
		String foot = Util.getFileContents("site_template/part/foot.html");

		// 각 게시판 별 게시물리스트 페이지 생성
		List<Board> boards = articleService.getBoards();

		for (Board board : boards) {
			String fileName = board.getCode() + "-list-1.html";

			String html = "";

			List<Article> articles = articleService.getArticlesByBoardCode(board.getCode());

			String template = Util.getFileContents("site_template/article/list.html");

			for (Article article : articles) {
				html += "<tr>";
				html += "<td>" + article.getId() + "</td>";
				html += "<td>" + article.getRegDate() + "</td>";
				html += "<td><a href=\"" + article.getId() + ".html\">" + article.getTitle() + "</a></td>";
				html += "</tr>";
			}

			html = template.replace("${TR}", html);

			html = head + html + foot;

			Util.writeFileContents("site/article/" + fileName, html);
		}

		// 게시물 별 파일 생성
		List<Article> articles = articleService.getArticles();

		for (Article article : articles) {
			String html = "";

			html += "<div>제목 : " + article.getTitle() + "</div>";
			html += "<div>내용 : " + article.getBody() + "</div>";
			html += "<div><a href=\"" + (article.getId() - 1) + ".html\">이전글</a></div>";
			html += "<div><a href=\"" + (article.getId() + 1) + ".html\">다음글</a></div>";

			html = head + html + foot;

			Util.writeFileContents("site/article/" + article.getId() + ".html", html);
		}
	}

}

class ArticleService {
	private ArticleDao articleDao;

	ArticleService() {
		articleDao = Factory.getArticleDao();
	}

//	public void detail(int id) {
//		articleDao.detail(id);
//	}

	public void delete(int id) {
		articleDao.delete(id);
	}

	public Article getArticleById(int id) {

		return articleDao.getArticleById(id);
	}

	public void modify(int id, String title, String body) {
		articleDao.modify(id, title, body);
	}

	public List<Article> getArticlesByBoardCode(String code) {
		return articleDao.getArticlesByBoardCode(code);
	}

	public List<Board> getBoards() {
		return articleDao.getBoards();
	}

	public int makeBoard(String name, String code) {
		Board oldBoard = articleDao.getBoardByCode(code);

		if (oldBoard != null) {
			return -1;
		}

		Board board = new Board(name, code);
		return articleDao.saveBoard(board);
		}

	public Board getBoard(int id) {
		return articleDao.getBoard(id);
	}

	public int write(int boardId, String title, String body) {
		Article article = new Article(boardId, title, body);
		return articleDao.save(article);
	}

	public List<Article> getArticles() {
		return articleDao.getArticles();
	}

}

// Dao
class ArticleDao {

	DBConnection dbConnection;

	ArticleDao() {

		dbConnection = Factory.getDBConnection();
	}

	public void modify(int id, String title, String body) {
		// TODO Auto-generated method stub

	}

	public void delete(int id) {
		// TODO Auto-generated method stub

	}

	public Article getArticleById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<Article> getArticlesByBoardCode(String code) {
		return dbConnection.getArticlesByBoardCode(code);
	}

	public List<Board> getBoards() {
		List<Map<String, Object>> rows = dbConnection.selectRows("SELECT * FROM board ORDER by id DESC");
		List<Board> boards = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			boards.add(new Board(row));
		}

		return boards;
	}

	public Board getBoardByCode(String code) {
		return dbConnection.getBoardByCode(code);
	}

	public int saveBoard(Board board) {
		String sql = "";
		sql += "INSERT INTO board ";
		sql += String.format("SET `name` = '%s'", board.getName());
		sql += String.format(", regDate = '%s'", board.getRegDate());
		sql += String.format(", `code` = '%s';", board.getCode());
		return dbConnection.insert(sql);
	}

	public int save(Article article) {
		String sql = "";
		sql += "INSERT INTO article ";
		sql += String.format("SET regDate = '%s'", article.getRegDate());
		sql += String.format(", title = '%s'", article.getTitle());
		sql += String.format(", `body` = '%s'", article.getBody());
		sql += String.format(", boardId = %d;", article.getBoardId());

		return dbConnection.insert(sql);
	}

	public Board getBoard(int id) {
		return dbConnection.getBoard(id);
	}

	public List<Article> getArticles() {
		List<Map<String, Object>> rows = dbConnection.selectRows("SELECT * FROM article ORDER by id DESC");
		List<Article> articles = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			articles.add(new Article(row));
		}

		return articles;

		// return db.getArticles();
	}

}

abstract class Dto {
	private int id;
	private String regDate;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getRegDate() {
		return regDate;
	}

	public void setRegDate(String regDate) {
		this.regDate = regDate;
	}

	Dto() {
		this(0);
	}

	Dto(int id) {
		this(id, Util.getNowDateStr());
	}

	Dto(int id, String regDate) {
		this.id = id;
		this.regDate = regDate;
	}
}

class Board extends Dto {
	private String name;
	private String code;

	@Override
	public String toString() {
		return String.format("%n번호 : %s%n 이름 : %s%n 코드 : %s%n", getId(), name, code);
	}

	public Board() {
	}

	public Board(String name, String code) {
		this.name = name;
		this.code = code;
	}

	public Board(Map<String, Object> row) {
		// TODO Auto-generated constructor stub
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}
}

class Article extends Dto {
	private int boardId;
	private String title;
	private String body;
	private int hit;
	private int like;

	public Article(int boardId, String title, String body) {

	}

	@Override
	public String toString() {
		return String.format("%n게시판 번호 : %s 게시 번호 : %s 게시 날짜 : %s%n제목 : %s%n내용 : %s%n%n ", boardId, getId(),
				getRegDate(), title, body);
	}

	public Article(int boardId, int memberId, String title, String body) {
		this.boardId = boardId;
		this.title = title;
		this.body = body;
	}

	public Article(Map<String, Object> row) {
		// TODO Auto-generated constructor stub
	}

	public int getBoardId() {
		return boardId;
	}

	public void setBoardId(int boardId) {
		this.boardId = boardId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public int getHit() {
		return hit;
	}

	public void setHit(int hit) {
		this.hit = hit;
	}

	public int getLike() {
		return like;
	}

	public void setLike(int like) {
		this.like = like;
	}

	public Article getArticleById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	public void delete(int id) {
		// TODO Auto-generated method stub

	}
}

// Util
class Util {
	// 현재날짜문장
	public static String getNowDateStr() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat Date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dateStr = Date.format(cal.getTime());
		return dateStr;
	}

	// 파일에 내용쓰기
	public static void writeFileContents(String filePath, int data) {
		writeFileContents(filePath, data + "");
	}

	// 첫 문자 소문자화
	public static String lcfirst(String str) {
		String newStr = "";
		newStr += str.charAt(0);
		newStr = newStr.toLowerCase();

		return newStr + str.substring(1);
	}

	// 파일이 존재하는지
	public static boolean isFileExists(String filePath) {
		File f = new File(filePath);
		if (f.isFile()) {
			return true;
		}

		return false;
	}

	// 파일내용 읽어오기
	public static String getFileContents(String filePath) {
		String rs = null;
		try {
			// 바이트 단위로 파일읽기
			FileInputStream fileStream = null; // 파일 스트림

			fileStream = new FileInputStream(filePath);// 파일 스트림 생성
			// 버퍼 선언
			byte[] readBuffer = new byte[fileStream.available()];
			while (fileStream.read(readBuffer) != -1) {
			}

			rs = new String(readBuffer);

			fileStream.close(); // 스트림 닫기
		} catch (Exception e) {
			e.getStackTrace();
		}

		return rs;
	}

	// 파일 쓰기
	public static void writeFileContents(String filePath, String contents) {
		BufferedOutputStream bs = null;
		try {
			bs = new BufferedOutputStream(new FileOutputStream(filePath));
			bs.write(contents.getBytes()); // Byte형으로만 넣을 수 있음
		} catch (Exception e) {
			e.getStackTrace();
		} finally {
			try {
				bs.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Json안에 있는 내용을 가져오기
	public static Object getObjectFromJson(String filePath, Class cls) {
		ObjectMapper om = new ObjectMapper();
		Object obj = null;
		try {
			obj = om.readValue(new File(filePath), cls);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {

		} catch (IOException e) {
			e.printStackTrace();
		}

		return obj;
	}

	public static void writeJsonFile(String filePath, Object obj) {
		ObjectMapper om = new ObjectMapper();
		try {
			om.writeValue(new File(filePath), obj);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void makeDir(String dirPath) {
		File dir = new File(dirPath);
		if (!dir.exists()) {
			dir.mkdir();
		}
	}
}
