package com.talend.se.lambda.handler;

import java.io.IOException;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

public class TalendJobHandler implements RequestStreamHandler {

	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {

		// Consider reading other default values for context variables.
		// These would be static configurations stored with code as property files.
		// For example, read an environment variable named TalendContext with the name
		// of the
		// context and find a file with that name in the standard Talend context path
		// location.
		// Read the context file into the parent context parameters so that there is
		// behavior similar to
		// running Talend from the command line.
		// Keep in mind that this entails the usual security risks wrt sensitive info in
		// the embedded context.
		//
		// An alternative would be storing them in s3 or dynamodb which would be more
		// secure
		// consider using the function name and version or function arn to lookup
		// default values
		// from dynamodb or s3 directory
		//
		Map<String, String> env = System.getenv();
		String talendJobClassName = env.get("TalendJobClassName");
		if (talendJobClassName == null || "".equals(talendJobClassName)) {
			throw new Error("TalendJobClassName environment variable is missing or empty.");
		}
		Object talendJob;
		Field parentContextMapField;
		Map<String, Object> parentContextMap;
		Method runJobMethod;
		String errorMessage = "Error in error handling code: errorMessage not initialized.";
		try {
			Class<?> jobClass = Class.forName(talendJobClassName);
			errorMessage = "Could not find default constructor for class '" + talendJobClassName + "'.";
			Constructor<?> ctor = jobClass.getConstructor();
			talendJob = ctor.newInstance();
			parentContextMapField = jobClass.getField("parentContextMap");
			parentContextMap = (Map<String, Object>) (parentContextMapField.get(talendJob));
			parentContextMap.put("inputStream", input);
			parentContextMap.put("outputStream", output);

			errorMessage = "Could not find runJob method for class '" + talendJobClassName + "'.";
			runJobMethod = talendJob.getClass().getMethod("runJob", String[].class);
			runJobMethod.invoke(talendJob, new Object[] { new String[] {} });
		} catch (ClassNotFoundException e) {
			throw new Error("Class for TalendJob `" + talendJobClassName + "' not found.", e);
		} catch (NoSuchMethodException e) {
			throw new Error(errorMessage, e);
		} catch (InstantiationException e) {
			throw new Error("Error instantiating `" + talendJobClassName + "'.", e);
		} catch (NoSuchFieldException e) {
			throw new Error("Could not find parentContextMap field in class " + talendJobClassName + ".", e);
		} catch (IllegalAccessException e) {
			throw new Error("Access error instantiating " + talendJobClassName + ".", e);
		} catch (InvocationTargetException e) {
			throw new Error("Error invoking default constructor for " + talendJobClassName + ".", e);
		}
	}
}
