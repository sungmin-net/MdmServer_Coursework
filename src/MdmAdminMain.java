import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.json.JSONObject;

// Json : https://code.google.com/archive/p/json-simple/downloads

public class MdmAdminMain {

    public static void main(String[] args) throws IOException {

        JSONObject serverPolicies = new JSONObject();

        // TODO add restriction policy if needed
        serverPolicies.put(Payload.POLICY_ALLOW_CAMERA, true);

        System.out.println(serverPolicies);

        File file = new File("ServerPolicies.json");
        Files.write(file.toPath(), serverPolicies.toString(4 /* indent */ ).getBytes());

    }

}
