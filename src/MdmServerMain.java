
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.json.JSONObject;

public class MdmServerMain {

    private final static int PORT = 9999;
    private final static String KEY_STORE = "UranMdmServer.p12";
    private final static String STORE_PASS = "mmlabmmlab";

    private static KeyStore mKeyStore;
    private static File mPolicyFile;
    private static JSONObject mCurClientMsg;

    public static void main(String[] args) {

        loadKeyStore();

        mPolicyFile = new File("ServerPolicies.json");

        // TLS setup
        System.setProperty("javax.net.ssl.keyStore", KEY_STORE);
        System.setProperty("javax.net.ssl.keyStorePassword", STORE_PASS); // TODO hide pw
        System.setProperty("javax.net.debug", "ssl");
        SSLServerSocket servSock = null;

        try {
            SSLServerSocketFactory factory =
                    (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            servSock = (SSLServerSocket) factory.createServerSocket(PORT);
            log("Wait for the client request");

            while (true) {
                SSLSocket sock = (SSLSocket) servSock.accept();
                new Thread(new ClientHandler(sock)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (servSock != null && !servSock.isClosed()) {
                try {
                    servSock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void loadKeyStore() {
        try {
            mKeyStore = KeyStore.getInstance("PKCS12");
            FileInputStream fis = new FileInputStream(new File(KEY_STORE));
            mKeyStore.load(fis, STORE_PASS.toCharArray());

        } catch (KeyStoreException | IOException | NoSuchAlgorithmException |
                CertificateException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private SSLSocket socket;

        public ClientHandler(SSLSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream());
                String clientMsg = reader.readLine();
                log("[" + socket.getInetAddress() + "] RECV: " + clientMsg);

                String serverMsg = null;
                if (clientMsg != null && clientMsg.startsWith("Hello!")) { // Echo reply
                    serverMsg = "[ServerEcho] " + clientMsg;
                } else {
                    parseClientMsg(clientMsg);
                    // log(getTime() + "Parsing result: " + mCurClientJson.toJSONString());
                    serverMsg = getServerReply();
                }
                log("[" + socket.getInetAddress() + "] SENT: " + serverMsg);

                writer.println(serverMsg);
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String getTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yy.MM.dd HH:mm:ss");
        return dateFormat.format(new Date());
    }

    public static String getServerReply() {
        JSONObject reply = new JSONObject();
        reply.put(Payload.MAGIC, "apm_service");

        JSONObject toBeSigned = new JSONObject();
        toBeSigned.put(Payload.VERSION, "0.1");
        toBeSigned.put(Payload.TIME_STAMP, getTime());

        if (mCurClientMsg.get(Payload.CMD).equals(Payload.CLIENT_REQUEST_POLICIES)) {
            toBeSigned.put(Payload.SERVER_REPLY_POLICIES, loadServerPolicies());
        }
        reply.put(Payload.TO_BE_SIGNED, toBeSigned);

        reply.put(Payload.SERVER_SIGN, getSign(toBeSigned.toString()));

        return reply.toString();
    }

    private static JSONObject loadServerPolicies() {
        try {
            StringBuffer buf = new StringBuffer();
            for (String line : Files.readAllLines(mPolicyFile.toPath())) {
                buf.append(line);
            }
            return new JSONObject(buf.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getSign(String toBeSignedStr) {
        String ret = "getSign() failed.";
        try {
            String alias = (String) mCurClientMsg.get(Payload.SERVER_ALIAS);
            PrivateKey privKey = (PrivateKey) mKeyStore.getKey(alias, STORE_PASS.toCharArray());

            Signature signer = Signature.getInstance("RSASSA-PSS");
            signer.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                    32, 1));
            signer.initSign(privKey);
            signer.update(toBeSignedStr.getBytes());
            byte[] signBytes = signer.sign();
            ret = Base64.getEncoder().encodeToString(signBytes);

        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException
                | InvalidKeyException| SignatureException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        return ret;
    }

    public static void parseClientMsg(String clientMsg) {

        mCurClientMsg = new JSONObject(clientMsg);

        // decrypt
        String cipherText = (String) mCurClientMsg.get(Payload.RSA_ENC);
        String plainText = rsaDecrypt(cipherText);
        JSONObject obj = new JSONObject(plainText);
        mCurClientMsg.put(Payload.VERSION, obj.get(Payload.VERSION));
        mCurClientMsg.put(Payload.CMD, obj.get(Payload.CMD));
        mCurClientMsg.put(Payload.USER_ID, obj.get(Payload.USER_ID));

    }

    private static String rsaDecrypt(String cipherText) {

        String ret = "rsaDecrypt() failed.";

        try {
            byte[] cipherBytes = Base64.getDecoder().decode(cipherText);
            String alias = (String) mCurClientMsg.get(Payload.SERVER_ALIAS);
            PrivateKey privKey = (PrivateKey) mKeyStore.getKey(alias, STORE_PASS.toCharArray());

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA-256andMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privKey);
            byte[] decryptedBytes = cipher.doFinal(cipherBytes);

            ret = new String(decryptedBytes);

        } catch (BadPaddingException | UnrecoverableKeyException | KeyStoreException |
                NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        return ret;
    }

    public static void log(String msg) {
        System.out.println("[" + getTime() + "] " + msg);
    }
}
