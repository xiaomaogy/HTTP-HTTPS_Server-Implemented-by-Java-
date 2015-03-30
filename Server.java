import java.io.*;
import java.net.*;

import javax.net.ssl.*;

import java.security.*;
import java.util.*;

// some sections of code dealing with requests were based on the example from cs.au.dk/~amoeller/WWW/javaweb/server.html
/* some sections of code dealing with SSL were based on the example from www.herongyang
.com/JDK/HTTPS-Server-Test-Program-HttpsHello.html */

public class Server {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: java Server --serverPort <HTTP port number> --sslServerPort <HTTPS port " +
                    "number>");
            System.exit(1);
        }
        // example usage: "java Server --serverPort 4444 --sslServerPort 5555"

        int portNumber = Integer.parseInt(args[1]);
        int httpsPortNumber = Integer.parseInt(args[3]);

        httpServer httpServer = new httpServer(portNumber);
        Thread httpServerThread = new Thread(httpServer);
        httpServerThread.start();

        httpsServer httpsServer = new httpsServer(httpsPortNumber);
        Thread httpsServerThread = new Thread(httpsServer);
        httpsServerThread.start();
    }
}

class httpServer implements Runnable {
    private int portNumber;
    private boolean connectionKeepAlive;
    private boolean connectionClose;

    public httpServer(int portNumber) {
        this.portNumber = portNumber;
    }

    public void run() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(portNumber);
            System.out.println("HTTP server accepting connections on port " + portNumber);

