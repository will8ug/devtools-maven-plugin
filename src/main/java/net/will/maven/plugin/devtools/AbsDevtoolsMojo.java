/**
 * 
 */
package net.will.maven.plugin.devtools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * 
 * @author Will
 * @version 2012-11-14
 */
public abstract class AbsDevtoolsMojo extends AbstractMojo {
	@Parameter(defaultValue = "${basedir}", readonly = true, required = true)
    protected File basedir;
    
    protected static final String PS = System.getProperty("path.separator");
    
    protected static final String LS = System.getProperty("line.separator");
    
    protected static final String FS = System.getProperty("file.separator");

	/* (non-Javadoc)
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	@Override
	abstract public void execute() throws MojoExecutionException, MojoFailureException;
	
	protected void close(InputStream is) {
		try {
			if (null != is) { is.close(); }
		} catch (IOException e) {
			// NOP
		}
	}
	
	protected void close(OutputStream os) {
		try {
			if (null != os) { os.close(); }
		} catch (IOException e) {
			// NOP
		}
	}
	
	protected void close(Reader reader) {
		try {
			if (null != reader) { reader.close(); }
		} catch (IOException e) {
			// NOP
		}
	}
	
	protected void close(Writer writer) {
		try {
			if (null != writer) { writer.close(); }
		} catch (IOException e) {
			// NOP
		}
	}

}
