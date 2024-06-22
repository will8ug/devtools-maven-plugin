/**
 * 
 */
package net.will.maven.plugin.devtools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * 
 * @author Will
 * @version 2012-12-13
 */
@Mojo(name = "replace-tabs")
public class ReplaceTabsWithSpacesMojo extends AbsDevtoolsMojo {
	@Parameter(defaultValue = "4")
	private Integer tabWidth;
	
	@Parameter(defaultValue = "${project.build.sourceDirectory}")
	private File operDir;

	/* (non-Javadoc)
	 * @see net.will.maven.plugin.devtools.AbsDevtoolsMojo#execute()
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			operateDir(operDir);
		} catch (Exception e) {
			throw new MojoFailureException("Unexpected Exception occurs!", e);
		}
	}
	
	public void operateDir(File dir) throws Exception {
		if (null == operDir || !operDir.exists() || !operDir.isDirectory()) {
			throw new MojoFailureException("Goal file[" + operDir +
					"] not exists or not a directory.");
		}
		
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				operateDir(file);
			} else {
				operateFile(file);
			}
		}
	}
	
	private void operateFile(File file) {
		StringBuilder spaces = new StringBuilder();
		for (int i = 0; i < tabWidth.intValue(); i++) {
			spaces.append(" ");
		}
	}

}
