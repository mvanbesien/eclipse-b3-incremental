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

public class CheckedHeadless implements IApplication {

	private Object defaultStart(IApplicationContext context) throws Exception {
		return new Headless().start(context);
	}

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
				System.out.println("Path to file not found. Launches aggregation by default.");
				return defaultStart(context);
			}
			File file = new File(pathToFile);
			if (!file.exists()) {
				System.out.println("No file found at path " + pathToFile + ". Launches aggregation by default.");
				return defaultStart(context);
			}

			// Parse the input file & get information about the repo to aggregate
			URI uri = URI.createFileURI(pathToFile);
			EPackage.Registry.INSTANCE.put(AggregatorPackage.eNS_URI, AggregatorPackage.eINSTANCE);
			Resource createdResource = new AggregatorResourceImpl(uri);
			createdResource.load(Collections.EMPTY_MAP); // Not working as per missing packages ?
			Aggregation aggregation = null;
			for (Iterator<EObject> iterator = createdResource.getContents().iterator(); iterator.hasNext() && aggregation == null;) {
				EObject next = iterator.next();
				if (next instanceof Aggregation) {
					aggregation = (Aggregation) next;
				}
			}
			if (aggregation == null) {
				System.out.println("Didn't find aggregation in file. Launches aggregation by default.");
				defaultStart(context);
			}

			// Load information about the repositories that need to be aggregated

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
				System.out.println("Checking last update for repository : " + repository);
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
						if (lastModified > lastStoredInfo) {
							hasRepositoryToRefresh = true;
							properties.put(repository, "" + lastModified);
							System.out.println("\tRepository found in file with newer value. Aggregation will happen and repository stored");
						} else {
							System.out.println("\tRepository found but with older/same value.");
						}
					} else {
						System.out.println("\tRepository not found in file. Aggregation will happen and repository stored");
						hasRepositoryToRefresh = true;
						properties.put(repository, "" + lastModified);
					}
				} else {
					System.out.println("\tRepository didn't give date information. Repository set in error");
					isProcessingInError = true;
				}

			}
			// If no newer, donc relaunch the aggregation.
			if (hasRepositoryToRefresh) {
				System.out.println("There are newer repositories built. Launching aggregation");
				properties.store(new FileWriter(infoFile), null);
				System.out.println("File with history saved at "+infoFile.getPath());
				return defaultStart(context);
			}

			if (isProcessingInError) {
				System.out.println("There were unresolvable information while processing. Launching aggregation by default");
				return defaultStart(context);
			}

			System.out.println("No new repository build found. Skipping aggregation");
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Exception encountered. Launching aggregation by default.");
			return defaultStart(context);
		}
	}

	@Override
	public void stop() {
		// Does nothing
	}

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
