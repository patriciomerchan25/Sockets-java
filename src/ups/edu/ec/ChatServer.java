package ups.edu.ec;

import java.io.*;
import java.net.*;
import java.util.*;
//import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class ChatServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clientHandlers = new HashSet<>(); // Declarar clientHandlers
    private static final String SECRET_KEY ="1234567890123456"; //Clave de 16 bytes para AES

    public static void main(String[] args) throws Exception {
        System.out.println("Chat server iniciando...");
        ServerSocket serverSocket = new ServerSocket(PORT);
        try {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } finally {
            serverSocket.close();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                synchronized (clientHandlers) {
                    clientHandlers.add(this);
                }

                 
                  // Lee el primer mensaje para obtener el nombre del cliente.
                String encryptedName = in.readLine();
                try {
                    clientName = decrypt(encryptedName, SECRET_KEY);
                    System.out.println(" conectado:" + clientName  );
                } catch (Exception e) {
                    e.printStackTrace();
                }

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Mensaje cifrado recibido de " + clientName);
                    synchronized (clientHandlers) {
                        for (ClientHandler handler : clientHandlers) {
                            handler.out.println(message); // Enviar el mensaje cifrado a otros clientes
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Error al manejar el cliente: " + e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                }
                synchronized (clientHandlers) {
                    clientHandlers.remove(this);
                }
                System.out.println( "desconectado: "+ clientName );
            }
        }

       /* 
        private String encrypt(String data, String key) throws Exception {
            Cipher cipher = Cipher.getInstance("AES");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedData = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encryptedData);
        }
            */

        private String decrypt(String data, String key) throws Exception {
            Cipher cipher = Cipher.getInstance("AES");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedData = Base64.getDecoder().decode(data);
            return new String(cipher.doFinal(decryptedData));
        }
    }

}