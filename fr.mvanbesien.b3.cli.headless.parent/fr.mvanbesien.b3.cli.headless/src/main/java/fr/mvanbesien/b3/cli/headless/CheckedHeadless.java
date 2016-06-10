/**
 *    DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 *                   Version 2, December 2004
 *
 *Copyright (C) 2004 Sam Hocevar <sam@hocevar.net>
 *
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license *ocument, and changing it is allowed as long
 * as the name is changed.*
 *
 *           DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 *  TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION
 *
 *  0. You just DO WHAT THE FUCK YOU WANT TO.
 * 
 */
package fr.mvanbesien.b3.cli.headless;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.eclipse.b3.aggregator.Aggregation;
import org.eclipse.b3.aggregator.AggregatorPackage;
import org.eclipse.b3.aggregator.Contribution;
import org.eclipse.b3.aggregator.MappedRepository;
import org.eclipse.b3.aggregator.ValidationSet;
import org.eclipse.b3.aggregator.util.AggregatorResourceImpl;
import org.eclipse.b3.cli.Headless;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

/**
 * 
 * Implementation that checks if the distant repositories have been rebuilt,
 * before performing the effective aggregation.
 * 
 * @author mvanbesien <mvaawl@gmail.com>
 *
 */
public class CheckedHeadless implements IApplication {

	/**
	 * Delegates to the effective call of the Headless B3 aggregation
	 * 
	 * @param context
	 * @return
	 * @throws Exception
	 */
	private Object defaultStart(IApplicationContext context) throws Exception {
		return new Headless().start(context);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.
	 * IApplicationContext)
	 */
	@Override
	public Object start(IApplicationContext context) throws Exception {

		try {
			// Retrieve the arguments.
			String[] args = (String[]) context.getArguments().get("application.args");
			if (args == null || args.length == 0) {
				return defaultStart(context);
			}
			String pathToFile = null;
			for (int i = 0; i < args.length && pathToFile == null; i++) {
				if ("--buildModel".equals(args[i]) && args.length > i + 1) {
					pathToFile = args[i + 1];
				}
			}
			if (pathToFile == null) {
				System.out.println(
						"Path to aggregation file not found. Skipping check and launching effective aggregation.");
				return defaultStart(context);
			}
			File file = new File(pathToFile);
			if (!file.exists()) {
				System.out.println("No file found at path [" + pathToFile
						+ ". Skipping check and launching effective aggregation.");
				return defaultStart(context);
			}

			// Parse the input file & get information about the repo to
			// aggregate
			URI uri = URI.createFileURI(pathToFile);
			EPackage.Registry.INSTANCE.put(AggregatorPackage.eNS_URI, AggregatorPackage.eINSTANCE);
			Resource createdResource = new AggregatorResourceImpl(uri);
			createdResource.load(Collections.EMPTY_MAP); // Not working as per
															// missing packages
															// ?
			Aggregation aggregation = null;
			for (Iterator<EObject> iterator = createdResource.getContents().iterator(); iterator.hasNext()
					&& aggregation == null;) {
				EObject next = iterator.next();
				if (next instanceof Aggregation) {
					aggregation = (Aggregation) next;
				}
			}
			if (aggregation == null) {
				System.out.println("Could not read file at [" + pathToFile
						+ "]. Skipping check and launching effective aggregation.");
				defaultStart(context);
			}

			// Load information about the repositories that need to be
			// aggregated

			List<String> repositories = new ArrayList<String>();
			for (ValidationSet validationSet : aggregation.getValidationSets()) {
				for (Contribution contribution : validationSet.getContributions()) {
					for (MappedRepository repository : contribution.getRepositories()) {
						repositories.add(repository.getLocation());
					}
				}
			}
			createdResource.unload();

			// Load files with current information
			File infoFile = new File(pathToFile.concat(".lastUpdated"));
			Properties properties = new Properties();
			if (infoFile.exists()) {
				properties.load(new FileInputStream(infoFile));
			}

			// Check if newer...
			boolean hasRepositoryToRefresh = false;
			boolean isProcessingInError = false;
			for (String repository : repositories) {
				System.out.println("Checking last update for repository : [" + repository + "]...");
				long lastModified = -1;
				if (repository.startsWith("http")) {
					URL url = new URL(repository);
					Proxy proxy = getProxy(args);
					URLConnection connection = proxy != null ? url.openConnection(proxy) : url.openConnection();
					lastModified = connection.getLastModified();
				} else {
					File repositoryFile = new File(repository);
					if (file.exists()) {
						lastModified = repositoryFile.lastModified();
					}
				}

				if (lastModified >= 0) {
					if (properties.containsKey(repository)) {
						long lastStoredInfo = Long.parseLong("" + properties.get(repository));
						System.out.println("- The repository seems to have been rebuilt "
								+ TimeMagnifier.magnifyTimeDifference(lastStoredInfo, lastModified)
								+ (lastModified - lastStoredInfo != 0 ? " than " : " as ")
								+ "the last referenced aggregation.");
						if (lastModified > lastStoredInfo) {
							hasRepositoryToRefresh = true;
							properties.put(repository, "" + lastModified);
						}
					} else {
						System.out.println("- Repository not found in file.");
						hasRepositoryToRefresh = true;
						properties.put(repository, "" + lastModified);
					}
				} else {
					System.out.println("- Repository didn't give date information.");
					isProcessingInError = true;
				}

			}
			// If no newer, donc relaunch the aggregation.
			if (hasRepositoryToRefresh) {
				System.out.println("==> There are new or rebuilt repositories. Will perform the aggregation.");
				properties.store(new FileWriter(infoFile), null);
				System.out.println("(File with history saved at " + infoFile.getPath() + ")");
				return defaultStart(context);
			}

			if (isProcessingInError) {
				System.out.println(
						"==> There were unresolvable information while processing. Will perform the aggregation.");
				return defaultStart(context);
			}

			System.out.println("==> No new or rebuilt repository identified. Skipping aggregation");
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("==> Exception encountered. Launching aggregation by default.");
			return defaultStart(context);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	@Override
	public void stop() {
		// Does nothing
	}

	/**
	 * Technical method that creates Java Proxy in case the env variables are
	 * present
	 * 
	 * @param args
	 * @return
	 */
	private Proxy getProxy(String[] args) {
		String httpProxyHost = System.getProperty("http.proxyHost");
		String httpProxyPort = System.getProperty("http.proxyPort");
		String httpsProxyHost = System.getProperty("https.proxyHost");
		String httpsProxyPort = System.getProperty("https.proxyPort");

		if (httpProxyHost != null && httpProxyPort != null) {
			return new Proxy(Type.HTTP, new InetSocketAddress(httpProxyHost, Integer.parseInt(httpProxyPort)));
		}
		if (httpsProxyHost != null && httpsProxyPort != null) {
			return new Proxy(Type.HTTP, new InetSocketAddress(httpsProxyHost, Integer.parseInt(httpsProxyPort)));
		}

		return null;
	}

}
