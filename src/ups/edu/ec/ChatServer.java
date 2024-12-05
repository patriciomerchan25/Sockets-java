package ups.edu.ec;

import java.io.*;
import java.net.*;
import java.util.*;
//import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

//import com.rabbitmq.client.*;
import com.rabbitmq.client.ConnectionFactory; 
//import com.rabbitmq.client.Connection; 
import com.rabbitmq.client.Channel;


import java.sql.*;



public class ChatServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clientHandlers = new HashSet<>(); // Declarar clientHandlers
    private static final String SECRET_KEY ="1234567890123456"; //Clave de 16 bytes para AES
    private static final String QUEUE_NAME = "chat_queue";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/chatdb";
    private static final String DB_USER = "chat_user";
    private static final String DB_PASSWORD = "12345db";

    public static void main(String[] args) throws Exception {
        System.out.println("Chat server iniciando...");
        ServerSocket serverSocket = new ServerSocket(PORT); //Aceptar conexiones entrantes
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        com.rabbitmq.client.Connection rabbitConnection = factory.newConnection();
        Channel chaneel = rabbitConnection.createChannel();
        chaneel.queueDeclare(QUEUE_NAME, true, false, false, null);

        try {
            while (true) {
                new ClientHandler(serverSocket.accept(), chaneel).start();
            }
        } finally {
            serverSocket.close();
            chaneel.close();
            rabbitConnection.close();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;
        private Channel channel;

        public ClientHandler(Socket socket, Channel channel) {
            this.socket = socket;
            this.channel = channel;
        }

        public void run() {

            try (java.sql.Connection  dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                synchronized (clientHandlers){
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
                    // Publish the message to RabbitMQ
                    channel.basicPublish("", QUEUE_NAME, null, message.getBytes());

                    //Almacenar el mensaje en la base de datos
                    try {
                        storeMessage(dbConnection, clientName, message);
                    } catch (SQLException e) {
                       e.printStackTrace();
                    }
                    
                }
            } catch (IOException | SQLException e) {
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

        private void storeMessage(java.sql.Connection dbConnection, String sender, String message) throws SQLException {
            String sql = "INSERT INTO messages (sender, content, status) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
                pstmt.setString(1, sender);
                pstmt.setString(2, message);
                pstmt.setString(3, "unread");
                pstmt.executeUpdate();
            }
        }

     

        private String decrypt(String data, String key) throws Exception {
            Cipher cipher = Cipher.getInstance("AES");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedData = Base64.getDecoder().decode(data);
            return new String(cipher.doFinal(decryptedData));
        }
    }

}