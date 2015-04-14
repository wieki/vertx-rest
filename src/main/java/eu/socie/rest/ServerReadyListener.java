package eu.socie.rest;

import javax.annotation.Nullable;


/**
 * 
 * @author Bram Wiekens
 *
 */
public interface ServerReadyListener {
	void finishedLoading(@Nullable String hostname, @Nullable Integer port);
}