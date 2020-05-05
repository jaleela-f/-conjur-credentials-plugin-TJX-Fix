package org.conjur.jenkins.api;

import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import org.conjur.jenkins.configuration.ConjurConfiguration;

import hudson.remoting.Channel;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jenkins.security.SlaveToMasterCallable;
import okhttp3.OkHttpClient;

public class ConjurAPIUtils {
	
	static Logger getLogger() {
		return Logger.getLogger(ConjurAPIUtils.class.getName());
	}
	
	public static OkHttpClient getHttpClient(ConjurConfiguration configuration) {

		OkHttpClient client = null;

		Channel channel = Channel.current();

		CertificateCredentials certificate = null;

		if (channel == null) {
			certificate = CredentialsMatchers.firstOrNull(
					CredentialsProvider.lookupCredentials(CertificateCredentials.class, Jenkins.get(), ACL.SYSTEM,
							Collections.<DomainRequirement>emptyList()),
					CredentialsMatchers.withId(configuration.getCertificateCredentialID()));
		} else {
			try {
				certificate = channel.call(new ConjurAPIUtils.NewCertificateCredentials(configuration));
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				getLogger().log(Level.INFO, "Exception getting global configuration", e);
				e.printStackTrace();
			}
		}
		
		if (certificate != null) {
			try {

				KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(certificate.getKeyStore(), certificate.getPassword().getPlainText().toCharArray());
				KeyManager[] kms = kmf.getKeyManagers();

				KeyStore trustStore = KeyStore.getInstance("JKS");
				trustStore.load(null, null);
				Enumeration<String> e = certificate.getKeyStore().aliases();
				while (e.hasMoreElements()) {
					String alias = e.nextElement();
					trustStore.setCertificateEntry(alias, certificate.getKeyStore().getCertificate(alias));
				}
				TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(trustStore);
				TrustManager[] tms = tmf.getTrustManagers();

				SSLContext sslContext = null;
				sslContext = SSLContext.getInstance("TLSv1.2");
				sslContext.init(kms, tms, new SecureRandom());

				client = new OkHttpClient.Builder()
						.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) tms[0]).build();
			} catch (Exception e) {
				throw new IllegalArgumentException("Error configuring server certificates.", e);
			}
		} else {
			client = new OkHttpClient.Builder().build();
		}

		return client;
	}

	static class NewCertificateCredentials extends SlaveToMasterCallable<CertificateCredentials, IOException> {
		/**
		 * Standardize serialization.
		 */
		private static final long serialVersionUID = 1L;

		ConjurConfiguration configuration;
		// Run<?, ?> context;

		public NewCertificateCredentials(ConjurConfiguration configuration) {
			super();
			this.configuration = configuration;
			// this.context = context;
		}

		/**
		 * {@inheritDoc}
		 */
		public CertificateCredentials call() throws IOException {
			CertificateCredentials certificate = CredentialsMatchers.firstOrNull(
					CredentialsProvider.lookupCredentials(CertificateCredentials.class, Jenkins.get(), ACL.SYSTEM,
							Collections.<DomainRequirement>emptyList()),
					CredentialsMatchers.withId(this.configuration.getCertificateCredentialID()));

			return certificate;
		}
	}

	static class NewAvailableCredentials extends SlaveToMasterCallable<List<UsernamePasswordCredentials>, IOException> {
		/**
		 * Standardize serialization.
		 */
		private static final long serialVersionUID = 1L;

		// Run<?, ?> context;

		// public NewAvailableCredentials(Run<?, ?> context) {
		// super();
		// this.context = context;
		// }

		/**
		 * {@inheritDoc}
		 */
		public List<UsernamePasswordCredentials> call() throws IOException {

			List<UsernamePasswordCredentials> availableCredentials = CredentialsProvider.lookupCredentials(
					UsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM,
					Collections.<DomainRequirement>emptyList());

			// if (context != null) {
			// availableCredentials.addAll(CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class,
			// context.getParent(), ACL.SYSTEM,
			// Collections.<DomainRequirement>emptyList()));
			// }

			return availableCredentials;
		}
	}


}