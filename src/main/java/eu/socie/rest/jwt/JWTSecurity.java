package eu.socie.rest.jwt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import com.hazelcast.util.Base64;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.ReadOnlyJWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Provide JWT token security and validation
 * 
 * @author Bram Wiekens
 *
 */
public class JWTSecurity extends Verticle {

	public final static String CONFIG_KEYSTORE_PATH = "keystore_path";
	public final static String CONFIG_KEYSTORE_PASSWORD = "keystore_password";
	public final static String CONFIG_KEYSTORE_TYPE = "keystore_type";
	public final static String CONFIG_KEYSTORE_ALIAS = "keystore_alias";

	public final static String DEFAULT_TYPE = "jceks";

	public final static String EVENT_VALIDATE_JWT_TOKEN = "jwt.validate.token";
	public final static String EVENT_VALIDATE_JWT_ROLE = "jwt.validate.role";
	public final static String EVENT_CREATE_JWT_TOKEN = "jwt.create.token";

	public final static String MSG_SUCCESS_KEYSTORE_LOADED = "Keystore has been successfully loaded";

	public final static JWSAlgorithm JWT_ALGORITHM = JWSAlgorithm.RS384;

	public final static int ERROR_PARSE_JWT_CODE = 2001;
	public final static int ERROR_KEYSTORE_READ_CODE = 2002;
	public final static int ERROR_JWT_SUBJECT_CODE = 2003;
	public final static int ERROR_JWT_SIGNING_CODE = 2004;
	public final static int ERROR_JWT_EXPIRED_CODE = 2005;
	public final static int ERROR_JWT_UNAUTHORIZED_CODE = 2006;
	public final static int ERROR_JWT_INVALID = 2007;

	public final static String ERROR_PARSE_JWT_MSG = "JWT token could not be parsed";
	public final static String ERROR_KEYSTORE_READ_MSG = "KeyStore could not be read due to %s";
	public final static String ERROR_JWT_SUBJECT_MSG = "The JWT token needs a subject to be created";
	public final static String ERROR_JWT_SIGNING_MSG = "The JWT token could not been signed due to %s";
	public final static String ERROR_JWT_EXPIRED_MSG = "JWT token has been expired";
	public final static String ERROR_JWT_UNAUTHORIZED_MSG = "Subject %s doest not have role %s";
	public final static String ERROR_JWT_INVALID_MSG = "The JWT token is invalid and could not be verified";

	private KeyStore keyStore;
	private Logger log;
	private JWSVerifier verifier;
	private RSAPublicKey publicKey;

	@Override
	public void start() {

		log = container.logger();

		JsonObject modConfig = getContainer().config();

		String keyStorePath = modConfig.getString(CONFIG_KEYSTORE_PATH);
		String keyStorePassword = modConfig.getString(CONFIG_KEYSTORE_PASSWORD);
		String keyStoreType = modConfig.getString(CONFIG_KEYSTORE_TYPE,
				DEFAULT_TYPE);
		String keyStoreAlias = modConfig.getString(CONFIG_KEYSTORE_ALIAS);

		vertx.fileSystem().readFile(
				keyStorePath,
				(b) -> handleKeyStore(b, keyStorePassword, keyStoreType,
						keyStoreAlias));
	}

