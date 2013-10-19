/**
 * 
 */
package net.will.maven.plugin.devtools;

import org.apache.maven.plugin.MojoFailureException;

/**
 * Just a self-defined Exception.
 * 
 * @author Will
 * @version 2012-11-14
 */
public class GenerateRunScriptFailureException extends MojoFailureException {
	private static final long serialVersionUID = 7682488389465270166L;

	public GenerateRunScriptFailureException(String message) {
		super(message);
	}

}
