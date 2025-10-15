package com.arquitectura.controladores;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class ClientSmoke {

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        int port = Integer.parseInt(System.getProperty("server.port", "5050"));
        try (Socket socket = new Socket("localhost", port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write(mapper.writeValueAsString(Map.of(
                    "command", "REGISTER",
                    "payload", Map.of(
                            "usuario", "demo",
                            "email", "demo@example.com",
                            "password", "secret",
                            "fotoBase64", Base64.getEncoder().encodeToString("demo".getBytes(StandardCharsets.UTF_8)),
                            "ip", "127.0.0.1"
                    )
            )));
            writer.write('\n');
            writer.flush();
            System.out.println("Servidor => " + reader.readLine());

            writer.write(mapper.writeValueAsString(Map.of(
                    "command", "SEND_USER",
                    "payload", Map.of(
                            "emisorId", 1,
                            "receptorId", 1,
                            "tipo", "TEXTO",
                            "contenido", "Hola mundo"
                    )
            )));
            writer.write('\n');
            writer.flush();
            System.out.println("Servidor => " + reader.readLine());

            writer.write(mapper.writeValueAsString(Map.of(
                    "command", "SEND_CHANNEL",
                    "payload", Map.of(
                            "emisorId", 1,
                            "canalId", 1,
                            "tipo", "TEXTO",
                            "contenido", "Hola canal"
                    )
            )));
            writer.write('\n');
            writer.flush();
            System.out.println("Servidor => " + reader.readLine());
        }
    }
}
