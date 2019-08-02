package com.talend.se.lambda.handler;

import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

public class TalendJobHandler implements RequestStreamHandler {

	private static final String LOG4J2_XML_PATH = "file:///opt/java/log4j.xml";

	public TalendJobHandler() {
	}

	public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {

		LambdaLogger logger;
		if (context != null) {
			logger = context.getLogger();
		} else {
			logger = new LambdaLogger() {
				Logger logger = LogManager.getLogger(TalendJobHandler.class);

				@Override
				public void log(String message) {
					logger.info(message);
				}
			};
		}
		
		logger.log("System.java.class.path = " + System.getProperty("java.class.path"));
		printClasspaths(logger::log, this.getClass().getClassLoader());

		// set log4j system property
		// see http://logging.apache.org/log4j/1.2/manual.html#defaultInit
		System.setProperty("log4j.configuration", LOG4J2_XML_PATH);

		Map<String, String> env = System.getenv();
		String talendJobClassName = env.get("TalendJobClassName");
		if (talendJobClassName == null || "".equals(talendJobClassName)) {
			throw new Error("TalendJobClassName environment variable is missing or empty.");
		}

		String talendContextFiles = env.get("TalendContextFiles");
		List<String> talendContextList = null;
		if (talendContextFiles != null) {
			talendContextList = Arrays.asList(talendContextFiles.split("(:|;)"));
		}

		try (final CloseableThreadContext.Instance ctc = CloseableThreadContext.push(
						Optional.ofNullable(System.getenv("AWS_REGION")).orElse("null") + ", "
						+ Optional.ofNullable(System.getenv("AWS_LAMBDA_FUNCTION_NAME")).orElse("null") + ", "
						+ Optional.ofNullable(System.getenv("AWS_LAMBDA_FUNCTION_VERSION")).orElse("null") + " : ")
				) {
			invokeTalendJob(talendJobClassName, talendContextList, logger::log, input, output, context);
		};
	}

	/**
	 * invokeTalendJob
	 * 
	 * Use reflection to get an instance of the job and then invoke runJob method.
	 * Bind the input and output streams received from the RequestStreamHandler
	 * interface to context variables.
	 * 
	 * @param talendJobClassName
	 *            - fully qualified class name of the job to run
	 * @param contextFiles
	 *            - a delimited list of context file path names to read
	 * @param input
	 *            - assigned to the job inputStream context variable
	 * @param output
	 *            - assigned to the job outputStream context variable
	 * @param context
	 *            - AWS Lambda context is assigned to the job awsContext context
	 *            variable
	 * @throws Error
	 */
	private void invokeTalendJob(String talendJobClassName, List<String> contextFiles, Consumer<String> logger, InputStream input,
			OutputStream output, Context context) throws Error {
		Object talendJob;
		Map<String, Object> parentContextMap;
		Method runJobMethod;
		String errorMessage = "Error in error handling code: errorMessage not initialized.";
		try {
			Class<?> jobClass = Class.forName(talendJobClassName);
			errorMessage = "Could not find default constructor for class '" + talendJobClassName + "'.";
			Constructor<?> ctor = jobClass.getConstructor();
			talendJob = ctor.newInstance();

			parentContextMap = readContextFiles(contextFiles, talendJob);
			parentContextMap.put("inputStream", input);
			parentContextMap.put("outputStream", output);
			parentContextMap.put("awsContext", context);

			errorMessage = "Could not find runJob method for class '" + talendJobClassName + "'.";
			runJobMethod = talendJob.getClass().getMethod("runJob", String[].class);

			logger.accept("invoking method runJob on class " + talendJob.getClass().getName());
			runJobMethod.invoke(talendJob, new Object[] { new String[] {} });
		} catch (ClassNotFoundException e) {
			throw new Error("Class for TalendJob '" + talendJobClassName + "' not found.", e);
		} catch (NoSuchMethodException e) {
			throw new Error(errorMessage, e);
		} catch (InstantiationException e) {
			throw new Error("Error instantiating '" + talendJobClassName + "'.", e);
		} catch (IllegalAccessException e) {
			throw new Error("Access error instantiating " + talendJobClassName + ".", e);
		} catch (InvocationTargetException e) {
			throw new Error("Error invoking default constructor for " + talendJobClassName + ".", e);
		}
	}

	private Map<String, Object> readContextFiles(List<String> contextFiles, Object talendJob) throws Error {

		Class<?> talendJobClass = talendJob.getClass();
		Map<String, Object> parentContextMap;
		parentContextMap = getParentContextMap(talendJob);

		if (contextFiles != null) {
			for (String contextFile : contextFiles) {
				URL contextUrl = talendJobClass.getResource(contextFile);
				if (contextUrl != null) {
					try {
						loadParentContext(parentContextMap, contextUrl.openStream());
					} catch (IOException e) {
						throw new Error("Error opening Talend context resource '" + contextUrl.toString() + "'");
					}
				}
			}
		}

		return parentContextMap;
	}

	private Map<String, Object> getParentContextMap(Object talendJob) throws Error {
		Map<String, Object> parentContextMap;

		try {
			Field parentContextMapField = talendJob.getClass().getField("parentContextMap");
			parentContextMap = (Map<String, Object>) (parentContextMapField.get(talendJob));
		} catch (NoSuchFieldException e) {
			throw new Error("Could not find parentContextMap field in class " + talendJob.getClass().getName() + ".",
					e);
		} catch (IllegalAccessException e) {
			throw new Error("Access error instantiating " + talendJob.getClass().getName() + ".", e);
		}
		return parentContextMap;
	}

	private void loadParentContext(Map<String, Object> parentContextMap, InputStream contextStream) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(contextStream));
		Properties defaultProps = new Properties();
		// must use the java.util.Properties.load() here to escape the string correctly.
		// for some reason the value is also escaped when persisted (although this does
		// not seem to be part of the spec)
		// so an entry with key name mykey and value with a string containing an equals
		// sign such as 'myparam=some_value'
		// mykey=myparam=some_value
		// unnecessarily escapes the second = sign
		// mykey=myparam\=some_value
		try {
			defaultProps.load(contextStream);
			Set<String> keys = defaultProps.stringPropertyNames();
			for (String key : keys) {
				parentContextMap.put(key, defaultProps.getProperty(key));
			}
		} catch (IOException e) {
			throw new Error("Error reading context stream into parentContextMap", e);
		}
	}

	private void printClasspaths(Consumer<String> logger, ClassLoader classLoader) {

		while (classLoader != null) {
			logger.accept("\nclassloader " + classLoader.getClass().getName());
			printClasspath(logger, classLoader);
			classLoader = classLoader.getParent();
		}

	}

	private void printClasspath(Consumer<String> logger, ClassLoader classLoader) {

		URL[] urls = ((URLClassLoader) classLoader).getURLs();

		StringBuilder classpath = new StringBuilder();
		for (URL url : urls) {
			classpath.append(url.getFile() + ":");
		}
		logger.accept(classpath.toString());
	}
}
