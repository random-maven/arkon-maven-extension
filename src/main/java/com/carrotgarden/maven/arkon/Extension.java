package com.carrotgarden.maven.arkon;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

/**
 * On-demand download remote maven parent pom.xml for current project.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "arkon")
public class Extension extends AbstractMavenLifecycleParticipant {

	/** 
	 * Use default logger. 
	 */
	@Requirement
	Logger logger;

	/** 
	 * Use default transport. 
	 */
	@Requirement(hint = "http")
	Wagon wagon;

	//

	/** 
	 * Provision settings entry from: environment, properties, default. 
	 */
	static String entry(String key, String def) {
		String val = System.getenv(key);
		if (val != null) {
			return val;
		}
		return System.getProperty(key, def);
	}

	/** 
	 * Extension settings. 
	 */
	static interface settings {
		/** Name space for environment/properties override. */
		String a = "arkon_maven_extension";
		/** Project-relative folder with properties file. */
		String dir = entry(a + "_dir", ".mvn");
		/** Name of the extension configuration properties file. */
		String file = entry(a + "_file", "arkon.props");
		/** Global behaviour changer: use top parent vs given module folder. */
		boolean root = Boolean.parseBoolean(entry(a + "_root", "true"));
	}

	/** 
	 * Available extension configuration keys. 
	 */
	static interface key {
		/** Reduce logging. */
		String quiet = "quiet";
		/** Ignore "Last-Modified" header. */
		String fresh = "fresh";
		/** Ignore download errors for existing files. */
		String offline = "offline";
		/** Path list separator character. */
		String separator = "separator";
		/** Server ID in settings.xml for credentials. */
		String serverId = "serverId";
		/** Remote repository url */
		String serverURL = "serverURL";
		/** Relative resource path in repository. */
		String sourceDir = "sourceDir";
		/** Project-relative destination folder. */
		String targetDir = "targetDir";
		/** Separator-delimited download resource file names. */
		String pathList = "pathList";
	}

	/** 
	 * Default extension configuration values. 
	 */
	static interface value {
		/** Name space for environment/properties override: values. */
		String a = settings.a + "_value_";
		/** Loud by default. */
		String quiet = entry(a + key.quiet, "false");
		/** Use "Last-Modified" header. */
		String fresh = entry(a + key.fresh, "false");
		/** Strict by default. */
		String offline = entry(a + key.offline, "false");
		/** Use semicolon. */
		String separator = entry(a + key.separator, ";");
		/** Use "arkon". */
		String serverId = entry(a + key.serverId, "arkon");
		/** Use github. */
		String serverURL = entry(a + key.serverURL, "https://raw.githubusercontent.com");
		/** Use extension project organization. */
		String sourceDir = entry(a + key.sourceDir, "random-maven/arkon/master");
		/** Project-local maven configuration folder. */
		String targetDir = entry(a + key.targetDir, ".mvn");
		/** Default maven project descriptor file name. */
		String pathList = entry(a + key.pathList, "pom.xml; extensions.xml; ");
	}

	/** 
	 * Extension logger identity. 
	 */
	final String prefix = "Arkon: ";

	/** 
	 * Flag to force download. 
	 */
	volatile boolean fresh = false;

	/** 
	 * Flag to reduce logging. 
	 */
	volatile boolean quiet = false;

	/** 
	 * Flag to ignore download errors for existing files. 
	 */
	volatile boolean offline = false;

	/** Log at level "info". */
	void info(String message) {
		if (this.quiet) {
			return;
		}
		logger.info(prefix + message);
	}

	/** 
	 * Log at level "warn". 
	 */
	void warn(String message) {
		if (this.quiet) {
			return;
		}
		logger.warn(prefix + message);
	}

	/** 
	 * Log at level "error". 
	 */
	void error(String message, Throwable error) {
		logger.error(prefix + message, error);
	}

	/** 
	 * Provide configuration defaults. 
	 */
	void initialize(Properties config) throws Exception {
		config.setProperty(key.quiet, value.quiet);
		config.setProperty(key.fresh, value.fresh);
		config.setProperty(key.offline, value.offline);
		config.setProperty(key.separator, value.separator);
		config.setProperty(key.serverId, value.serverId);
		config.setProperty(key.serverURL, value.serverURL);
		config.setProperty(key.sourceDir, value.sourceDir);
		config.setProperty(key.targetDir, value.targetDir);
		config.setProperty(key.pathList, value.pathList);
	}

	/** 
	 Provide configuration overrides. 
	*/
	void override(Properties config, File root) throws Exception {
		File dir = new File(root, settings.dir);
		File file = new File(dir, settings.file);
		if (file.exists()) {
			InputStream input = new FileInputStream(file);
			config.load(input);
		}
	}

	/** 
	 * Build local resource download path. 
	 */
	File target(Properties config, File root, String path) throws Exception {
		File dir = new File(root, config.getProperty(key.targetDir));
		File file = new File(dir, path);
		return file;
	}

	/** 
	 * Ensure parent folder tree. 
	 */
	void ensureParent(File file) throws Exception {
		File parent = file.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
	}

	/** 
	 * Change execution behaviour. 
	 */
	void changeBehaviour(Properties config) {
		this.quiet = Boolean.parseBoolean(config.getProperty(key.quiet));
		this.fresh = Boolean.parseBoolean(config.getProperty(key.fresh));
		this.offline = Boolean.parseBoolean(config.getProperty(key.offline));
	}

	/** 
	 * Perform download of a file. 
	 */
	void download(Wagon wagon, String sourceFile, File targetFile) throws Exception {
		ensureParent(targetFile);
		boolean hasTarget = targetFile.exists();
		try {
			if (this.fresh) {
				wagon.get(sourceFile, targetFile);
				info("downloaded");
			} else {
				long timestamp = hasTarget ? targetFile.lastModified() : 0;
				boolean hasUpdate = wagon.getIfNewer(sourceFile, targetFile, timestamp);
				if (hasUpdate) {
					info("updated");
				} else {
					info("not modified");
				}
			}
		} catch (Throwable e) {
			if (this.offline && hasTarget) {
				warn("ignoring: " + e.getMessage());
			} else {
				throw e;
			}
		}
	}

	/** 
	 * Configured path list for download. 
	 */
	List<String> pathList(Properties config) {
		String separator = config.getProperty(key.separator);
		String entry = config.getProperty(key.pathList);
		String[] pathList = entry.split(separator);
		List<String> source = Arrays.asList(pathList);
		List<String> target = new ArrayList<>();
		for (String path : source) {
			path = path.trim();
			if ("".equals(path)) {
				continue;
			} else {
				target.add(path);
			}
		}
		return target;
	}

	/**
	 * Resolve configuration settings from file.
	 */
	Properties settings(File root) throws Exception {
		Properties config = new Properties();
		initialize(config);
		override(config, root);
		return config;
	}

	/**
	 * Configure remote server repository.
	 */
	Repository repository(Properties config) throws Exception {
		String serverId = config.getProperty(key.serverId);
		String serverURL = config.getProperty(key.serverURL);
		Repository repository = new Repository(serverId, serverURL);
		return repository;
	}

	/**
	 * Select top root vs given level module project.
	 */
	File projectDir(MavenSession session) throws Exception {
		if (settings.root) {
			return session.getRequest().getMultiModuleProjectDirectory();
		} else {
			return new File(session.getRequest().getBaseDirectory());
		}
	}

	/** 
	 * Download remote parent pom.xml. 
	 */
	void provision(MavenSession session) throws Exception {
		info("provisioning...");

		File root = projectDir(session);

		Properties config = settings(root);
		changeBehaviour(config);

		String serverURL = config.getProperty(key.serverURL);
		String sourceDir = config.getProperty(key.sourceDir);
		String sourceRoot = serverURL + "/" + sourceDir;
		info("source: " + sourceRoot);
		
		File targetRoot = target(config, root, File.separator);
		info("target: " + targetRoot);

		Repository repository = repository(config);
		wagon.connect(repository);

		List<String> pathList = pathList(config);
		for (String path : pathList) {
			info("path: " + path);
			String sourceFile = sourceDir + "/" + path; // relative
			File targetFile = target(config, root, path); // absolute
			download(wagon, sourceFile, targetFile);
		}
	}

	/**
	 * Invoke extension early in build process.
	 */
	@Override
	public void afterSessionStart(MavenSession session) throws MavenExecutionException {
		try {
			long time1 = System.currentTimeMillis();
			provision(session);
			long time2 = System.currentTimeMillis();
			long timeDiff = time2 - time1;
			info("execution time: " + timeDiff + " ms");
		} catch (Throwable e) {
			throw new MavenExecutionException(prefix, e);
		}
	}

}
