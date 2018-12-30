package com.talend.se.lambda.handler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Assert;
import org.junit.Test;

public class TalendJobHandlerTest {

    private static final String SAMPLE_INPUT_STRING = "{\"foo\": \"bar\"}";
    private static final String EXPECTED_OUTPUT_STRING = SAMPLE_INPUT_STRING + "\n";

    @Test
    public void testLambdaJobHandler() throws IOException {
        TalendJobHandler handler = new TalendJobHandler();

        InputStream input = new ByteArrayInputStream(SAMPLE_INPUT_STRING.getBytes());
        OutputStream output = new ByteArrayOutputStream();

        handler.handleRequest(input, output, null);

        String sampleOutputString = output.toString();
        System.out.println("sampleOutputString='" + sampleOutputString + "'");
        Assert.assertEquals(EXPECTED_OUTPUT_STRING, sampleOutputString);
    }
    
}
