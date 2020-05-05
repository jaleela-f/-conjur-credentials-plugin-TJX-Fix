package org.conjur.jenkins.conjursecrets;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;

import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.api.ConjurAPIUtils;
import org.conjur.jenkins.configuration.ConjurConfiguration;
import org.conjur.jenkins.configuration.ConjurJITJobProperty;
import org.conjur.jenkins.configuration.FolderConjurConfiguration;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.exceptions.InvalidConjurSecretException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.util.Secret;
import jenkins.security.SlaveToMasterCallable;
import okhttp3.OkHttpClient;

public class ConjurSecretCredentialsImpl extends BaseStandardCredentials implements ConjurSecretCredentials {

	@Extension
	public static class DescriptorImpl extends CredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return "Conjur Secret Credential";
		}

	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = Logger.getLogger(ConjurSecretCredentialsImpl.class.getName());
	private String variablePath; // to be used as Username

	private transient ConjurConfiguration conjurConfiguration;

	private transient Run<?, ?> context;

	static Logger getLogger() {
		return Logger.getLogger(ConjurSecretCredentialsImpl.class.getName());
	}


	@DataBoundConstructor
	public ConjurSecretCredentialsImpl(@CheckForNull CredentialsScope scope, @CheckForNull String id,
			@CheckForNull String variablePath, @CheckForNull String description) {
		super(scope, id, description);
		this.variablePath = variablePath;
	}

	protected ConjurConfiguration getConfigurationFromContext(Run<?, ?> context) {
		LOGGER.log(Level.INFO, "Getting Configuration from Context");
		ConjurConfiguration conjurConfig = GlobalConjurConfiguration.get().getConjurConfiguration();

		if (context == null) {
			return ConjurAPI.logConjurConfiguration(conjurConfig);
		}

		ConjurJITJobProperty conjurJobConfig = context.getParent().getProperty(ConjurJITJobProperty.class);

		if (conjurJobConfig != null && !conjurJobConfig.getInheritFromParent()) {
			// Taking the configuration from the Job
			return ConjurAPI.logConjurConfiguration(conjurJobConfig.getConjurConfiguration());
		}

		ConjurConfiguration inheritedConfig = inheritedConjurConfiguration(context.getParent());
		if (inheritedConfig != null) {
			return ConjurAPI.logConjurConfiguration(inheritedConfig);
		}

		return ConjurAPI.logConjurConfiguration(conjurConfig);

	}

	@SuppressWarnings("unchecked")
	private ConjurConfiguration inheritedConjurConfiguration(Item job) {
		for (ItemGroup<? extends Item> g = job
				.getParent(); g instanceof AbstractFolder; g = ((AbstractFolder<? extends Item>) g).getParent()) {
			FolderConjurConfiguration fconf = ((AbstractFolder<?>) g).getProperties()
					.get(FolderConjurConfiguration.class);
			if (!(fconf == null || fconf.getInheritFromParent())) {
				// take the folder Conjur Configuration
				return fconf.getConjurConfiguration();
			}
		}
		return null;
	}

	@Override
	public String getDisplayName() {
		return "ConjurSecret:" + this.variablePath;
	}

	static Secret secretFromString(String secretString) {
		Channel channel = Channel.current();
		Secret secretResult = null;

		if (channel == null) {
			secretResult = Secret.fromString(secretString);
		} else {
			try {
				secretResult = channel.call(new NewSecretFromString(secretString));
			} catch (IOException | InterruptedException e) {
				getLogger().log(Level.INFO, "Exception getting global configuration", e);
				e.printStackTrace();
			}
		}
		return secretResult;
	}

	public Secret getSecret() {
		String result = "";
		try {
			// Get Http Client
			OkHttpClient client = ConjurAPIUtils.getHttpClient(this.conjurConfiguration);
			// Authenticate to Conjur
			String authToken = ConjurAPI.getAuthorizationToken(client, this.conjurConfiguration, context);
			// Retrieve secret from Conjur
			String secretString = ConjurAPI.getSecret(client, this.conjurConfiguration, authToken, this.variablePath);
			result = secretString;
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "EXCEPTION: " + e.getMessage());
			throw new InvalidConjurSecretException(e.getMessage(), e);
		}

		return secretFromString(result);
	}

	public String getVariablePath() {
		return this.variablePath;
	}

	public void setConjurConfiguration(ConjurConfiguration conjurConfiguration) {
		if (conjurConfiguration != null)
			this.conjurConfiguration = conjurConfiguration;
	}

	public void setContext(Run<?, ?> context) {
		LOGGER.log(Level.INFO, "Setting context");
		this.context = context;
		setConjurConfiguration(getConfigurationFromContext(context));
	}

	@DataBoundSetter
	public void setVariablePath(String variablePath) {
		this.variablePath = variablePath;
	}

	@Override
	public String getNameTag() {
		return "/*Conjur*";
	}

}
