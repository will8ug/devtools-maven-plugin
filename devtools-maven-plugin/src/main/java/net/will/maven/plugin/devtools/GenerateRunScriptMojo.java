package net.will.maven.plugin.devtools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.repository.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;

/**
 * <p>
 * Generate a script for running a client project.
 * </p>
 * <p>
 * Here is an example for client project:
 * <blockquote><pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;net.will&lt;/groupId&gt;
 *     &lt;artifactId&gt;devtools-maven-plugin&lt;/artifactId&gt;
 *     &lt;version&gt;0.1.2&lt;/version&gt;
 *     &lt;!-- you may specify your own script here --&gt;
 *     &lt;!--
 *     &lt;configuration&gt;
 *         &lt;inputScript&gt;exec_new.sh&lt;/inputScript&gt;
 *     &lt;/configuration&gt;
 *     --&gt;
 * &lt;/plugin&gt;
 * </pre></blockquote>
 * </p>
 *
 * @author Will
 * @version 2012-11-12
 */
@Mojo(name = "generate-run-script")
public class GenerateRunScriptMojo extends AbsDevtoolsMojo {
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject mavenProject;
	
	/**
	 * The current repository/network configuration of Maven.
	 */
	@Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
	protected RepositorySystemSession repoSession;

	@Component
	protected ProjectDependenciesResolver projectDependenciesResolver;
	
	@Parameter(defaultValue = "${project.build.finalName}", readonly = true, required = true)
    private String outputFileName;
    
	@Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File buildDirectory;
	
	/**
	 * Allows client project to specify a customized script - relative path
	 * to the root of client project. If it's not specified, a default simple
	 * script will be enabled.
	 */
	@Parameter
	private String inputScript;
	
	private static final String TEMPLATE_SCRIPT_BASH = "net/will/maven/plugin/devtools/exec_bash.sh";
	
	private static final String DEFAULT_OUTPUT_SCRIPT = "exec.sh";
	
	/*
	 * Classpath definition will be added after this line.
	 */
	private static final String CLASSPATH_PRECEDING_LINE = "# THE CLASSPATH VARIABLE CONTAINING MAVEN DEPENDENCY JAR FILES";
	
	private static final String BUILD_FILE_PRECEDING_LINE = "# THE FINAL OUTPUT FILE AFTER BUILD TO RUN";
	
