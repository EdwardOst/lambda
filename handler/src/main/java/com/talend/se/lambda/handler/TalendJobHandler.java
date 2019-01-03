package com.talend.se.lambda.handler;

import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

		System.out.println("System.java.class.path = " + System.getProperty("java.class.path"));
		printClasspaths(System.out, this.getClass().getClassLoader());
		
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
		
		invokeTalendJob(talendJobClassName, talendContextList, input, output);

	}

	private void invokeTalendJob(String talendJobClassName, List<String> contextFiles, InputStream input, OutputStream output) throws Error {
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

			errorMessage = "Could not find runJob method for class '" + talendJobClassName + "'.";
			runJobMethod = talendJob.getClass().getMethod("runJob", String[].class);

			System.out.println("invoking method runJob on class " + talendJob.getClass().getName());
			runJobMethod.invoke(talendJob, new Object[] { new String[] {} });
		} catch (ClassNotFoundException e) {
			throw new Error("Class for TalendJob `" + talendJobClassName + "' not found.", e);
		} catch (NoSuchMethodException e) {
			throw new Error(errorMessage, e);
		} catch (InstantiationException e) {
			throw new Error("Error instantiating `" + talendJobClassName + "'.", e);
		} catch (IllegalAccessException e) {
			throw new Error("Access error instantiating " + talendJobClassName + ".", e);
		} catch (InvocationTargetException e) {
			throw new Error("Error invoking default constructor for " + talendJobClassName + ".", e);
		}
	}

	private Map<String, Object> readContextFiles(List<String> contextFiles, Object talendJob)
			throws Error {

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
			throw new Error("Could not find parentContextMap field in class " + talendJob.getClass().getName() + ".", e);
		} catch (IllegalAccessException e) {
			throw new Error("Access error instantiating " + talendJob.getClass().getName() + ".", e);
		}
		return parentContextMap;
	}

	
	private void loadParentContext(Map<String, Object> parentContextMap, InputStream contextStream) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(contextStream));
		Properties defaultProps = new Properties();
		// must use the java.util.Properties.load() here to escape the string correctly.
		// for some reason the value is also escaped when persisted (although this does not seem to be part of the spec
		// so an entry with key name mykey and value myparam=some_value
		//     mykey=myparam=some_value
		// unnecessarily escapes the second = sign
		//     mykey=myparam\=some_value
		try {
			defaultProps.load(contextStream);
			Set<String> keys = defaultProps.stringPropertyNames();
			for (String key : keys) {
				parentContextMap.put(key, defaultProps.getProperty(key));
			}
		} catch (IOException e) {
			throw new Error("Error reading context stream into parentContextMap", e);
		}
//		Stream<String> lines = reader.lines();
//		lines.forEach( new Consumer<String>() {
//			public void accept(String line) {
//				if (line.startsWith("#")) {
//					return;
//				}
//				String[] items = line.split("=",2);
//				if (items.length == 1) {
//					parentContextMap.put(items[0], "");
//					System.out.println("setting '" + items[0] + "' to empty string");
//				} else if (items.length == 2) {
//					parentContextMap.put(items[0], items[1]);
//					System.out.println("setting '" + items[0] + "' to '" + items[1] + "'");
//				} else {
//					System.out.println("items.length = " + items.length + " : " + items.toString());
//				}
//			}
//		});
	}

	private void printClasspaths(PrintStream stream, ClassLoader classLoader) {

		while (classLoader != null) {
			stream.println("\nclassloader " + classLoader.getClass().getName());
			printClasspath(stream, classLoader);
			classLoader = classLoader.getParent();
		}
		stream.println();
		
	}
	
	private void printClasspath(PrintStream stream, ClassLoader classLoader) {

        URL[] urls = ((URLClassLoader)classLoader).getURLs();

        StringBuilder classpath = new StringBuilder();
        for(URL url: urls){
        	classpath.append(url.getFile() + ":");
        }
        stream.println(classpath.toString());
	}
}
