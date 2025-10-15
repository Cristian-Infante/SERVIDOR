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

    public static void main(String[] args) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        try (Socket socket = new Socket("localhost", 5050);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            Map<String, Object> registerPayload = Map.of(
                    "usuario", "demo",
                    "email", "demo@example.com",
                    "contrasenia", "demo123",
                    "fotoBase64", Base64.getEncoder().encodeToString("demo".getBytes(StandardCharsets.UTF_8)),
                    "ip", "127.0.0.1"
            );
            writer.write(mapper.writeValueAsString(Map.of("command", "REGISTER", "payload", registerPayload)));
            writer.write('\n');
            writer.flush();
            System.out.println("-> " + reader.readLine());

            Map<String, Object> messagePayload = Map.of(
                    "receptor", 1,
                    "tipo", "TEXTO",
                    "contenido", "Hola mundo"
            );
            writer.write(mapper.writeValueAsString(Map.of("command", "SEND_USER", "payload", messagePayload)));
            writer.write('\n');
            writer.flush();
            System.out.println("-> " + reader.readLine());

            Map<String, Object> audioPayload = Map.of(
                    "receptor", 1,
                    "tipo", "AUDIO",
                    "rutaArchivo", "audio/demo.mp3",
                    "mime", "audio/mpeg",
                    "duracionSeg", 10
            );
            writer.write(mapper.writeValueAsString(Map.of("command", "SEND_USER", "payload", audioPayload)));
            writer.write('\n');
            writer.flush();
            System.out.println("-> " + reader.readLine());

            Thread.sleep(500);
        }
    }
}