	/* (non-Javadoc)
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	@Override
    public void execute() throws MojoExecutionException, MojoFailureException {
		InputStream is = null;
		
		try {
			// 1. analysis the parameter "inputScript" (stands for input script file)
			boolean customizedScript = false;
			File infile = null;
			if (null != inputScript && !"".equals(inputScript.trim())) {
				getLog().info("Customized script file (inputScript) has been specified, accepted as input: " + inputScript);
				infile = new File(inputScript);
				if (null == infile || !infile.exists()) {
					throw new GenerateRunScriptFailureException("Input script file not exists!");
				}
				
				is = new FileInputStream(infile);
				getLog().debug("inputScriptFile: " + infile);
				customizedScript = true;
			} else {
				getLog().info("Input script file (inputScript) hasn't been specified, template script will be enabled.");
				is = getClass().getClassLoader().getResourceAsStream(TEMPLATE_SCRIPT_BASH);
				getLog().debug("inputScriptFile: " + TEMPLATE_SCRIPT_BASH);
			}
			
			// 2. generate a temporary new script file
			File tmpOutputFile = new File(basedir, Thread.currentThread().getId() + new Date().getTime() + ".tmp");
			tmpOutputFile.deleteOnExit();  // register to remove it automatically by JVM
			BufferedReader br = null;
			BufferedWriter bw = null;
			try {
				br = new BufferedReader(new InputStreamReader(is));
				bw = new BufferedWriter(new FileWriter(tmpOutputFile));
				String line;
				boolean fmtCpOk = false, fmtBfOk = false;
				while ( null != (line = br.readLine()) ) {
					bw.write(line);
					bw.write(LS);
					
					if ( line.startsWith(CLASSPATH_PRECEDING_LINE) ) {
						String nextLine = br.readLine();
						if (null != nextLine && nextLine.indexOf('=') >0 ) {
							bw.write(nextLine.substring(0, nextLine.indexOf('=') + 1));
						} else {
							throw new GenerateRunScriptFailureException("Input script file format is not correct!");
						}
						bw.write(resolveDependencyJarsPath());
						bw.write(LS);
						fmtCpOk = true; // classpath variable definition OK
					} else if ( line.startsWith(BUILD_FILE_PRECEDING_LINE) ) {
						String nextLine = br.readLine();
						if (null != nextLine && nextLine.indexOf('=') >0 ) {
							bw.write(nextLine.substring(0, nextLine.indexOf('=') + 1));
						} else {
							throw new GenerateRunScriptFailureException("Input script file format is not correct!");
						}
						bw.write(resolveBuildFilePath());
						bw.write(LS);
						fmtBfOk = true; // build file variable definition OK
					}
				}
				if (!fmtCpOk || !fmtBfOk) {
					throw new Exception("Input script file format is not correct!");
				}
			} finally {
				// not catch the Exceptions here, but only close the Reader and Writer.
				// if the Writer isn't closed, renaming temp file will fail later.
				close(br);
				close(bw);
			}

			// 3. rename the temporary new script file
			File outputScriptFile = (customizedScript) ? infile : new File(basedir, DEFAULT_OUTPUT_SCRIPT);
			if (outputScriptFile.exists()) {
				// try to make sure renaming temp file succeed in Windows
				outputScriptFile.delete();
			}
			if (!tmpOutputFile.renameTo(outputScriptFile)) {
				throw new GenerateRunScriptFailureException(
						"Temp new file renaming failed! The expected outputScriptFile is: "
								+ outputScriptFile);
			}
			getLog().info("Generating script succeeded: " + outputScriptFile.getAbsolutePath());
		} catch (Exception e) {
			throw new MojoExecutionException("Unexpected Exception occurs!", e);
		} finally {
			close(is);
		}
    }
	
	private String resolveDependencyJarsPath() throws Exception {
		Set<Artifact> arts = getDependencyArtifacts(mavenProject, repoSession, projectDependenciesResolver);
		getLog().info(arts.size() + " Artifact(s) was resolved.");
		
		StringBuilder artifactJarsPath = new StringBuilder();
		for (Artifact art : arts) {
			artifactJarsPath.append(art.getFile().getAbsolutePath());
			artifactJarsPath.append(PS);
		}
		if (artifactJarsPath.length() > 1) { // delete the last path separator
			artifactJarsPath.delete(artifactJarsPath.lastIndexOf(PS), artifactJarsPath.length());
		}
		
		getLog().debug(artifactJarsPath.toString());
		return artifactJarsPath.toString();
	}
	
	/**
	 * Resolves dependencies transitively.
	 * Reference to
	 * http://labs.bsb.com/2012/10/using-aether-to-resolve-dependencies-in-a-maven-plugins.
	 * 
	 * @param project
	 * @param repoSession
	 * @param projectDependenciesResolver
	 * @return
	 * @throws MojoExecutionException
	 */
	public Set<Artifact> getDependencyArtifacts(MavenProject project,
			RepositorySystemSession repoSession,
			ProjectDependenciesResolver projectDependenciesResolver)
			throws MojoExecutionException {
		DefaultDependencyResolutionRequest dependencyResolutionRequest = new DefaultDependencyResolutionRequest(
				project, repoSession);
		DependencyResolutionResult dependencyResolutionResult;
		try {
			dependencyResolutionResult = projectDependenciesResolver
					.resolve(dependencyResolutionRequest);
		} catch (DependencyResolutionException ex) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}
		Set<Artifact> artifacts = new LinkedHashSet<Artifact>();
		if (dependencyResolutionResult.getDependencyGraph() != null
				&& !dependencyResolutionResult.getDependencyGraph()
						.getChildren().isEmpty()) {
			RepositoryUtils.toArtifacts(artifacts, dependencyResolutionResult
					.getDependencyGraph().getChildren(), Collections
					.singletonList(project.getArtifact().getId()), null);
		}
		return artifacts;
	}
	
	/**
	 * Another method which can resolve dependencies transitively, but using a
	 * legacy underlying implementation.
	 * @param projectDependenciesResolver
	 * @param scopesToResolve
	 * @param session
	 * @return
	 * @throws Exception
	 */
	public Set<Artifact> getDependencyArtifactsLegacy(
			org.apache.maven.ProjectDependenciesResolver projectDependenciesResolver,
			Collection<String> scopesToResolve, MavenSession session)
			throws Exception {
		Set<Artifact> arts = projectDependenciesResolver.resolve(mavenProject,
				scopesToResolve, session);

		return arts;
	}
	
	@Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
	private ArtifactRepository localRepository;
	
	@Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
	private List<ArtifactRepository> remoteRepositories;
	
	@Component
	protected RepositorySystem repositorySystem;
	
	/**
	 * just a sample usage of resolveArtifact().
	 * @throws MojoExecutionException
	 */
	protected void sampleUsageForResolvingSingleArtifact() throws MojoExecutionException {
		// Example 01:
		Set<Artifact> arts = mavenProject.getDependencyArtifacts();
		System.out.println("mavenProject.getDependencyArtifacts() = " + arts);
    	for (Artifact art : arts) {
    		System.out.println(art.getFile());  // NULL here
    		if (null != art.getFile()) getLog().info(art.getFile().getPath());
    		
    		ArtifactResolutionResult arr0 = resolveArtifact(art, repositorySystem, remoteRepositories, localRepository);
    		Set<Artifact> arts0 = arr0.getArtifacts();
    		System.out.println("Single Artifact [" + arts0.size() + "] = " + arts0);
    		System.out.println(art.getFile());  // right value should be shown
		}

		// Example 02:
    	Artifact artifact = repositorySystem.createArtifact("org.apache.maven", "maven-core", "3.0.4", "jar");
		ArtifactResolutionResult arr = resolveArtifact(artifact, repositorySystem, remoteRepositories, localRepository);
		Set<Artifact> arts2 = arr.getArtifacts();
		System.out.println("Single Artifact [" + arts2.size() + "] = " + arts2);
	}
	
	/**
	 * Resolves one single artifact.
	 * Reference to
	 * http://labs.bsb.com/2012/10/using-aether-to-resolve-dependencies-in-a-maven-plugins.
	 * 
	 * @param artifact
	 * @param repositorySystem
	 * @param remoteRepositories
	 * @param localRepository
	 * @return
	 * @throws MojoExecutionException
	 */
	public ArtifactResolutionResult resolveArtifact(Artifact artifact,
			RepositorySystem repositorySystem,
			List<ArtifactRepository> remoteRepositories,
			ArtifactRepository localRepository) throws MojoExecutionException {
		ArtifactResolutionRequest request = new ArtifactResolutionRequest()
				.setArtifact(artifact)
				.setRemoteRepositories(remoteRepositories)
				.setLocalRepository(localRepository);
		ArtifactResolutionResult resolutionResult = repositorySystem
				.resolve(request);
		if (resolutionResult.hasExceptions()) {
			throw new MojoExecutionException("Could not resolve artifact: "
					+ artifact, resolutionResult.getExceptions().get(0));
		}
		return resolutionResult;
	}

	private String resolveBuildFilePath() {
		StringBuilder path = new StringBuilder();
		path.append(buildDirectory).append(FS).append(outputFileName).append(".jar");
		
		getLog().debug(path.toString());
		return path.toString();
	}
}
