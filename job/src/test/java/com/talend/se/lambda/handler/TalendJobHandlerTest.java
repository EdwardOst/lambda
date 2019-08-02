package com.talend.se.lambda.handler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;

import org.junit.Assert;
import org.junit.Test;


public class TalendJobHandlerTest {

    private static final String SAMPLE_INPUT_STRING = "{ }";
    private static final String EXPECTED_OUTPUT_STRING = "{\"message\":\"mercury venus\"}\n";

    @Test
    public void testLambdaJobHandler() throws IOException {
        TalendJobHandler handler = new TalendJobHandler();

        InputStream input = new ByteArrayInputStream(SAMPLE_INPUT_STRING.getBytes());
        OutputStream output = new ByteArrayOutputStream();

        Map<String, String> env = System.getenv();
		String talendJobClassName = env.get("TalendJobClassName");
        System.out.println("env[TalendJobClassName]=" + talendJobClassName);

        LambdaLogger logger = new LambdaLogger() {
			Logger logger = LogManager.getLogger(TalendJobHandler.class);

			@Override
			public void log(String message) {
				logger.info(message);
			}
		};

		Context context = new Context() {

			@Override
			public String getAwsRequestId() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getLogGroupName() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getLogStreamName() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getFunctionName() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getFunctionVersion() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getInvokedFunctionArn() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public CognitoIdentity getIdentity() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public ClientContext getClientContext() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public int getRemainingTimeInMillis() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int getMemoryLimitInMB() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public LambdaLogger getLogger() {
				return logger;
			}
			
		};
		
        handler.handleRequest(input, output, context);

        String sampleOutputString = output.toString();
        System.out.println("sampleOutputString='" + sampleOutputString + "'");
        Assert.assertEquals(EXPECTED_OUTPUT_STRING, sampleOutputString);
    }
    
}
