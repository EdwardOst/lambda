package job;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import com.talend.se.lambda.handler.TalendJobHandler;

public class RunJob {

    private static final String SAMPLE_INPUT_STRING = "hello earth";
    private static final String EXPECTED_OUTPUT_STRING = SAMPLE_INPUT_STRING + "\n";

	public static void main(String[] args) {
        TalendJobHandler handler = new TalendJobHandler();

        InputStream input = new ByteArrayInputStream(SAMPLE_INPUT_STRING.getBytes());
        OutputStream output = new ByteArrayOutputStream();

        Map<String, String> env = System.getenv();
		String talendJobClassName = env.get("TalendJobClassName");
        System.out.println("env[TalendJobClassName]=" + talendJobClassName);
        
        try {
			handler.handleRequest(input, output, null);
		} catch (IOException e) {
			e.printStackTrace();
		}

        String sampleOutputString = output.toString();
        System.out.println(sampleOutputString);
	}

}
