package TestowanieTCP;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final String CONFIG_FILE = "src/TestowanieTCP/parametry.txt";
    private static int MAX_STUDENTS;
    private static int QUESTION_TIMEOUT;
    private static int SERVER_PORT;
    private static String QUESTIONS_FILE;
    private static String ANSWERS_FILE;
    private static String RESULTS_FILE;

    private static List<Question> questions = new ArrayList<>();
    private static ExecutorService executorService;
    private static ServerSocket serverSocket;
    private static int connectedClients = 0;

    public static void main(String[] args) {
        loadConfig();
        loadQuestions();

        executorService = Executors.newFixedThreadPool(MAX_STUDENTS);

        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Serwer uruchomiony na porcie " + SERVER_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                if (connectedClients < MAX_STUDENTS) {
                    connectedClients++;
                    System.out.println("Nowy klient podłączony. Liczba klientów: " + connectedClients);
                    executorService.execute(new ClientHandler(clientSocket));
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdown();
        }
    }

    private static void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            prop.load(input);

            MAX_STUDENTS = Integer.parseInt(prop.getProperty("MAX_STUDENTS"));
            QUESTION_TIMEOUT = Integer.parseInt(prop.getProperty("QUESTION_TIMEOUT"));
            SERVER_PORT = Integer.parseInt(prop.getProperty("SERVER_PORT"));
            QUESTIONS_FILE = prop.getProperty("QUESTIONS_FILE");
            ANSWERS_FILE = prop.getProperty("ANSWERS_FILE");
            RESULTS_FILE = prop.getProperty("RESULTS_FILE");

        } catch (IOException e) {
            System.err.println("Błąd podczas wczytywania pliku konfiguracyjnego");
            System.exit(1);
        }
    }

    private static void loadQuestions() {
        try (BufferedReader br = new BufferedReader(new FileReader(QUESTIONS_FILE))) {
            String line;
            StringBuilder questionText = new StringBuilder();
            List<String> options = new ArrayList<>();
            String correctAnswer = null;

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                if (line.matches("^\\d+\\..*")) {
                    if (questionText.length() > 0) {
                        questions.add(new Question(questionText.toString(), new ArrayList<>(options), correctAnswer));
                        options.clear();
                    }
                    questionText = new StringBuilder(line);
                } else if (line.matches("^[a-d]\\).*")) {
                    options.add(line);
                } else if (line.matches("^[a-d]$")) {
                    correctAnswer = line;
                }
            }

            if (questionText.length() > 0) {
                questions.add(new Question(questionText.toString(), options, correctAnswer));
            }

            System.out.println("Załadowano " + questions.size() + " pytań");
        } catch (IOException e) {
            System.err.println("Błąd podczas wczytywania pytań");
            System.exit(1);
        }
    }

    private static synchronized void saveAnswer(String studentId, int questionNum, String answer) {
        try (PrintWriter out = new PrintWriter(new FileWriter(ANSWERS_FILE, true))) {
            out.println(studentId + ";" + questionNum + ";" + answer);
        } catch (IOException e) {
            System.err.println("Błąd podczas zapisywania odpowiedzi");
        }
    }

    private static synchronized void saveResult(String studentId, int score) {
        try (PrintWriter out = new PrintWriter(new FileWriter(RESULTS_FILE, true))) {
            out.println(studentId + ";" + score + "/" + questions.size());
        } catch (IOException e) {
            System.err.println("Błąd podczas zapisywania wyniku");
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
                // Generowanie unikalnego ID studenta
                studentId = "STUDENT_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();

                int score = 0;

                for (int i = 0; i < questions.size(); i++) {
                    Question q = questions.get(i);

                    // Wysyłanie pytania
                    out.println("QUESTION");
                    out.println(q.getText());
                    for (String option : q.getOptions()) {
                        out.println(option);
                    }
                    out.println("END_QUESTION");

                    // Odbieranie odpowiedzi z limitem czasu
                    String answer = "";
                    ExecutorService answerExecutor = Executors.newSingleThreadExecutor();
                    Future<String> future = answerExecutor.submit(() -> in.readLine());

                    try {
                        answer = future.get(QUESTION_TIMEOUT, TimeUnit.SECONDS);
                        if (answer == null || answer.isBlank()) {
                            answer = "czas";
                            out.println("TIMEOUT");
                        }
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        answer = "czas"; // Pusta odpowiedź w przypadku przekroczenia czasu
                        out.println("TIMEOUT");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        answerExecutor.shutdownNow();
                    }

                    // Zapisywanie odpowiedzi
                    saveAnswer(studentId, i + 1, answer);

                    // Sprawdzanie odpowiedzi
                    if (answer.equalsIgnoreCase(q.getCorrectAnswer())) {
                        score++;
                    }
                }

                // Wysyłanie wyniku
                out.println("RESULT");
                out.println(score + "/" + questions.size());

                // Zapisywanie wyniku
                saveResult(studentId, score);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                    connectedClients--;
                    System.out.println("Klient rozłączony. Liczba klientów: " + connectedClients);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class Question {
        private String text;
        private List<String> options;
        private String correctAnswer;

        public Question(String text, List<String> options, String correctAnswer) {
            this.text = text;
            this.options = options;
            this.correctAnswer = correctAnswer;
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
    }
}