            Socket clientSocket = serverSocket.accept();

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream());
            PrintStream out = new PrintStream(outputStream);

            while (true) {
                connectionKeepAlive = false;
                connectionClose = false;

                if (!clientSocket.isClosed()) {
                    String request = in.readLine();

                    if (request == null) {
                        continue;
                    }

                    String[] requestArray = request.split(" "); // splits the request line into the 3 components

                    while (true) {
                        String requestMessageContinued = in.readLine();

                        if (requestMessageContinued.equals("Connection: Keep-Alive")) {
                            connectionKeepAlive = true;
                        } else if (requestMessageContinued.equals("Connection: close")) {
                            connectionClose = true;
                        }

                        if (requestMessageContinued == null || requestMessageContinued.length() == 0) {
                            break;
                        }
                    }

                    if (requestArray[2].equals("HTTP/1.1") && !connectionClose) {
                        connectionKeepAlive = true;
                    }

                    if (!(request.startsWith("GET") || request.startsWith("HEAD")) || !(request.endsWith("HTTP/1.0") ||
                            request.endsWith("HTTP/1.1")) || requestArray.length != 3) {
                        out.println(httpResponse(0, "N/A", requestArray[2], 403));
                    } else {
                        String requestedObject = requestArray[1];
                        String filePath = "";
                        String substringToReplace = "";

                        try {
                            if (requestedObject.substring(0, 13).equalsIgnoreCase("http://linux1")) {
                                substringToReplace = "http://linux1.cs.uchicago.edu:" + portNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                                // converts http://linux1.cs.uchicago.edu:4444/foo/bar.html into /foo/bar.html, for example
                            } else if (requestedObject.substring(0, 13).equalsIgnoreCase("http://linux2")) {
                                substringToReplace = "http://linux2.cs.uchicago.edu:" + portNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 13).equalsIgnoreCase("http://linux.")) {
                                substringToReplace = "http://linux.cs.uchicago.edu:" + portNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 6).equalsIgnoreCase("linux1")) {
                                substringToReplace = "linux1.cs.uchicago.edu:" + portNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 6).equalsIgnoreCase("linux2")) {
                                substringToReplace = "linux2.cs.uchicago.edu:" + portNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 6).equalsIgnoreCase("linux.")) {
                                substringToReplace = "linux.cs.uchicago.edu:" + portNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else {
                                filePath = requestedObject;
                            }
                        } catch (IndexOutOfBoundsException e) {
                            filePath = requestedObject;
                        }

                        if (!filePath.startsWith("/")) {
                            filePath = "/" + filePath;  // appends a "/" to the beginning of the file path
                            // if there isn't already one
                        }

                        String relativeFilePath = "./www" + filePath;

                        if (relativeFilePath.endsWith("/") || relativeFilePath.endsWith("redirect.defs")) {
                /* if file path is a directory or the actual redirect.defs file, return 404 not found */
                            out.println(httpResponse(0, "N/A", requestArray[2], 404));
                        } else {
                            if (requestArray[0].equals("GET")) {
                                try { // send file
                                    File requestedFile = new File(relativeFilePath);
                                    InputStream requestedFileInputStream = new FileInputStream(requestedFile);
                                    String requestedFileType = mimeType(relativeFilePath);
                                    out.println(httpResponse(requestedFile.length(), requestedFileType,
                                            requestArray[2], 200));
                                    deliverFileContents(requestedFileInputStream, outputStream); // send raw file
                                } catch (FileNotFoundException e) {
                                    findRedirects(filePath, out, requestArray);
                                }
                            } else { // a HEAD request
                                try {
                                    File requestedFile = new File(relativeFilePath);
                                    InputStream requestedFileInputStream = new FileInputStream(requestedFile);
                                    String requestedFileType = mimeType(relativeFilePath);
                                    out.println(httpResponse(requestedFile.length(), requestedFileType,
                                            requestArray[2], 200));
                                } catch (FileNotFoundException e) {
                                    findRedirects(filePath, out, requestArray);
                                }
                            }
                        }
                    }

                    out.flush();
                } else {
                    System.exit(-1); // if the user doesn't want persistent connections, either
                                     // through the "Connection: close" header or by specifying
                                     // HTTP/1.0 without the "Connection: Keep-Alive" header
                }

                if (!connectionKeepAlive) {
                    if (clientSocket != null) {
                        clientSocket.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String httpResponse(long contentLength, String contentType, String httpVersion,
                                       int statusCode) {   // contentLength is the size of the file, in bytes
        String response = "";

        switch (statusCode) {
            case 200:
                response = httpVersion + " " + statusCode + " OK\r\n" + "Date: " + new Date() + "\r\n" +
                        "Server: Simple Java Web Server\r\n" + "Content-Length: " + contentLength + "\r\n"  +
                        "Content-Type: " + contentType + "\r\n";
                break;

            case 403:
                response = httpVersion + " " + statusCode + " Forbidden\r\n"+ "Date: " + new Date() + "\r\n" +
                        "Server: Simple Java Web Server\r\n" + "Content-Length: " + contentLength + "\r\n"  +
                        "Content-Type: " + contentType + "\r\n";
                break;

            case 404:
                response = httpVersion + " " + statusCode + " Not Found\r\n" + "Date: " + new Date() + "\r\n" +
                        "Server: Simple Java Web Server\r\n" + "Content-Length: " + contentLength + "\r\n"  +
                        "Content-Type: " + contentType + "\r\n";
                break;
        }

        return response;
    }

    private static String mimeType(String filePath) {
        if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
            return "text/html";
        } else if (filePath.endsWith(".pdf")) {
            return "application/pdf";
        } else if (filePath.endsWith(".png")) {
            return "image/png";
        } else if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "text/plain"; // default mimeType
    }

    private static void deliverFileContents(InputStream requestedFileInputStream, OutputStream outputStream) {
        try {
            byte[] buffer = new byte[4096];
            while (requestedFileInputStream.available() > 0) {
                outputStream.write(buffer, 0, requestedFileInputStream.read(buffer));
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    private static void findRedirects(String filePath, PrintStream out, String[] requestArray) {
        try {
            File redirectCatalog = new File("./www/redirect.defs");
            Scanner input = new Scanner(redirectCatalog);
            FileReader fileReader = new FileReader(redirectCatalog);
            LineNumberReader lineNumberReader = new LineNumberReader(fileReader);
            int numberOfLines = 0; // number of lines in the redirect file

            while (lineNumberReader.readLine() != null) {
                numberOfLines++;
            }

            lineNumberReader.close();

            String[][] redirectLinks = new String[numberOfLines][2]; // number of rows equal to the number of lines
            // and number of columns = 2, one for the original
            // path and the other for the redirect link
            int rowNumber = 0;
            boolean fileRedirectFound = false; // has a redirect link been found for the file?

            while (input.hasNextLine()) {
                redirectLinks[rowNumber] = input.nextLine().split(" ");
                rowNumber++;
            }

            for (int i = 0; i < rowNumber; i++) {
                if (filePath.equals(redirectLinks[i][0])) { // checks if the file is present in redirect.defs
                    String response = requestArray[2] + " " + "301 Moved Permanently\r\n" + "Location: " +
                            redirectLinks[i][1] + "\r\n" + "Date: " + new Date() + "\r\n" +
                            "Server: Simple Java Web Server\r\n";

                    out.println(response);
                    fileRedirectFound = true;
                    break;
                }
            }

            if (!fileRedirectFound) {
                out.println(httpResponse(0, "N/A", requestArray[2], 404));
            }
        } catch (FileNotFoundException e) { // cannot find the redirect.defs file
            out.println(httpResponse(0, "N/A", requestArray[2], 404));
        } catch (IOException e) {
            out.println(e);
        }
    }
}

class httpsServer implements Runnable {
    private int sslPortNumber;
    SSLSocket sslClientSocket=null;
    SSLServerSocket sslServerSocket = null;
    SSLSession session;
    private String keystoreName = "server.jks";
    private char[] keystorePassword = "6aebad282d".toCharArray();
    private char[] keyPassword = "6aebad282d".toCharArray();
    private boolean connectionKeepAlive;
    private boolean connectionClose;

    public httpsServer(int sslPortNumber) {
        this.sslPortNumber = sslPortNumber;
    }

    public void run() {
        
        try {
        	System.setProperty("javax.net.ssl.keyStore", "serverRSA.jks");
		    System.setProperty("javax.net.ssl.keyStorePassword", "881222");
		    SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
			try {
				sslServerSocket = (SSLServerSocket) factory.createServerSocket(sslPortNumber);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.println("cannot create a ssl port on: "+sslPortNumber);
				e.printStackTrace();
			}
			System.out.println("SSLServer now accepting connection on port: "+sslPortNumber);
			sslClientSocket = (SSLSocket) sslServerSocket.accept();
	    	session=sslClientSocket.getSession();
		    
		    
//            KeyStore keyStore = KeyStore.getInstance("JKS");
//            keyStore.load(new FileInputStream(keystoreName), keystorePassword);
//
//            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
//            kmf.init(keyStore, keyPassword);
//
//            SSLContext sc = SSLContext.getInstance("TLS");
//            sc.init(kmf.getKeyManagers(), null, null);
//
//            SSLServerSocketFactory sssf = sc.getServerSocketFactory();
//            sslServerSocket = (SSLServerSocket)sssf.createServerSocket(sslPortNumber);
//            System.out.println("HTTPS server accepting connections on port " + sslPortNumber);
//
//            SSLSocket sslClientSocket = (SSLSocket)sslServerSocket.accept();

            BufferedReader in = new BufferedReader(new InputStreamReader(sslClientSocket.getInputStream()));
            OutputStream outputStream = new BufferedOutputStream(sslClientSocket.getOutputStream());
            PrintStream out = new PrintStream(outputStream);

            while (true) {
                connectionKeepAlive = false;
                connectionClose = false;

                if (!sslClientSocket.isClosed()) {
                    String request = in.readLine();

                    if (request == null) {
                        continue;
                    }

                    String[] requestArray = request.split(" "); // splits the request line into the 3 components

                    while (true) {
                        String requestMessageContinued = in.readLine();

                        if (requestMessageContinued.equals("Connection: Keep-Alive")) {
                            connectionKeepAlive = true;
                        } else if (requestMessageContinued.equals("Connection: close")) {
                            connectionClose = true;
                        }

                        if (requestMessageContinued == null || requestMessageContinued.length() == 0) {
                            break;
                        }
                    }

                    if (requestArray[2].equals("HTTP/1.1") && !connectionClose) {
                        connectionKeepAlive = true;
                    }

                    if (!(request.startsWith("GET") || request.startsWith("HEAD")) || !(request.endsWith("HTTP/1.0") ||
                            request.endsWith("HTTP/1.1")) || requestArray.length != 3) {
                        out.println(httpResponse(0, "N/A", requestArray[2], 403));
                    } else {
                        String requestedObject = requestArray[1];
                        String filePath = "";
                        String substringToReplace = "";

                        try {
                            if (requestedObject.substring(0, 14).equalsIgnoreCase("https://linux1")) {
                                substringToReplace = "https://linux1.cs.uchicago.edu:" + sslPortNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                                // converts http://linux1.cs.uchicago.edu:4444/foo/bar.html into /foo/bar.html, for example
                            } else if (requestedObject.substring(0, 14).equalsIgnoreCase("https://linux2")) {
                                substringToReplace = "https://linux2.cs.uchicago.edu:" + sslPortNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 14).equalsIgnoreCase("https://linux.")) {
                                substringToReplace = "https://linux.cs.uchicago.edu:" + sslPortNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 6).equalsIgnoreCase("linux1")) {
                                substringToReplace = "linux1.cs.uchicago.edu:" + sslPortNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 6).equalsIgnoreCase("linux2")) {
                                substringToReplace = "linux2.cs.uchicago.edu:" + sslPortNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else if (requestedObject.substring(0, 6).equalsIgnoreCase("linux.")) {
                                substringToReplace = "linux.cs.uchicago.edu:" + sslPortNumber;
                                filePath = requestedObject.replace(substringToReplace, "");
                            } else {
                                filePath = requestedObject;
                            }
                        } catch (IndexOutOfBoundsException e) {
                            filePath = requestedObject;
                        }

                        if (!filePath.startsWith("/")) {
                            filePath = "/" + filePath;  // appends a "/" to the beginning of the file path
                            // if there isn't already one
                        }

                        String relativeFilePath = "./www" + filePath;

                        if (relativeFilePath.endsWith("/") || relativeFilePath.endsWith("redirect.defs")) {
                /* if file path is a directory or the actual redirect.defs file, return 404 not found */
                            out.println(httpResponse(0, "N/A", requestArray[2], 404));
                        } else {
                            if (requestArray[0].equals("GET")) {
                                try { // send file
                                    File requestedFile = new File(relativeFilePath);
                                    InputStream requestedFileInputStream = new FileInputStream(requestedFile);
                                    String requestedFileType = mimeType(relativeFilePath);
                                    out.println(httpResponse(requestedFile.length(), requestedFileType,
                                            requestArray[2], 200));
                                    deliverFileContents(requestedFileInputStream, outputStream); // send raw file
                                } catch (FileNotFoundException e) {
                                    findRedirects(filePath, out, requestArray);
                                }
                            } else { // a HEAD request
                                try {
                                    File requestedFile = new File(relativeFilePath);
                                    InputStream requestedFileInputStream = new FileInputStream(requestedFile);
                                    String requestedFileType = mimeType(relativeFilePath);
                                    out.println(httpResponse(requestedFile.length(), requestedFileType,
                                            requestArray[2], 200));
                                } catch (FileNotFoundException e) {
                                    findRedirects(filePath, out, requestArray);
                                }
                            }
                        }
                    }

                    out.flush();
                } else {
                    System.exit(-1); // if the user doesn't want persistent connections, either
                    // through the "Connection: close" header or by specifying
                    // HTTP/1.0 without the "Connection: Keep-Alive" header
                }

                if (!connectionKeepAlive) {
                    if (sslClientSocket != null) {
                       sslClientSocket.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String httpResponse(long contentLength, String contentType, String httpVersion,
                                       int statusCode) {   // contentLength is the size of the file, in bytes
        String response = "";

        switch (statusCode) {
            case 200:
                response = httpVersion + " " + statusCode + " OK\r\n" + "Date: " + new Date() + "\r\n" +
                        "Server: Simple Java Web Server\r\n" + "Content-Length: " + contentLength + "\r\n"  +
                        "Content-Type: " + contentType + "\r\n";
                break;

            case 403:
                response = httpVersion + " " + statusCode + " Forbidden\r\n"+ "Date: " + new Date() + "\r\n" +
                        "Server: Simple Java Web Server\r\n" + "Content-Length: " + contentLength + "\r\n"  +
                        "Content-Type: " + contentType + "\r\n";
                break;

            case 404:
                response = httpVersion + " " + statusCode + " Not Found\r\n" + "Date: " + new Date() + "\r\n" +
                        "Server: Simple Java Web Server\r\n" + "Content-Length: " + contentLength + "\r\n"  +
                        "Content-Type: " + contentType + "\r\n";
                break;
        }

        return response;
    }

    private static String mimeType(String filePath) {
        if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
            return "text/html";
        } else if (filePath.endsWith(".pdf")) {
            return "application/pdf";
        } else if (filePath.endsWith(".png")) {
            return "image/png";
        } else if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "text/plain"; // default mimeType
    }

    private static void deliverFileContents(InputStream requestedFileInputStream, OutputStream outputStream) {
        try {
            byte[] buffer = new byte[4096];
            while (requestedFileInputStream.available() > 0) {
                outputStream.write(buffer, 0, requestedFileInputStream.read(buffer));
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    private static void findRedirects(String filePath, PrintStream out, String[] requestArray) {
        try {
            File redirectCatalog = new File("./www/redirect.defs");
            Scanner input = new Scanner(redirectCatalog);
            FileReader fileReader = new FileReader(redirectCatalog);
            LineNumberReader lineNumberReader = new LineNumberReader(fileReader);
            int numberOfLines = 0; // number of lines in the redirect file

            while (lineNumberReader.readLine() != null) {
                numberOfLines++;
            }

            lineNumberReader.close();

            String[][] redirectLinks = new String[numberOfLines][2]; // number of rows equal to the number of lines
            // and number of columns = 2, one for the original
            // path and the other for the redirect link
            int rowNumber = 0;
            boolean fileRedirectFound = false; // has a redirect link been found for the file?

            while (input.hasNextLine()) {
                redirectLinks[rowNumber] = input.nextLine().split(" ");
                rowNumber++;
            }

            for (int i = 0; i < rowNumber; i++) {
                if (filePath.equals(redirectLinks[i][0])) { // checks if the file is present in redirect.defs
                    String response = requestArray[2] + " " + "301 Moved Permanently\r\n" + "Location: " +
                            redirectLinks[i][1] + "\r\n" + "Date: " + new Date() + "\r\n" +
                            "Server: Simple Java Web Server\r\n";

                    out.println(response);
                    fileRedirectFound = true;
                    break;
                }
            }

            if (!fileRedirectFound) {
                out.println(httpResponse(0, "N/A", requestArray[2], 404));
            }
        } catch (FileNotFoundException e) { // cannot find the redirect.defs file
            out.println(httpResponse(0, "N/A", requestArray[2], 404));
        } catch (IOException e) {
            out.println(e);
        }
    }
}