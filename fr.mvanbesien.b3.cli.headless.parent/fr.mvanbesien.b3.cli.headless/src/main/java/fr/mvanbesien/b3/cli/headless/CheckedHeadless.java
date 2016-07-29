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
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
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
		System.out.println(Messages.TITLE.value());
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
			System.out.println(Messages.PARSING_MODEL.value());
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
				System.out.println(Messages.PATH_NOT_FOUND.value());
				return defaultStart(context);
			}
			File file = new File(pathToFile);
			if (!file.exists()) {
				System.out.println(Messages.FILE_NOT_FOUND.value(pathToFile));
				return defaultStart(context);
			}

			// Parse the input file & get information about the repo to
			// aggregate
			URI uri = URI.createFileURI(pathToFile);
			EPackage.Registry.INSTANCE.put(AggregatorPackage.eNS_URI, AggregatorPackage.eINSTANCE);
			Resource createdResource = new AggregatorResourceImpl(uri);
			createdResource.load(Collections.EMPTY_MAP);

			Aggregation aggregation = null;
			for (Iterator<EObject> iterator = createdResource.getContents().iterator(); iterator.hasNext() && aggregation == null;) {
				EObject next = iterator.next();
				if (next instanceof Aggregation) {
					aggregation = (Aggregation) next;
				}
			}
			if (aggregation == null) {
				System.out.println(Messages.FILE_NOT_READABLE.value(pathToFile));
				defaultStart(context);
			}

			// Load information about the repositories that need to be
			// aggregated

			List<String> repositories = new ArrayList<String>();
			for (ValidationSet validationSet : aggregation.getValidationSets()) {
				for (Contribution contribution : validationSet.getContributions()) {
					for (MappedRepository repository : contribution.getRepositories()) {
						if (repository != null) {
							System.out.println(Messages.LOCATED_REPO.value(repository.getLocation(), repository.isEnabled(), repository.isMirrorArtifacts()));
							if (repository.isEnabled()) {
								repositories.add(repository.getLocation());
							}
						}
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
				System.out.println(Messages.CHECKING_LAST_UPDATE.value(repository));
				long beginTime = System.nanoTime();
				long lastModified = -1;
				if (repository.startsWith("http")) {
					URL url = new URL(repository);
					Proxy proxy = getProxy(repository);
					URLConnection connection = proxy != null ? url.openConnection(proxy) : url.openConnection();
					lastModified = connection.getLastModified();
				} else {
					File repositoryFile = new File(repository);
					if (file.exists()) {
						lastModified = repositoryFile.lastModified();
					}
				}
				System.out.println(Messages.TIME_TO_CHECK.value((System.nanoTime() - beginTime) / 1000000));
				if (lastModified >= 0) {
					if (properties.containsKey(repository)) {
						long lastStoredInfo = Long.parseLong("" + properties.get(repository));
						System.out.println(Messages.CHECK_RESULT.value(lastModified > lastStoredInfo ? ">" : "-",
								TimeMagnifier.magnifyTimeDifference(lastStoredInfo, lastModified)));
						if (lastModified > lastStoredInfo) {
							hasRepositoryToRefresh = true;
							properties.put(repository, "" + lastModified);
						}
					} else {
						System.out.println(Messages.REPO_NOT_FOUND_IN_FILE.value());
						hasRepositoryToRefresh = true;
						properties.put(repository, "" + lastModified);
					}
				} else {
					System.out.println(Messages.REPO_NOT_DATED.value());
					isProcessingInError = true;
				}

			}
			// If no newer, donc relaunch the aggregation.
			if (hasRepositoryToRefresh) {
				System.out.println(Messages.AGGREGATION_WILL_HAPPEN.value());
				properties.store(new FileWriter(infoFile), null);
				System.out.println("Updated file with history at " + infoFile.getPath());
				return defaultStart(context);
			}

			if (isProcessingInError) {
				System.out.println(Messages.AN_ERROR_OCCURRED.value());
				return defaultStart(context);
			}

			System.out.println(Messages.AGGREGATION_SKIPPED.value());
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(Messages.ON_EXCEPTION.value());
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
	private static Proxy getProxy(String url) {
		try {
			List<Proxy> selector = ProxySelector.getDefault().select(new java.net.URI(url));
			return selector != null && selector.size() > 0 ? selector.get(0) : null;
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return null;
	}

}
