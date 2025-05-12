package TestowanieTCP;
import java.io.*;
import java.net.*;
import java.util.Scanner;

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
                Scanner scanner = new Scanner(System.in);
        ) {
            System.out.println("Połączono z serwerem. Rozpoczynanie testu...");

            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("MAX_STUDENTS_REACHED")) {
                    System.out.println("Serwer osiągnął maksymalną liczbę połączeń. Spróbuj później.");
                    return;
                } else if (line.equals("QUESTION")) {
                    // Wyświetlanie pytania
                    System.out.println();
                    while (!(line = in.readLine()).equals("END_QUESTION")) {
                        System.out.println(line);
                    }

                    // Pobieranie odpowiedzi od użytkownika
                    System.out.print("Twoja odpowiedź (a-d): ");
                    String answer = scanner.nextLine().toLowerCase();
                    while (!answer.matches("[a-d]?")) {
                        System.out.print("Nieprawidłowa odpowiedź. Wpisz a, b, c lub d: ");
                        answer = scanner.nextLine().toLowerCase();
                    }

                    out.println(answer);
                } else if (line.equals("RESULT")) {
                    // Wyświetlanie wyniku
                    String result = in.readLine();
                    System.out.println("\nTwój wynik: " + result);
                    break;
                }else if (line.equals("TIMEOUT")) {
                System.out.println("Czas na odpowiedź minął. Automatycznie zaznaczono: 'czas'");
                continue; // Przejście do kolejnego pytania
            }


        }
        } catch (UnknownHostException e) {
            System.err.println("Nieznany host: " + hostname);
        } catch (IOException e) {
            System.err.println("Błąd połączenia z serwerem: " + e.getMessage());
        }
    }
}