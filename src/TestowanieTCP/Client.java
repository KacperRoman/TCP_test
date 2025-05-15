package TestowanieTCP;
import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.*;

public class Client {
    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 12345;

        if (args.length > 0) {
            hostname = args[0];
        }
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        try (
                Socket socket = new Socket(hostname, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        ) {
            System.out.println("Połączono z serwerem. Rozpoczynanie testu...");

            String line;
            while ((line = in.readLine()) != null) {
                 if (line.equals("QUESTION")) {
                    System.out.println();
                    while (!(line = in.readLine()).equals("END_QUESTION")) {
                        System.out.println(line);
                    }

                    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                    Future<String> future = scheduler.submit(() -> {
                        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
                        String input = userInput.readLine();
                        return (input != null && input.matches("[a-dA-D]")) ? input.toLowerCase() : "czas";
                    });

                    String answer = "czas";
                    try {
                        answer = future.get(30, TimeUnit.SECONDS);
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
                    System.out.println("\nTwój wynik: " + result);
                    break;
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("Nieznany host: " + hostname);
        } catch (IOException e) {
            System.err.println("Błąd połączenia z serwerem: " + e.getMessage());
        }
    }
}
