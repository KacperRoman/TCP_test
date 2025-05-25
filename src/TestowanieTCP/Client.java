package TestowanieTCP;
import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.*;

public class Client {
    private static String hostname = "localhost";
    private static int port = 12345;
    private static int QUESTION_TIMEOUT_SECONDS = 30;

    public static void main(String[] args) {
        if (args.length > 0) {
            hostname = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Nieprawidłowy numer portu: " + args[1] + ". Używam domyślnego portu: " + port);
            }
        }

        System.out.println("Próba połączenia z serwerem na " + hostname + ":" + port);

        try (
                Socket socket = new Socket(hostname, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Połączono z serwerem. Rozpoczynanie testu...");

            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("QUESTION")) {
                    System.out.println("\n--- NOWE PYTANIE ---");
                    StringBuilder questionBuilder = new StringBuilder();
                    while (!(line = in.readLine()).equals("END_QUESTION")) {
                        questionBuilder.append(line).append("\n");
                    }
                    System.out.println(questionBuilder.toString());

                    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                    Future<String> future = scheduler.submit(() -> {
                        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
                        String input = userInput.readLine();
                        return (input != null && input.matches("[a-dA-D]")) ? input.toLowerCase() : "czas";
                    });

                    String answer = "czas";
                    try {
                        answer = future.get(QUESTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        System.out.println("\nCzas na odpowiedź minął. Automatycznie zaznaczono: 'czas'");
                        System.out.println("Naciśnij dowolny przycisk, aby kontynuować...");
                    } catch (Exception e) {
                        System.err.println("Błąd: " + e.getMessage());
                    } finally {
                        future.cancel(true);
                        scheduler.shutdownNow();
                    }

                    out.println(answer);
                } else if (line.equals("RESULT")) {
                    String result = in.readLine();
                    System.out.println("\n--- TEST ZAKOŃCZONY ---");
                    System.out.println("Twój wynik: " + result);
                    break;
                } else {
                    System.out.println("Otrzymano nieznaną komendę od serwera: " + line);
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("Błąd: Nieznany host '" + hostname + "'. Upewnij się, że adres jest prawidłowy.");
        } catch (ConnectException e) {
            System.err.println("Błąd połączenia z serwerem: Odmowa połączenia. Upewnij się, że serwer jest uruchomiony na " + hostname + ":" + port);
        } catch (IOException e) {
            System.err.println("Błąd wejścia/wyjścia podczas komunikacji z serwerem: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Wystąpił nieoczekiwany błąd: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Klient zakończył działanie.");
    }
}