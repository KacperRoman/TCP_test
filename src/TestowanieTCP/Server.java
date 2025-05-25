package TestowanieTCP;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private static final String CONFIG_FILE = "src/TestowanieTCP/parametry.txt";

    private static int MAX_STUDENTS;
    private static int QUESTION_TIMEOUT;
    private static int SERVER_PORT;
    private static String QUESTIONS_FILE; // Nadal używamy tego do inicjalnego ładowania pytań do DB
    private static String ANSWERS_FILE; // Nie używamy już bezpośrednio, ale zachowuję dla kontekstu
    private static String RESULTS_FILE; // Nie używamy już bezpośrednio, ale zachowuję dla kontekstu

    // Parametry bazy danych
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;

    private static List<Question> questions = new ArrayList<>();
    private static ExecutorService executorService;
    private static ServerSocket serverSocket;
    private static AtomicInteger connectedClients = new AtomicInteger(0);

    public static void main(String[] args) {
        loadConfig();
        initializeDatabase(); // Nowa metoda do inicjalizacji bazy danych
        loadQuestionsFromDatabase(); // Ładowanie pytań z bazy danych

        executorService = Executors.newFixedThreadPool(MAX_STUDENTS);

        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Serwer uruchomiony na porcie " + SERVER_PORT);
            System.out.println("Oczekiwanie na połączenia klientów... (Max: " + MAX_STUDENTS + ")");

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                if (connectedClients.get() < MAX_STUDENTS) {
                    connectedClients.incrementAndGet();
                    System.out.println("Nowy klient podłączony z " + clientSocket.getInetAddress().getHostAddress() + ". Liczba klientów: " + connectedClients.get());
                    executorService.execute(new ClientHandler(clientSocket));
                } else {
                    System.out.println("Odmowa połączenia dla " + clientSocket.getInetAddress().getHostAddress() + ". Osiągnięto limit MAX_STUDENTS.");
                    PrintWriter tempOut = new PrintWriter(clientSocket.getOutputStream(), true);
                    tempOut.println("SERVER_FULL");
                    clientSocket.close();
                }
            }
        } catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed()) {
                System.out.println("Serwer został zamknięty.");
            } else {
                System.err.println("Błąd operacji serwera: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Błąd podczas zamykania gniazda serwera: " + e.getMessage());
                }
            }
            System.out.println("Serwer zakończył działanie.");
        }
    }

    private static void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            prop.load(input);

            MAX_STUDENTS = Integer.parseInt(prop.getProperty("MAX_STUDENTS", "5"));
            QUESTION_TIMEOUT = Integer.parseInt(prop.getProperty("QUESTION_TIMEOUT", "30"));
            SERVER_PORT = Integer.parseInt(prop.getProperty("SERVER_PORT", "12345"));
            QUESTIONS_FILE = prop.getProperty("QUESTIONS_FILE", "src/TestowanieTCP/pytania.txt");
            ANSWERS_FILE = prop.getProperty("ANSWERS_FILE", "src/TestowanieTCP/answers.txt"); // Zachowuję
            RESULTS_FILE = prop.getProperty("RESULTS_FILE", "src/TestowanieTCP/results.txt"); // Zachowuję

            DB_URL = prop.getProperty("DB_URL");
            DB_USER = prop.getProperty("DB_USER");
            DB_PASSWORD = prop.getProperty("DB_PASSWORD");

            if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
                System.err.println("Błąd konfiguracji bazy danych: DB_URL, DB_USER lub DB_PASSWORD nie zostały określone w pliku parametry.txt");
                System.exit(1);
            }

            System.out.println("Konfiguracja załadowana:");
            System.out.println("  MAX_STUDENTS: " + MAX_STUDENTS);
            System.out.println("  QUESTION_TIMEOUT: " + QUESTION_TIMEOUT + "s");
            System.out.println("  SERVER_PORT: " + SERVER_PORT);
            System.out.println("  QUESTIONS_FILE: " + QUESTIONS_FILE);
            System.out.println("  DB_URL: " + DB_URL);
            System.out.println("  DB_USER: " + DB_USER);

        } catch (IOException e) {
            System.err.println("Błąd podczas wczytywania pliku konfiguracyjnego '" + CONFIG_FILE + "': " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Błąd formatu liczby w pliku konfiguracyjnym: " + e.getMessage());
            System.exit(1);
        }
    }

    // Nowa metoda do inicjalizacji bazy danych i tabel
    private static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Połączono z bazą danych MySQL.");

            // Tworzenie tabeli 'questions'
            String createQuestionsTableSQL = "CREATE TABLE IF NOT EXISTS questions ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "question_text TEXT NOT NULL,"
                    + "option_a VARCHAR(255),"
                    + "option_b VARCHAR(255),"
                    + "option_c VARCHAR(255),"
                    + "option_d VARCHAR(255),"
                    + "correct_answer CHAR(1) NOT NULL"
                    + ");";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createQuestionsTableSQL);
                System.out.println("Tabela 'questions' gotowa.");
            }

            // Tworzenie tabeli 'answers'
            String createAnswersTableSQL = "CREATE TABLE IF NOT EXISTS answers ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "student_id VARCHAR(255) NOT NULL,"
                    + "question_id INT NOT NULL,"
                    + "submitted_answer CHAR(1),"
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ");";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createAnswersTableSQL);
                System.out.println("Tabela 'answers' gotowa.");
            }

            // Tworzenie tabeli 'results'
            String createResultsTableSQL = "CREATE TABLE IF NOT EXISTS results ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "student_id VARCHAR(255) NOT NULL UNIQUE," // UNIQUE, aby jeden student miał jeden wynik
                    + "score INT NOT NULL,"
                    + "total_questions INT NOT NULL,"
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ");";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createResultsTableSQL);
                System.out.println("Tabela 'results' gotowa.");
            }

            // Wczytaj pytania z pliku tylko jeśli tabela jest pusta
            if (isQuestionsTableEmpty(conn)) {
                System.out.println("Tabela 'questions' jest pusta. Ładowanie pytań z pliku: " + QUESTIONS_FILE);
                loadQuestionsFromFileToDatabase(conn);
            } else {
                System.out.println("Tabela 'questions' zawiera już dane. Pominięto ładowanie z pliku.");
            }

        } catch (SQLException e) {
            System.err.println("Błąd inicjalizacji bazy danych: " + e.getMessage());
            System.exit(1);
        }
    }

    // Sprawdza, czy tabela 'questions' jest pusta
    private static boolean isQuestionsTableEmpty(Connection conn) throws SQLException {
        String countSQL = "SELECT COUNT(*) FROM questions;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSQL)) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        }
        return true;
    }

    // Ładuje pytania z pliku tekstowego do bazy danych
    private static void loadQuestionsFromFileToDatabase(Connection conn) {
        try (BufferedReader br = new BufferedReader(new FileReader(QUESTIONS_FILE));
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO questions (question_text, option_a, option_b, option_c, option_d, correct_answer) VALUES (?, ?, ?, ?, ?, ?)"
             )) {
            String line;
            StringBuilder questionText = new StringBuilder();
            List<String> options = new ArrayList<>(4); // Opcje a,b,c,d
            String correctAnswer = null;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.matches("^\\d+\\..*")) { // Nowe pytanie
                    if (questionText.length() > 0) { // Zapisz poprzednie, jeśli istnieje
                        if (options.size() == 4 && correctAnswer != null) {
                            pstmt.setString(1, questionText.toString().trim());
                            pstmt.setString(2, options.get(0));
                            pstmt.setString(3, options.get(1));
                            pstmt.setString(4, options.get(2));
                            pstmt.setString(5, options.get(3));
                            pstmt.setString(6, correctAnswer.toLowerCase());
                            pstmt.addBatch(); // Dodaj do batcha
                        } else {
                            System.err.println("Ostrzeżenie: Niepełne dane dla pytania: " + questionText.substring(0, Math.min(questionText.length(), 50)) + "...");
                        }
                    }
                    questionText = new StringBuilder(line);
                    options.clear();
                    correctAnswer = null;
                } else if (line.matches("^[a-d]\\).*")) { // Opcje
                    options.add(line);
                } else if (line.matches("^[a-d]$")) { // Poprawna odpowiedź
                    correctAnswer = line;
                } else { // Dalsza część pytania
                    questionText.append("\n").append(line);
                }
            }

            // Zapisz ostatnie pytanie
            if (questionText.length() > 0 && options.size() == 4 && correctAnswer != null) {
                pstmt.setString(1, questionText.toString().trim());
                pstmt.setString(2, options.get(0));
                pstmt.setString(3, options.get(1));
                pstmt.setString(4, options.get(2));
                pstmt.setString(5, options.get(3));
                pstmt.setString(6, correctAnswer.toLowerCase());
                pstmt.addBatch();
            } else if (questionText.length() > 0) {
                System.err.println("Ostrzeżenie: Niepełne dane dla ostatniego pytania: " + questionText.substring(0, Math.min(questionText.length(), 50)) + "...");
            }

            int[] insertedRows = pstmt.executeBatch(); // Wykonaj wszystkie zapytania batchowo
            System.out.println("Załadowano " + insertedRows.length + " pytań z pliku do bazy danych.");

        } catch (IOException e) {
            System.err.println("Błąd podczas wczytywania pytań z pliku '" + QUESTIONS_FILE + "': " + e.getMessage());
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("Błąd podczas zapisywania pytań do bazy danych: " + e.getMessage());
            System.exit(1);
        }
    }

    // Ładuje pytania z bazy danych do pamięci serwera
    private static void loadQuestionsFromDatabase() {
        questions.clear();
        String selectSQL = "SELECT id, question_text, option_a, option_b, option_c, option_d, correct_answer FROM questions ORDER BY id;";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String text = rs.getString("question_text");
                List<String> options = new ArrayList<>();
                options.add(rs.getString("option_a"));
                options.add(rs.getString("option_b"));
                options.add(rs.getString("option_c"));
                options.add(rs.getString("option_d"));
                String correctAnswer = rs.getString("correct_answer");
                questions.add(new Question(id, text, options, correctAnswer));
            }
            System.out.println("Załadowano " + questions.size() + " pytań z bazy danych.");
            if (questions.isEmpty()) {
                System.err.println("Błąd: Baza danych nie zawiera pytań. Sprawdź plik " + QUESTIONS_FILE + " i inicjalizację bazy.");
                System.exit(1);
            }
        } catch (SQLException e) {
            System.err.println("Błąd podczas ładowania pytań z bazy danych: " + e.getMessage());
            System.exit(1);
        }
    }

    // Metoda do zapisywania odpowiedzi w bazie danych
    private static synchronized void saveAnswer(String studentId, int questionDbId, String answer) {
        String insertSQL = "INSERT INTO answers (student_id, question_id, submitted_answer) VALUES (?, ?, ?);";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, studentId);
            pstmt.setInt(2, questionDbId);
            pstmt.setString(3, answer.toLowerCase()); // Zapisuj zawsze małą literą
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Błąd podczas zapisywania odpowiedzi dla studenta " + studentId + " na pytanie " + questionDbId + ": " + e.getMessage());
        }
    }

    // Metoda do zapisywania wyniku w bazie danych
    private static synchronized void saveResult(String studentId, int score, int totalQuestions) {
        String insertOrUpdateSQL = "INSERT INTO results (student_id, score, total_questions) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE score = ?, total_questions = ?;"; // Aktualizuj, jeśli student_id już istnieje
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(insertOrUpdateSQL)) {
            pstmt.setString(1, studentId);
            pstmt.setInt(2, score);
            pstmt.setInt(3, totalQuestions);
            pstmt.setInt(4, score); // Dla ON DUPLICATE KEY UPDATE
            pstmt.setInt(5, totalQuestions); // Dla ON DUPLICATE KEY UPDATE
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Błąd podczas zapisywania wyniku dla studenta " + studentId + ": " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private String studentId;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            ) {
                studentId = "STUDENT_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
                System.out.println("Rozpoczynanie sesji dla " + studentId + " z " + clientSocket.getInetAddress().getHostAddress());

                int score = 0;

                for (int i = 0; i < questions.size(); i++) {
                    Question q = questions.get(i);

                    System.out.println(studentId + ": Wysyłanie pytania " + (i + 1) + " (ID w DB: " + q.getId() + ")");
                    out.println("QUESTION");
                    out.println(q.getText());
                    for (String option : q.getOptions()) {
                        out.println(option);
                    }
                    out.println("END_QUESTION");

                    String answer = "czas";
                    ExecutorService answerExecutor = Executors.newSingleThreadExecutor();
                    Future<String> future = answerExecutor.submit(() -> {
                        String clientAnswer = in.readLine();
                        return (clientAnswer == null || clientAnswer.isBlank()) ? "czas" : clientAnswer;
                    });

                    try {
                        answer = future.get(QUESTION_TIMEOUT, TimeUnit.SECONDS);
                        System.out.println(studentId + ": Otrzymano odpowiedź: " + answer + " na pytanie " + (i + 1));
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        answer = "czas";
                        System.out.println(studentId + ": Czas na odpowiedź minął dla pytania " + (i + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println(studentId + ": Wątek został przerwany podczas oczekiwania na odpowiedź.");
                        answer = "czas";
                    } catch (ExecutionException e) {
                        System.err.println(studentId + ": Błąd podczas pobierania odpowiedzi: " + e.getCause().getMessage());
                        answer = "czas";
                    } finally {
                        answerExecutor.shutdownNow();
                    }

                    // Zapisywanie odpowiedzi w bazie danych
                    saveAnswer(studentId, q.getId(), answer);

                    if (q.getCorrectAnswer() != null && answer.equalsIgnoreCase(q.getCorrectAnswer())) {
                        score++;
                        System.out.println(studentId + ": Poprawna odpowiedź!");
                    } else {
                        System.out.println(studentId + ": Niepoprawna odpowiedź. Poprawna: " + q.getCorrectAnswer());
                    }
                }

                out.println("RESULT");
                out.println(score + "/" + questions.size());
                System.out.println(studentId + ": Wysyłanie końcowego wyniku: " + score + "/" + questions.size());

                // Zapisywanie wyniku w bazie danych
                saveResult(studentId, score, questions.size());

            } catch (SocketException e) {
                System.err.println(studentId + ": Połączenie zostało zresetowane lub zamknięte przez klienta: " + e.getMessage());
            } catch (IOException e) {
                System.err.println(studentId + ": Błąd wejścia/wyjścia podczas obsługi klienta: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                    connectedClients.decrementAndGet();
                    System.out.println(studentId + ": Klient rozłączony. Liczba klientów: " + connectedClients.get());
                } catch (IOException e) {
                    System.err.println(studentId + ": Błąd podczas zamykania gniazda klienta: " + e.getMessage());
                }
            }
        }
    }

    private static class Question {
        private int id; // ID pytania z bazy danych
        private String text;
        private List<String> options;
        private String correctAnswer;

        public Question(int id, String text, List<String> options, String correctAnswer) {
            this.id = id;
            this.text = text;
            this.options = options;
            this.correctAnswer = correctAnswer;
        }

        public int getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public List<String> getOptions() {
            return options;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ID: ").append(id).append("\n");
            sb.append(text).append("\n");
            for (String option : options) {
                sb.append(option).append("\n");
            }
            sb.append("Correct: ").append(correctAnswer);
            return sb.toString();
        }
    }
}