	/**
	 * Try to read the key store file from the given path. If reading succeeds,
	 * listener will be bound to handle JWT requests.
	 * 
	 * @param fileReadResult
	 *            is the result of reading the key store file
	 * @param keyStorePassword
	 *            is the password needed to read keys from the key store
	 * @param keyStoreType
	 *            is the type of key store
	 * @param keyStoreAlias
	 *            is the alias to be used to retrieve the keys from the key
	 *            store
	 */
	private void handleKeyStore(AsyncResult<Buffer> fileReadResult,
			String keyStorePassword, String keyStoreType, String keyStoreAlias) {
		if (fileReadResult.succeeded()) {
			Buffer b = fileReadResult.result();

			InputStream in = null;
			try {
				keyStore = KeyStore.getInstance(keyStoreType);

				in = new ByteArrayInputStream(b.getBytes());
				keyStore.load(
						in,
						keyStorePassword != null ? keyStorePassword
								.toCharArray() : null);

				log.info(MSG_SUCCESS_KEYSTORE_LOADED);

				publicKey = (RSAPublicKey) keyStore.getCertificate(
						keyStoreAlias).getPublicKey();

				verifier = new RSASSAVerifier(publicKey);

				vertx.eventBus().registerHandler(EVENT_VALIDATE_JWT_TOKEN,
						(Message<String> m) -> validateJWTToken(m));

				vertx.eventBus().registerHandler(
						EVENT_CREATE_JWT_TOKEN,
						(Message<JsonObject> m) -> createJWTToken(m,
								keyStoreAlias, keyStorePassword));

				vertx.eventBus().registerHandler(EVENT_VALIDATE_JWT_ROLE,
						(Message<JsonObject> m) -> validateJWTClaims(m));

			} catch (NoSuchAlgorithmException | CertificateException
					| IOException | KeyStoreException e) {
				log.error(String.format(ERROR_KEYSTORE_READ_MSG, e.getMessage()));
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException ignore) {
					}
				}
			}
		} else {
			log.error(String.format(ERROR_KEYSTORE_READ_MSG, fileReadResult
					.cause().getMessage()));
		}

	}

	public static JsonObject requiresRole(String jwtToken, String role) {
		JsonObject jwtClaims = new JsonObject();
		jwtClaims.putString("jwt_token", jwtToken);
		jwtClaims.putString("role", role);

		return jwtClaims;
	}

	private void validateJWTClaims(Message<JsonObject> jwtMessage) {
		JsonObject jwtObj = jwtMessage.body();
		String jwtToken = jwtObj.getString("jwt_token");
		String role = jwtObj.getString("role");
		boolean hasClaims = false;

		SignedJWT jwt;
		try {
			jwt = SignedJWT.parse(jwtToken);

			if (jwt.verify(verifier)) {
				ReadOnlyJWTClaimsSet claimsSet = jwt.getJWTClaimsSet();

				Date expires = claimsSet.getExpirationTime();
				long now = System.currentTimeMillis();

				if (expires != null &&  now > expires.getTime()) {
					jwtMessage.fail(ERROR_JWT_EXPIRED_CODE,
							ERROR_JWT_EXPIRED_MSG);
					return;
				}

				List<String> roles = claimsSet.getStringListClaim("roles");
				hasClaims = roles.contains(role);

				if (hasClaims) {
					jwtMessage.reply(claimsSet.getSubject());
					return;
				} else {
					jwtMessage.fail(
							ERROR_JWT_UNAUTHORIZED_CODE,
							String.format(ERROR_JWT_UNAUTHORIZED_MSG,
									claimsSet.getSubject(), role));
				}

			} else {
				jwtMessage.fail(ERROR_JWT_INVALID, ERROR_JWT_INVALID_MSG);
			}

		} catch (ParseException | JOSEException e) {
			jwtMessage.fail(ERROR_PARSE_JWT_CODE, ERROR_PARSE_JWT_MSG);
		}

	}

	/**
	 * Validates if the information in the JWT token conforms the signature.
	 * 
	 * @param jwtMessage
	 *            is the message that contains the JWT token to be verified
	 */
	private void validateJWTToken(Message<String> jwtMessage) {
		String jwtToken = jwtMessage.body();
		boolean isVerified = false;

		SignedJWT jwt;
		try {
			jwt = SignedJWT.parse(jwtToken);

			if (jwt.verify(verifier)) {
				ReadOnlyJWTClaimsSet claims = jwt.getJWTClaimsSet();
				Date expires = claims.getExpirationTime();
				long now = System.currentTimeMillis();

				if (expires == null || now < expires.getTime() ) {
					isVerified = true;
				} else {
					jwtMessage.fail(ERROR_JWT_EXPIRED_CODE,
							ERROR_JWT_EXPIRED_MSG);
					return;
				}

			}
		} catch (ParseException | JOSEException e) {
			jwtMessage.fail(ERROR_PARSE_JWT_CODE, ERROR_PARSE_JWT_MSG);
		}

		jwtMessage.reply(isVerified);

	}

	/**
	 * Creates a new signed JWT token to be used for further verification. To
	 * get a signed JWT token at least the subject should be specified in the
	 * request message in a subject field. The issuer can optionally be
	 * specified to make it part of the returned token. The issue date is set to
	 * moment of invocation. The message will be signed with the certifcate that
	 * is read from the key store file.
	 * 
	 * @param jwtMessage
	 *            is the request message, that should at least contain a
	 *            subject, optionally the issuer
	 * @param alias
	 *            is the alias of the key to be retrieved from the key store
	 * @param password
	 *            is the password needed to get the private key from the
	 *            keystore.
	 */
	private void createJWTToken(Message<JsonObject> jwtMessage, String alias,
			String password) {
		JsonObject obj = jwtMessage.body();
		String subject = obj.getString("subject", "");
		String issuer = obj.getString("issuer");

		if (subject.isEmpty()) {
			jwtMessage.fail(ERROR_JWT_SUBJECT_CODE, ERROR_JWT_SUBJECT_MSG);
			log.warn(ERROR_JWT_SUBJECT_MSG);
			return;
		}

		try {
			RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(alias,
					password.toCharArray());
			JWSSigner signer = new RSASSASigner(privateKey);

			JWTClaimsSet claimsSet = new JWTClaimsSet();

			claimsSet.setSubject(subject);
			// TODO make a sensible choice for this
			// claimsSet.setExpirationTime(exp);

			if (obj.containsField("roles")) {
				List<String> claimList = new ArrayList<>();

				JsonArray roles = obj.getArray("roles");
				roles.forEach(s -> claimList.add((String) s));
				claimsSet.setCustomClaim("roles", roles);

			}
			claimsSet.setIssueTime(new Date());

			if (issuer != null && !issuer.isEmpty()) {
				claimsSet.setIssuer(issuer);
			}

			SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWT_ALGORITHM),
					claimsSet);

			// Compute the RSA signature
			signedJWT.sign(signer);

			jwtMessage.reply(createResponse(signedJWT, publicKey));

		} catch (UnrecoverableKeyException | KeyStoreException
				| NoSuchAlgorithmException e) {
			jwtMessage.fail(ERROR_KEYSTORE_READ_CODE,
					String.format(ERROR_KEYSTORE_READ_MSG, e.getMessage()));
		} catch (JOSEException e) {
			jwtMessage.fail(ERROR_JWT_SIGNING_CODE,
					String.format(ERROR_JWT_SIGNING_MSG, e.getMessage()));
		}

	}

	/**
	 * Convenience method to create a response JSON object based on the JWT
	 * token and the public key. Both should be send to the client, so the
	 * client is able to verify the message.
	 * 
	 * @param jwtToken
	 *            the token that should be send back to the client
	 * @param publicKey
	 *            the public key which the user can use to verify the message
	 * @return a json ojbect with the JWT token and public key
	 */
	private JsonObject createResponse(SignedJWT jwtToken, RSAPublicKey publicKey) {
		JsonObject obj = new JsonObject();
		obj.putString("jwt_token", jwtToken.serialize());

		/*
		 * byte[] data = Base64.encode(publicKey.getEncoded());
		 * 
		 * X509EncodedKeySpec spec = new
		 * X509EncodedKeySpec(Base64.decode(data)); KeyFactory keyFactory; try {
		 * keyFactory = KeyFactory.getInstance(publicKey.getAlgorithm());
		 * PublicKey key = keyFactory.generatePublic(spec);
		 * 
		 * } catch (NoSuchAlgorithmException | InvalidKeySpecException e) { //
		 * TODO Auto-generated catch block e.printStackTrace(); }
		 */

		obj.putString("key_alg", publicKey.getAlgorithm());
		obj.putBinary("public_key", Base64.encode(publicKey.getEncoded()));

		return obj;
	}

}
