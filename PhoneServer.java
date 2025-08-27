import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerToImageApp extends NanoHTTPD {

    private static String lastMessage = ""; // Stores the latest message
    private static AtomicInteger imageCount = new AtomicInteger(0);

    public ServerToImageApp(int port) {
        super(port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        try {
            if ("/send".equals(uri) && Method.POST.equals(method)) {
                String contentType = session.getHeaders().get("content-type");

                // Handle multipart/form-data (image/audio)
                if (contentType != null && contentType.startsWith("multipart/form-data")) {
                    Map<String, String> files = new java.util.HashMap<>();
                    session.parseBody(files);

                    for (Map.Entry<String, String> entry : files.entrySet()) {
                        String tempFile = entry.getValue();
                        String filename = "uploaded_file_" + System.currentTimeMillis();

                        if (contentType.contains("image")) {
                            filename += ".jpg";
                        } else if (contentType.contains("mpeg")) {
                            filename += ".mp3";
                        } else {
                            filename += ".dat";
                        }

                        File dest = new File(filename);
                        try (InputStream in = new FileInputStream(tempFile);
                             OutputStream out = new FileOutputStream(dest)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }

                        System.out.println("File uploaded: " + dest.getAbsolutePath());
                    }

                    return newFixedLengthResponse("File uploaded successfully.");
                }

                // Handle plain text
                if (contentType != null && contentType.equals("text/plain")) {
                    InputStream is = session.getInputStream();
                    lastMessage = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    System.out.println("Received text: " + lastMessage);
                    return newFixedLengthResponse("Text received.");
                }

                return newFixedLengthResponse("Unsupported Content-Type.");
            }

            // Handle /receive
            if ("/receive".equals(uri)) {
                String response = lastMessage.isEmpty() ? "No new messages" : lastMessage;
                lastMessage = ""; // Clear after sending
                return newFixedLengthResponse(response);
            }

            return newFixedLengthResponse("Invalid endpoint.");

        } catch (Exception e) {
            e.printStackTrace();
            return newFixedLengthResponse("Server error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int port = 8888; // Use non-privileged port on Termux
        ServerToImageApp server = new ServerToImageApp(port);

        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            System.out.println("Server started on port " + port);
            System.out.println("Endpoints: /send (POST), /receive (GET)");

            // Optional: terminal input for messages
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String input;
            while (true) {
                System.out.print("Enter message to send (type 'exit' to stop): ");
                input = reader.readLine();
                if ("exit".equalsIgnoreCase(input)) {
                    System.out.println("Server shutting down...");
                    server.stop();
                    break;
                }
                if (input != null && !input.trim().isEmpty()) {
                    lastMessage = input;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
