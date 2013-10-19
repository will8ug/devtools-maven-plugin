/**
 * 
 */
package net.will.maven.plugin.devtools.toolobjs;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @author Will
 * @version 2012-11-26
 */
public class ReplaceTabsWithSpacesTool {
	private Integer tabWidth;
	
	private Set<String> includes = new HashSet<String>();
	
	private Set<String> excludes = new HashSet<String>();
	
	private File operDir;
	
}
